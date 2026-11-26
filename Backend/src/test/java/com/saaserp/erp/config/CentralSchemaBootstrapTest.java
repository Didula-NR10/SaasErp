package com.saaserp.erp.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CentralSchemaBootstrapTest {

    private DataSource source;
    private Connection connection;
    private PreparedStatement schemaStatement;
    private ResultSet identityRows;
    private ResultSet schemaRows;
    private Flyway platform;
    private Flyway billing;
    private final Map<String, Boolean> history = new HashMap<>();
    private final Map<String, Boolean> pristine = new HashMap<>();
    private final Map<String, MigrationInfo[]> applied = new HashMap<>();

    @BeforeEach
    void setup() throws SQLException {
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement identity = mock(PreparedStatement.class);
        schemaStatement = mock(PreparedStatement.class);
        identityRows = mock(ResultSet.class);
        schemaRows = mock(ResultSet.class);
        when(identity.executeQuery()).thenReturn(identityRows);
        when(identityRows.next()).thenReturn(true);
        when(identityRows.getBoolean(1)).thenReturn(true);
        when(schemaStatement.executeQuery()).thenReturn(schemaRows);
        when(schemaRows.next()).thenReturn(true);
        when(schemaRows.getBoolean(1)).thenReturn(true);
        AtomicReference<String> selected = new AtomicReference<>();
        doAnswer(invocation -> {
            selected.set(invocation.getArgument(1, String.class));
            return null;
        }).when(schemaStatement).setString(eq(5), anyString());
        when(schemaRows.getBoolean(2)).thenAnswer(invocation -> history.get(selected.get()));
        when(schemaRows.getBoolean(3)).thenAnswer(invocation -> pristine.get(selected.get()));
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            return sql.contains("FROM pg_catalog.pg_namespace n") ? schemaStatement : identity;
        });
        for (String schema : new String[] {"platform", "billing"}) {
            history.put(schema, false);
            pristine.put(schema, true);
            applied.put(schema, new MigrationInfo[0]);
        }
        platform = stream("platform");
        billing = stream("billing");
    }

    @Test
    void explicitFreshBootstrapBaselinesBothOnlyAfterBothSchemasPassPreflight() throws Exception {
        bootstrap();
        var order = inOrder(schemaStatement, platform, billing);
        order.verify(schemaStatement).setString(5, "platform");
        order.verify(schemaStatement).executeQuery();
        order.verify(schemaStatement).setString(5, "billing");
        order.verify(schemaStatement).executeQuery();
        order.verify(platform).baseline();
        order.verify(billing).baseline();
        verify(platform).validate();
        verify(billing).validate();
        verify(connection).close();
        assertProtectedOperationsUnused();
    }

    @Test
    void invalidIdentityOrDatabasePrivilegesPreventAnyBaseline() throws Exception {
        when(identityRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void missingSchemaStopsBeforeEitherHistoryIsCreated() throws Exception {
        when(schemaRows.next()).thenReturn(false);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void wrongOwnershipAclOrHistoryPermissionsPreventBaselining() throws Exception {
        when(schemaRows.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void unexpectedBillingObjectsRejectTheWholePreflight() throws Exception {
        pristine.put("billing", false);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void partialCentralInitializationIsNotAutomaticallyCompleted() throws Exception {
        history.put("platform", true);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void existingEmptyHistoryTablesRequireOperatorReview() throws Exception {
        history.replaceAll((schema, exists) -> true);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void existingSqlV1ChecksumsAndHistoricalInstallerArePreserved() throws Exception {
        existing(CoreMigrationType.SQL, MigrationState.SUCCESS);
        bootstrap();
        verify(platform).validate();
        verify(billing).validate();
        assertNoBaseline();
        assertProtectedOperationsUnused();
    }

    @Test
    void recognizedBaselineV1IsValidatedWithoutRebaselining() throws Exception {
        existing(CoreMigrationType.BASELINE, MigrationState.BASELINE);
        bootstrap();
        verify(platform).validate();
        verify(billing).validate();
        assertNoBaseline();
    }

    @Test
    void checksumValidationFailuresPropagateWithoutRepair() throws Exception {
        existing(CoreMigrationType.SQL, MigrationState.SUCCESS);
        FlywayException failure = new FlywayException("Checksum mismatch");
        doThrow(failure).when(platform).validate();
        assertThat(assertThrows(FlywayException.class, this::bootstrap)).isSameAs(failure);
        assertNoBaseline();
        assertProtectedOperationsUnused();
    }

    @Test
    void unrecognizedBaselineDescriptionIsRejected() throws Exception {
        existing(CoreMigrationType.BASELINE, MigrationState.BASELINE);
        when(applied.get("billing")[0].getDescription()).thenReturn("Unreviewed baseline");
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void transactionalHandoffIsNotCommittedOrRolledBack() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        assertThrows(SQLException.class, this::bootstrap);
        assertNoBaseline();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).setAutoCommit(true);
    }

    @Test
    void databaseFailureIsPropagatedBeforeHistoryChanges() throws Exception {
        SQLException failure = new SQLException("Connection unavailable");
        when(source.getConnection()).thenThrow(failure);
        SQLException thrown = assertThrows(SQLException.class, this::bootstrap);
        assertThat((Throwable) thrown).isSameAs(failure);
        assertNoBaseline();
    }

    @Test
    void immutableLegacyResourcesContainOnlyReviewedSchemaCreation() throws Exception {
        CentralSchemaBootstrap.verifyLegacyMigration("platform");
        CentralSchemaBootstrap.verifyLegacyMigration("billing");
        assertThrows(IllegalArgumentException.class, () -> CentralSchemaBootstrap.verifyLegacyMigration("public"));
    }

    @Test
    void bootstrapConfigurationKeepsAllSafetyFlagsAndIndependentLocations() {
        for (String schema : new String[] {"platform", "billing"}) {
            var config = CentralSchemaBootstrap.migration(source, schema).getConfiguration();
            assertThat(config.getSchemas()).containsExactly(schema);
            assertThat(config.getDefaultSchema()).isEqualTo(schema);
            assertThat(config.getLocations()).extracting(location -> location.getDescriptor())
                    .containsExactly("classpath:db/migration/" + schema);
            assertThat(config.getBaselineVersion().getVersion()).isEqualTo("1");
            assertThat(config.isCreateSchemas()).isFalse();
            assertThat(config.isBaselineOnMigrate()).isFalse();
            assertThat(config.isValidateOnMigrate()).isTrue();
            assertThat(config.isCleanDisabled()).isTrue();
        }
    }

    @Test
    void standaloneValidationAllowsPendingButDoesNotIgnoreAppliedOrFailedMigrations() {
        for (String schema : new String[] {"platform", "billing"}) {
            var patterns = CentralSchemaBootstrap.migration(source, schema).getConfiguration()
                    .getIgnoreMigrationPatterns();
            assertThat(patterns).containsExactly(ValidatePattern.fromPattern("*:pending"));
            assertThat(patterns[0].matchesMigration(true, MigrationState.PENDING)).isTrue();
            assertThat(patterns[0].matchesMigration(true, MigrationState.SUCCESS)).isFalse();
            assertThat(patterns[0].matchesMigration(true, MigrationState.FAILED)).isFalse();
        }
    }

    @Test
    void unsafeFlywayOptionsAreRejectedBeforeDatabaseAccess() throws Exception {
        var unsafe = Flyway.configure().configuration(platform.getConfiguration()).createSchemas(true).load();
        when(platform.getConfiguration()).thenReturn(unsafe.getConfiguration());
        assertThrows(IllegalArgumentException.class, this::bootstrap);
        assertNoBaseline();
    }

    @Test
    void commandRequiresAnExplicitActionAndAllowlistedDatabase() {
        assertThrows(IllegalArgumentException.class, () -> CentralSchemaBootstrap.main(new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> CentralSchemaBootstrap.main(new String[] {"--baseline-central-v1", "postgres"}));
    }

    @Test
    void environmentCannotSelectAnAdministratorOrAnotherDatabase() {
        Map<String, String> administrator = environment();
        administrator.put("TEST_DB_MIGRATION_USER", "hich_dev");
        assertThrows(IllegalArgumentException.class,
                () -> CentralSchemaBootstrap.dataSource(CentralSchemaBootstrap.Target.TEST, administrator));
        Map<String, String> mismatched = environment();
        mismatched.put("TEST_DB_NAME", "hich_erp");
        assertThrows(IllegalArgumentException.class,
                () -> CentralSchemaBootstrap.dataSource(CentralSchemaBootstrap.Target.TEST, mismatched));
    }

    @Test
    void selectedTestEnvironmentUsesOnlyRestrictedMigrationCredentials() {
        DriverManagerDataSource dataSource = (DriverManagerDataSource) CentralSchemaBootstrap.dataSource(
                CentralSchemaBootstrap.Target.TEST, environment());
        assertThat(dataSource.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/hich_erp_test");
        assertThat(dataSource.getUsername()).isEqualTo("hich_migrator_test");
    }

    private void bootstrap() throws Exception {
        CentralSchemaBootstrap.baseline(platform, billing, CentralSchemaBootstrap.Target.TEST);
    }

    private Flyway stream(String schema) {
        Flyway flyway = mock(Flyway.class);
        when(flyway.getConfiguration()).thenReturn(CentralSchemaBootstrap.migration(source, schema).getConfiguration());
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.applied()).thenAnswer(invocation -> applied.get(schema));
        when(flyway.baseline()).thenAnswer(invocation -> {
            history.put(schema, true);
            pristine.put(schema, false);
            applied.put(schema, new MigrationInfo[] {migration(CoreMigrationType.BASELINE, MigrationState.BASELINE)});
            return null;
        });
        return flyway;
    }

    private void existing(CoreMigrationType type, MigrationState state) {
        history.replaceAll((schema, exists) -> true);
        pristine.replaceAll((schema, empty) -> false);
        applied.replaceAll((schema, rows) -> new MigrationInfo[] {migration(type, state)});
    }

    private static MigrationInfo migration(CoreMigrationType type, MigrationState state) {
        MigrationInfo migration = mock(MigrationInfo.class);
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("1"));
        when(migration.getState()).thenReturn(state);
        when(migration.getType()).thenReturn(type);
        when(migration.getChecksum()).thenReturn(type == CoreMigrationType.SQL ? 12345 : null);
        when(migration.getDescription()).thenReturn(CentralSchemaBootstrap.DESCRIPTION);
        when(migration.getInstalledBy()).thenReturn(type == CoreMigrationType.SQL ? "hich_dev" : "hich_migrator_test");
        return migration;
    }

    private void assertNoBaseline() {
        verify(platform, never()).baseline();
        verify(billing, never()).baseline();
    }

    private void assertProtectedOperationsUnused() {
        for (Flyway flyway : new Flyway[] {platform, billing}) {
            verify(flyway, never()).migrate();
            verify(flyway, never()).repair();
            verify(flyway, never()).clean();
        }
    }

    private static Map<String, String> environment() {
        return new HashMap<>(Map.of("TEST_DB_NAME", "hich_erp_test", "TEST_DB_MIGRATION_USER", "hich_migrator_test",
                "TEST_DB_HOST", "localhost", "TEST_DB_PORT", "5432", "TEST_DB_MIGRATION_PASSWORD", UUID.randomUUID().toString()));
    }
}
