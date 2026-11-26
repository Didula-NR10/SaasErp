package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantConnectionProviderTest {

    private static final TenantId TENANT = TenantId.parse("550e8400-e29b-41d4-a716-446655440000");
    private static final String ID = TENANT.toString();
    private static final String SCHEMA = TenantSchemaIdentifier.from(TENANT).value();
    private static final String BASELINE = "pg_catalog, pg_temp";

    private HikariDataSource pool;
    private Connection connection;
    private PreparedStatement runtime;
    private PreparedStatement schema;
    private PreparedStatement setPath;
    private PreparedStatement checkPath;
    private ResultSet runtimeRows;
    private ResultSet schemaRows;
    private ResultSet setRows;
    private ResultSet pathRows;
    private AtomicReference<String> selectedPath;
    private TenantConnectionProvider provider;

    @BeforeEach
    void setup() throws SQLException {
        assertThat(TenantContextHolder.current()).isEmpty();
        pool = mock(HikariDataSource.class);
        connection = mock(Connection.class);
        runtime = mock(PreparedStatement.class);
        schema = mock(PreparedStatement.class);
        setPath = mock(PreparedStatement.class);
        checkPath = mock(PreparedStatement.class);
        runtimeRows = successfulRows(runtime);
        schemaRows = successfulRows(schema);
        setRows = successfulRows(setPath);
        pathRows = successfulRows(checkPath);
        selectedPath = new AtomicReference<>();
        when(pool.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("pg_catalog.pg_roles")) {
                return runtime;
            }
            if (sql.contains("pg_catalog.pg_namespace")) {
                return schema;
            }
            if (sql.contains("pg_catalog.set_config")) {
                return setPath;
            }
            if (sql.contains("pg_catalog.current_setting")) {
                return checkPath;
            }
            throw new SQLException("Unexpected test SQL");
        });
        doAnswer(invocation -> {
            selectedPath.set(invocation.getArgument(1, String.class));
            return null;
        }).when(setPath).setString(eq(1), anyString());
        when(setRows.getString(1)).thenAnswer(invocation -> selectedPath.get());
        provider = new TenantConnectionProvider(pool);
    }

    @AfterEach
    void contextWasNotLeaked() {
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    @Test
    void rejectsMissingRuntimePool() {
        assertThrows(NullPointerException.class, () -> new TenantConnectionProvider(null));
    }

    @Test
    void acquiresFromRuntimePoolAndBindsOnlyValidatedSchemaAsData() throws SQLException {
        assertThat(borrowTenant()).isSameAs(connection);
        verify(pool).getConnection();
        verify(pool, never()).getConnection(anyString(), anyString());
        verify(schema).setString(1, SCHEMA);
        verify(setPath).setString(1, SCHEMA + ", pg_temp");
        verify(checkPath).setString(2, SCHEMA);
        verify(checkPath).setString(3, "pg_catalog," + SCHEMA);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(4)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues()).noneMatch(statement -> statement.contains(SCHEMA) || statement.contains(ID));
        provider.releaseConnection(ID, connection);
    }

    @Test
    void requiresActiveContextBeforeBorrowing() {
        assertThrows(IllegalStateException.class, () -> provider.getConnection(ID));
        verifyNoInteractions(pool);
    }

    @Test
    void rejectsMalformedReservedAndMaliciousIdentifiersBeforeBorrowing() {
        try (var scope = TenantContextHolder.bind(new TenantContext(TENANT))) {
            for (String input : new String[] {"", "public", "platform", "billing", "information_schema",
                    "pg_catalog", "pg_temp", SCHEMA, "1-1-1-1-1", ID.toUpperCase(java.util.Locale.ROOT),
                    ID + "\n", ID + "' OR 1=1 --", "\u0000", "other.tenant"}) {
                var failure = assertThrows(IllegalArgumentException.class, () -> provider.getConnection(input));
                assertThat(failure.getMessage()).isEqualTo("Tenant identity must be a canonical UUID");
            }
            assertThrows(NullPointerException.class, () -> provider.getConnection(null));
        }
        verifyNoInteractions(pool);
    }

    @Test
    void rejectsAnotherTenantEvenWhenItsUuidIsValid() {
        try (var scope = TenantContextHolder.bind(new TenantContext(TENANT))) {
            assertThrows(IllegalStateException.class,
                    () -> provider.getConnection("550e8400-e29b-41d4-a716-446655440001"));
        }
        verifyNoInteractions(pool);
    }

    @Test
    void cannotBorrowUsingAStaleContext() throws SQLException {
        Connection borrowed = borrowTenant();
        provider.releaseConnection(ID, borrowed);
        assertThrows(IllegalStateException.class, () -> provider.getConnection(ID));
        verify(pool, times(1)).getConnection();
    }

    @Test
    void rejectsNonexistentSchemaAndEvictsConnection() throws SQLException {
        when(schemaRows.next()).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verifyNoInteractions(setPath);
        verify(connection).close();
    }

    @Test
    void rejectsSchemaWithoutUsageOrWithExcessiveCreatePrivilege() throws SQLException {
        when(schemaRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verifyNoInteractions(setPath);
    }

    @Test
    void rejectsAdministratorMigrationAndOtherwiseUnsafeRuntimeIdentities() throws SQLException {
        when(runtimeRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verifyNoInteractions(schema, setPath);
    }

    @Test
    void rejectsMissingRuntimeIdentityResult() throws SQLException {
        when(runtimeRows.next()).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
    }

    @Test
    void rejectsTransactionalAcquisitionWithoutChangingTransactionState() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verifyNoInteractions(runtime, schema, setPath);
        verifyNoTransactionControl();
    }

    @Test
    void acquisitionFailurePreservesCauseWithoutInventingAConnection() throws SQLException {
        SQLException cause = new SQLException("Unavailable", "08001");
        when(pool.getConnection()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        verify(pool, never()).evictConnection(connection);
        verifyNoInteractions(connection);
    }

    @Test
    void schemaSelectionFailureEvictsBeforeClose() throws SQLException {
        SQLException cause = new SQLException("Selection failed");
        when(setPath.executeQuery()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        var order = inOrder(pool, connection);
        order.verify(pool).getConnection();
        order.verify(pool).evictConnection(connection);
        order.verify(connection).close();
    }

    @Test
    void pathEchoMismatchIsRejected() throws SQLException {
        when(setRows.getString(1)).thenReturn("public");
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
    }

    @Test
    void effectivePathOrTemporarySchemaMismatchIsRejected() throws SQLException {
        when(pathRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
    }

    @Test
    void initializationQueryFailurePreservesCauseAndClosesJdbcResources() throws SQLException {
        SQLException cause = new SQLException("Runtime check failed");
        when(runtime.executeQuery()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        verify(runtime).close();
        verify(pool).evictConnection(connection);
    }

    @Test
    void normalReleaseRestoresAndVerifiesCatalogBaselineBeforeClosing() throws SQLException {
        borrowTenant();
        provider.releaseConnection(ID, connection);
        verify(setPath).setString(1, BASELINE);
        verify(checkPath).setString(2, "pg_catalog");
        verify(checkPath).setString(3, "pg_catalog");
        var order = inOrder(setPath, checkPath, connection);
        order.verify(setPath).setString(1, BASELINE);
        order.verify(checkPath).executeQuery();
        order.verify(connection).close();
        verify(pool, never()).evictConnection(connection);
        verifyNoTransactionControl();
    }

    @Test
    void releaseStillCleansUpAfterContextHasGoneOrChanged() throws SQLException {
        borrowTenant();
        try (var scope = TenantContextHolder.bind(new TenantContext(
                TenantId.parse("550e8400-e29b-41d4-a716-446655440001")))) {
            provider.releaseConnection(ID, connection);
        }
        assertThat(selectedPath.get()).isEqualTo(BASELINE);
    }

    @Test
    void transactionalReleaseEvictsInsteadOfResettingInsideTransaction() throws SQLException {
        borrowTenant();
        when(connection.getAutoCommit()).thenReturn(false);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        verify(pool).evictConnection(connection);
        verify(setPath, never()).setString(1, BASELINE);
        verifyNoTransactionControl();
    }

    @Test
    void resetFailureCannotReturnUnsafeConnectionForReuse() throws SQLException {
        borrowTenant();
        SQLException cause = new SQLException("Reset failed");
        when(setPath.executeQuery()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        assertThat(failure.getCause()).isSameAs(cause);
        var order = inOrder(pool, connection);
        order.verify(pool).getConnection();
        order.verify(pool).evictConnection(connection);
        order.verify(connection).close();
    }

    @Test
    void failedResetVerificationAlsoEvicts() throws SQLException {
        borrowTenant();
        when(pathRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        verify(pool).evictConnection(connection);
    }

    @Test
    void changedRuntimeIdentityAtReleaseIsNotRecycled() throws SQLException {
        borrowTenant();
        when(runtimeRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        verify(pool).evictConnection(connection);
    }

    @Test
    void closeFailureTriggersEvictionAndRetainsCleanupFailure() throws SQLException {
        borrowTenant();
        SQLException closeFailure = new SQLException("Close failed");
        doThrow(closeFailure).when(connection).close();
        SQLException failure = assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        assertThat(failure.getCause()).isSameAs(closeFailure);
        assertThat(failure.getSuppressed()).containsExactly(closeFailure);
        verify(pool).evictConnection(connection);
    }

    @Test
    void evictionFailureShutsDownPoolBeforeAnyOrdinaryClose() throws SQLException {
        when(schemaRows.next()).thenReturn(false);
        RuntimeException evictionFailure = new IllegalStateException("Eviction failed");
        doThrow(evictionFailure).when(pool).evictConnection(connection);
        when(pool.isClosed()).thenReturn(true);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getSuppressed()).containsExactly(evictionFailure);
        var order = inOrder(pool, connection);
        order.verify(pool).evictConnection(connection);
        order.verify(pool).close();
        order.verify(connection).close();
    }

    @Test
    void failedEvictionAndPoolShutdownNeverRecycleConnection() throws SQLException {
        when(schemaRows.next()).thenReturn(false);
        RuntimeException evictionFailure = new IllegalStateException("Eviction failed");
        RuntimeException shutdownFailure = new IllegalStateException("Shutdown failed");
        doThrow(evictionFailure).when(pool).evictConnection(connection);
        doThrow(shutdownFailure).when(pool).close();
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getSuppressed()).containsExactly(evictionFailure, shutdownFailure);
        verify(connection, never()).close();
    }

    @Test
    void acquisitionFailureRetainsOriginalCauseAndSuppressedCloseFailure() throws SQLException {
        SQLException original = new SQLException("Switch failed");
        SQLException cleanup = new SQLException("Close failed");
        when(setPath.executeQuery()).thenThrow(original);
        doThrow(cleanup).when(connection).close();
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(original);
        assertThat(failure.getSuppressed()).containsExactly(cleanup);
    }

    @Test
    void bootstrapNeedsNoTenantAndUsesOnlyCatalogBaseline() throws SQLException {
        assertThat(provider.getAnyConnection()).isSameAs(connection);
        verifyNoInteractions(schema);
        assertThat(selectedPath.get()).isEqualTo(BASELINE);
        verify(checkPath).setString(2, "pg_catalog");
        verify(checkPath).setString(3, "pg_catalog");
        provider.releaseAnyConnection(connection);
        verify(connection).close();
    }

    @Test
    void bootstrapDoesNotUseEvenAnActiveCustomerTenant() throws SQLException {
        try (var scope = TenantContextHolder.bind(new TenantContext(TENANT))) {
            provider.getAnyConnection();
            provider.releaseAnyConnection(connection);
        }
        verifyNoInteractions(schema);
        verify(setPath, never()).setString(1, SCHEMA + ", pg_temp");
    }

    @Test
    void mismatchedTenantReleaseEvictsOwnedConnection() throws SQLException {
        borrowTenant();
        assertThrows(SQLException.class, () -> provider.releaseConnection("public", connection));
        verify(pool).evictConnection(connection);
    }

    @Test
    void bootstrapReleaseCannotBeUsedForTenantLease() throws SQLException {
        borrowTenant();
        assertThrows(SQLException.class, () -> provider.releaseAnyConnection(connection));
        verify(pool).evictConnection(connection);
    }

    @Test
    void nullTenantReleaseCannotBeUsedForBootstrapLease() throws SQLException {
        provider.getAnyConnection();
        assertThrows(SQLException.class, () -> provider.releaseConnection(null, connection));
        verify(pool).evictConnection(connection);
    }

    @Test
    void foreignAndAlreadyReleasedConnectionsAreNotTouched() throws SQLException {
        Connection foreign = mock(Connection.class);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, foreign));
        verifyNoInteractions(foreign);
        borrowTenant();
        provider.releaseConnection(ID, connection);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        verify(connection, times(1)).close();
        verify(pool, never()).evictConnection(connection);
    }

    @Test
    void unwrapExposesOnlyProviderContractsNotPoolOrConnections() {
        assertThat(provider.isUnwrappableAs(TenantConnectionProvider.class)).isTrue();
        assertThat(provider.isUnwrappableAs(MultiTenantConnectionProvider.class)).isTrue();
        assertThat(provider.unwrap(TenantConnectionProvider.class)).isSameAs(provider);
        assertThat(provider.unwrap(MultiTenantConnectionProvider.class)).isSameAs(provider);
        assertThat(provider.isUnwrappableAs(HikariDataSource.class)).isFalse();
        assertThat(provider.isUnwrappableAs(Connection.class)).isFalse();
        assertThat(provider.isUnwrappableAs(null)).isFalse();
        assertThrows(UnknownUnwrapTypeException.class, () -> provider.unwrap(HikariDataSource.class));
        assertThrows(NullPointerException.class, () -> provider.unwrap(null));
    }

    @Test
    void declaresAccurateHibernateCapabilitiesAndReadOnlyDefaults() throws SQLException {
        assertThat(provider.supportsAggressiveRelease()).isFalse();
        assertThat(provider.handlesConnectionSchema()).isTrue();
        assertThat(provider.handlesConnectionReadOnly()).isFalse();
        try (var scope = TenantContextHolder.bind(new TenantContext(TENANT))) {
            assertThat(provider.getReadOnlyConnection(ID)).isSameAs(connection);
        }
        provider.releaseReadOnlyConnection(ID, connection);
        verify(connection, never()).setReadOnly(true);
    }

    @Test
    void uncheckedInitializationFailureStillEvicts() throws SQLException {
        RuntimeException cause = new IllegalStateException("Driver failure");
        when(runtime.executeQuery()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        verify(pool).evictConnection(connection);
    }

    @Test
    void errorDuringInitializationStillEvictsAndPropagatesOriginal() throws SQLException {
        AssertionError cause = new AssertionError("Driver error");
        when(runtime.executeQuery()).thenThrow(cause);
        assertThat(assertThrows(AssertionError.class, this::borrowTenant)).isSameAs(cause);
        verify(pool).evictConnection(connection);
    }

    private Connection borrowTenant() throws SQLException {
        try (var scope = TenantContextHolder.bind(new TenantContext(TENANT))) {
            return provider.getConnection(ID);
        }
    }

    private static ResultSet successfulRows(PreparedStatement statement) throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getBoolean(1)).thenReturn(true);
        return rows;
    }

    private void verifyNoTransactionControl() throws SQLException {
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).setAutoCommit(true);
        verify(connection, never()).setAutoCommit(false);
    }
}
