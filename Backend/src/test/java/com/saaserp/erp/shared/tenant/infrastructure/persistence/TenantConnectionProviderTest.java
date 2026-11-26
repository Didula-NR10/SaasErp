package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.TransactionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
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
    private BaseConnection physical;
    private Statement discard;
    private PreparedStatement settings;
    private PreparedStatement sessionCheck;
    private ResultSet sessionRows;

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
        physical = mock(BaseConnection.class);
        discard = mock(Statement.class);
        settings = mock(PreparedStatement.class);
        sessionCheck = mock(PreparedStatement.class);
        successfulRows(settings);
        sessionRows = successfulRows(sessionCheck);
        when(pool.getUsername()).thenReturn("hich_runtime_test");
        when(connection.unwrap(BaseConnection.class)).thenReturn(physical);
        when(physical.getTransactionState()).thenReturn(TransactionState.IDLE);
        when(physical.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.createStatement()).thenReturn(discard);
        when(pool.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("pg_catalog.pg_prepared_statements")) {
                return sessionCheck;
            }
            if (sql.contains("pg_catalog.set_config('TimeZone'")) {
                return settings;
            }
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
        // Constructor configuration reads are not connection-acquisition operations.
        clearInvocations(pool);
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
        verify(connection, atLeastOnce()).prepareStatement(sql.capture());
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
        verify(setPath, never()).setString(1, SCHEMA + ", pg_temp");
        verify(connection, never()).close();
    }

    @Test
    void rejectsSchemaWithoutUsageOrWithExcessiveCreatePrivilege() throws SQLException {
        when(schemaRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verify(setPath, never()).setString(1, SCHEMA + ", pg_temp");
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
    void schemaSelectionFailureEvictsWithoutOrdinaryClose() throws SQLException {
        SQLException cause = new SQLException("Selection failed");
        when(setPath.executeQuery()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        var order = inOrder(pool, connection);
        order.verify(pool).getConnection();
        order.verify(pool).evictConnection(connection);
        verify(connection, never()).close();
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
        clearInvocations(setPath, checkPath, connection);
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
        clearInvocations(setPath);
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
        verify(connection, never()).close();
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
    void closeFailureTriggersEvictionWithoutDoubleClose() throws SQLException {
        borrowTenant();
        SQLException closeFailure = new SQLException("Close failed");
        doThrow(closeFailure).when(connection).close();
        SQLException failure = assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        assertThat(failure.getCause()).isSameAs(closeFailure);
        assertThat(failure.getSuppressed()).isEmpty();
        verify(pool).evictConnection(connection);
        verify(connection, times(1)).close();
    }

    @Test
    void evictionFailureShutsDownPoolWithoutOrdinaryClose() throws SQLException {
        when(schemaRows.next()).thenReturn(false);
        RuntimeException evictionFailure = new IllegalStateException("Eviction failed");
        doThrow(evictionFailure).when(pool).evictConnection(connection);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getSuppressed()).containsExactly(evictionFailure);
        var order = inOrder(pool, connection);
        order.verify(pool).evictConnection(connection);
        order.verify(pool).close();
        verify(connection, never()).close();
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
    void acquisitionFailureRetainsOriginalCauseAndSuppressedEvictionFailure() throws SQLException {
        SQLException original = new SQLException("Switch failed");
        RuntimeException cleanup = new IllegalStateException("Eviction failed");
        when(setPath.executeQuery()).thenThrow(original);
        doThrow(cleanup).when(pool).evictConnection(connection);
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

    @Test
    void requiresExplicitExpectedRuntimeIdentity() {
        when(pool.getUsername()).thenReturn(" ");
        assertThrows(IllegalArgumentException.class, () -> new TenantConnectionProvider(pool));
    }

    @Test
    void performsFullResetAndSynchronizesJdbcStateAtBothBoundaries() throws SQLException {
        borrowTenant();
        provider.releaseConnection(ID, connection);
        verify(discard, times(2)).execute("DISCARD ALL");
        verify(discard, times(2)).setQueryTimeout(5);
        verify(physical, times(2)).setFlushCacheOnDeallocate(true);
        verify(physical, times(2)).getNotifications();
        verify(connection, times(2)).setReadOnly(false);
        verify(connection, times(2)).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(connection, times(2)).setSchema(null);
        verify(runtime, times(4)).setString(1, "hich_runtime_test");
        verify(settings, times(2)).setString(1, "off");
        verify(sessionCheck, times(2)).setString(1, "read committed");
        verifyNoTransactionControl();
    }

    @Test
    void honorsConfiguredPoolIsolationAndReadOnlyBaseline() throws SQLException {
        when(pool.getTransactionIsolation()).thenReturn("TRANSACTION_SERIALIZABLE");
        when(pool.isReadOnly()).thenReturn(true);
        borrowTenant();
        provider.releaseConnection(ID, connection);
        verify(connection, times(2)).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        verify(connection, times(2)).setReadOnly(true);
        verify(settings, times(2)).setString(1, "on");
        verify(sessionCheck, times(2)).setString(1, "serializable");
        verify(sessionCheck, times(2)).setString(3, "on");
    }

    @Test
    void rejectsUnsupportedPoolIsolationWithoutReturningConnection() throws SQLException {
        when(pool.getTransactionIsolation()).thenReturn("unsupported");
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verify(connection, never()).close();
    }

    @Test
    void discardFailurePreservesCauseAndEvictsWithoutProxyRollback() throws SQLException {
        SQLException cause = new SQLException("Discard failed");
        when(discard.execute("DISCARD ALL")).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        verify(discard).close();
        verify(pool).evictConnection(connection);
        verify(connection, never()).close();
        verifyNoTransactionControl();
    }

    @Test
    void failedSessionVerificationEvictsBeforeTenantBinding() throws SQLException {
        when(sessionRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
        verify(setPath, never()).setString(1, SCHEMA + ", pg_temp");
    }

    @Test
    void releaseSessionVerificationFailureAlsoEvicts() throws SQLException {
        borrowTenant();
        when(sessionRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        verify(pool).evictConnection(connection);
        verify(connection, never()).close();
    }

    @Test
    void serverOpenTransactionIsRejectedEvenWhenJdbcAutocommitIsTrue() throws SQLException {
        when(physical.getTransactionState()).thenReturn(TransactionState.OPEN);
        assertThrows(SQLException.class, this::borrowTenant);
        verifyNoInteractions(runtime, discard);
        verify(pool).evictConnection(connection);
        verifyNoTransactionControl();
    }

    @Test
    void failedServerTransactionIsRejectedBeforeAnyCleanupSql() throws SQLException {
        when(physical.getTransactionState()).thenReturn(TransactionState.FAILED);
        assertThrows(SQLException.class, this::borrowTenant);
        verifyNoInteractions(runtime, discard);
        verify(pool).evictConnection(connection);
        verify(connection, never()).close();
    }

    @Test
    void unknownDriverTransactionStateFailsClosed() throws SQLException {
        when(physical.getTransactionState()).thenReturn(null);
        assertThrows(SQLException.class, this::borrowTenant);
        verify(pool).evictConnection(connection);
    }

    @Test
    void unavailableDriverStateFailsClosedAndRetainsCause() throws SQLException {
        SQLException cause = new SQLException("Unwrap failed");
        when(connection.unwrap(BaseConnection.class)).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, this::borrowTenant);
        assertThat(failure.getCause()).isSameAs(cause);
        verify(pool).evictConnection(connection);
    }

    @Test
    void manualTransactionAtReleaseIsEvictedWithoutExecutingReset() throws SQLException {
        borrowTenant();
        clearInvocations(discard);
        when(physical.getTransactionState()).thenReturn(TransactionState.OPEN);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        verify(discard, never()).execute("DISCARD ALL");
        verify(pool).evictConnection(connection);
        verify(connection, never()).close();
        verifyNoTransactionControl();
    }

    @Test
    void closedStaleProxyCannotInvalidateNewerLease() throws Exception {
        borrowTenant();
        when(connection.isClosed()).thenReturn(true);
        Connection newer = anotherConnection();
        when(pool.getConnection()).thenReturn(newer);
        assertThat(provider.getAnyConnection()).isSameAs(newer);
        assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        assertThat(activeLeaseCount()).isEqualTo(1);
        verify(pool, never()).evictConnection(connection);
        verify(pool, never()).evictConnection(newer);
        provider.releaseAnyConnection(newer);
        assertThat(activeLeaseCount()).isZero();
    }

    @Test
    void failedCloseThatMayHaveRecycledProxyStopsPoolWithoutEvictingNewBorrower() throws SQLException {
        borrowTenant();
        when(connection.isClosed()).thenReturn(false, true);
        SQLException cause = new SQLException("Close failed after recycling");
        doThrow(cause).when(connection).close();
        SQLException failure = assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(failure.getSuppressed()).hasSize(1);
        verify(pool).close();
        verify(pool, never()).evictConnection(connection);
        verify(connection, times(1)).close();
    }

    @Test
    void failedOwnershipCheckStopsPoolWithoutTouchingPossiblyReusedEntry() throws Exception {
        borrowTenant();
        SQLException cause = new SQLException("Closed-state check failed");
        when(connection.isClosed()).thenThrow(cause);
        SQLException failure = assertThrows(SQLException.class, () -> provider.releaseConnection(ID, connection));
        assertThat(failure.getCause()).isSameAs(cause);
        verify(pool).close();
        verify(pool, never()).evictConnection(connection);
        verify(connection, never()).close();
        assertThat(activeLeaseCount()).isZero();
    }

    @Test
    void repeatedSuccessAndInitializationFailureDoNotAccumulateLeases() throws Exception {
        for (int iteration = 0; iteration < 50; iteration++) {
            borrowTenant();
            assertThat(activeLeaseCount()).isEqualTo(1);
            provider.releaseConnection(ID, connection);
            assertThat(activeLeaseCount()).isZero();
        }
        when(sessionRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::borrowTenant);
        assertThat(activeLeaseCount()).isZero();
    }

    @Test
    void concurrentIndependentLeasesAreTrackedAndReleasedExactlyOnce() throws Exception {
        Connection other = anotherConnection();
        when(pool.getConnection()).thenReturn(connection, other);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch borrowed = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Callable<Void> task = () -> {
            Connection owned = provider.getAnyConnection();
            try {
                borrowed.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                provider.releaseAnyConnection(owned);
            }
            return null;
        };
        try {
            Future<Void> first = executor.submit(task);
            Future<Void> second = executor.submit(task);
            assertThat(borrowed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(activeLeaseCount()).isEqualTo(2);
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(activeLeaseCount()).isZero();
            verify(connection, times(1)).close();
            verify(other, times(1)).close();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Connection anotherConnection() throws SQLException {
        Connection other = mock(Connection.class);
        BaseConnection otherPhysical = mock(BaseConnection.class);
        when(other.getAutoCommit()).thenReturn(true);
        when(other.unwrap(BaseConnection.class)).thenReturn(otherPhysical);
        when(otherPhysical.getTransactionState()).thenReturn(TransactionState.IDLE);
        when(otherPhysical.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(other.createStatement()).thenReturn(discard);
        // Metadata-only concurrency exercises the lease map, not a simulated database:
        // every session uses the same fixed catalog path and boolean JDBC responses.
        when(other.prepareStatement(anyString())).thenAnswer(invocation ->
                connection.prepareStatement(invocation.getArgument(0, String.class)));
        return other;
    }

    private int activeLeaseCount() throws ReflectiveOperationException {
        var field = TenantConnectionProvider.class.getDeclaredField("leases");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(provider)).size();
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
