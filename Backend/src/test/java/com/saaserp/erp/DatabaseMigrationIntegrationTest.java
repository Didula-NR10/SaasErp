package com.saaserp.erp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIntegrationTest {

    @Autowired
    @Qualifier("platformFlyway")
    private Flyway platformFlyway;

    @Autowired
    @Qualifier("billingFlyway")
    private Flyway billingFlyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Value("${app.datasource.migration.url}")
    private String testMigrationUrl;

    @Value("${TEST_DB_MIGRATION_USER}")
    private String testMigrationUsername;

    @Value("${TEST_DB_MIGRATION_PASSWORD}")
    private String testMigrationPassword;

    @Value("${TEST_DB_RUNTIME_USER}")
    private String testRuntimeUsername;

    @Value("${TEST_DB_RUNTIME_PASSWORD}")
    private String testRuntimePassword;

    @Value("${TEST_DB_NAME}")
    private String testDatabaseName;

    @Value("${security.test.development-datasource.url}")
    private String developmentDatabaseUrl;

    @Value("${security.test.development-datasource.username}")
    private String developmentRuntimeUsername;

    @Value("${security.test.development-datasource.password}")
    private String developmentRuntimePassword;

    @Value("${security.test.development-database-name}")
    private String developmentDatabaseName;

    @Value("${security.test.development-migration-datasource.url}")
    private String developmentMigrationUrl;

    @Value("${security.test.development-migration-datasource.username}")
    private String developmentMigrationUsername;

    @Value("${security.test.development-migration-datasource.password}")
    private String developmentMigrationPassword;

    @Value("${security.test.postgres-datasource.url}")
    private String postgresDatabaseUrl;

    @Value("${security.test.template-datasource.url}")
    private String templateDatabaseUrl;

    private Flyway securityFixtureFlyway;

    @BeforeEach
    void migrateSecurityTestFixture() throws SQLException {
        requireTestDatabase();
        requireNonBlankCredential("TEST_DB_RUNTIME_USER", testRuntimeUsername);
        requireNonBlankCredential("TEST_DB_RUNTIME_PASSWORD", testRuntimePassword);
        requireNonBlankCredential("TEST_DB_MIGRATION_USER", testMigrationUsername);
        requireNonBlankCredential("TEST_DB_MIGRATION_PASSWORD", testMigrationPassword);
        requireNonBlankCredential("DB_RUNTIME_USER", developmentRuntimeUsername);
        requireNonBlankCredential("DB_RUNTIME_PASSWORD", developmentRuntimePassword);
        requireNonBlankCredential("DB_MIGRATION_USER", developmentMigrationUsername);
        requireNonBlankCredential("DB_MIGRATION_PASSWORD", developmentMigrationPassword);
        requireExpectedIdentities();
        requireIndependentPasswords();

        securityFixtureFlyway = Flyway.configure()
                .dataSource(testMigrationUrl, testMigrationUsername, testMigrationPassword)
                .locations("classpath:db/security-test")
                .schemas("platform")
                .defaultSchema("platform")
                .table("privilege_test_schema_history")
                .baselineVersion("0")
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .createSchemas(false)
                .load();
        var platformVersionsBeforeFixture = appliedMigrationVersions(platformFlyway);
        var billingVersionsBeforeFixture = appliedMigrationVersions(billingFlyway);
        if (!fixtureHistoryTableExists()) {
            securityFixtureFlyway.baseline();
        }
        securityFixtureFlyway.migrate();
        assertExpectedMigrationApplied(securityFixtureFlyway, "1");
        assertThat(appliedMigrationVersions(platformFlyway))
                .containsExactlyElementsOf(platformVersionsBeforeFixture);
        assertThat(appliedMigrationVersions(billingFlyway))
                .containsExactlyElementsOf(billingVersionsBeforeFixture);
    }

    @Test
    void initializesPlatformAndBillingWithIndependentHistoryTables() {
        assertThat(schemaAndHistoryExist("platform")).isTrue();
        assertThat(schemaAndHistoryExist("billing")).isTrue();
        assertExpectedMigrationApplied(platformFlyway, "1");
        assertExpectedMigrationApplied(billingFlyway, "1");
        assertThat(platformFlyway.getConfiguration().getSchemas())
                .containsExactly("platform");
        assertThat(billingFlyway.getConfiguration().getSchemas())
                .containsExactly("billing");
        assertThat(platformFlyway.getConfiguration().getDefaultSchema())
                .isEqualTo("platform");
        assertThat(billingFlyway.getConfiguration().getDefaultSchema())
                .isEqualTo("billing");
    }

    @Test
    void configuresOnlyExplicitCentralLocations() {
        assertThat(locations(platformFlyway))
                .containsExactly("classpath:db/migration/platform");
        assertThat(locations(billingFlyway))
                .containsExactly("classpath:db/migration/billing");
        assertThat(applicationContext.getBeansOfType(Flyway.class))
                .containsOnlyKeys("platformFlyway", "billingFlyway");
        assertThat(applicationContext.getBeansOfType(FlywayMigrationInitializer.class))
                .containsOnlyKeys(
                        "platformFlywayMigrationInitializer",
                        "billingFlywayMigrationInitializer");
        assertThat(platformFlyway.getConfiguration().isValidateOnMigrate()).isTrue();
        assertThat(billingFlyway.getConfiguration().isValidateOnMigrate()).isTrue();
        assertThat(platformFlyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(billingFlyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(platformFlyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(billingFlyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(platformFlyway.getConfiguration().isCreateSchemas()).isFalse();
        assertThat(billingFlyway.getConfiguration().isCreateSchemas()).isFalse();
    }

    @Test
    void migrationsFinishBeforeHibernateInitializes() {
        var entityManagerFactoryDefinition = applicationContext.getBeanFactory()
                .getBeanDefinition("entityManagerFactory");
        var billingInitializerDefinition = applicationContext.getBeanFactory()
                .getBeanDefinition("billingFlywayMigrationInitializer");

        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(schemaAndHistoryExist("platform")).isTrue();
        assertThat(schemaAndHistoryExist("billing")).isTrue();
        assertThat(billingInitializerDefinition.getDependsOn())
                .contains("platformFlywayMigrationInitializer");
        assertThat(entityManagerFactoryDefinition.getDependsOn())
                .contains("platformFlywayMigrationInitializer",
                        "billingFlywayMigrationInitializer");
    }

    @Test
    void repeatedMigrationDoesNotReapplyCompletedMigrations() {
        var appliedPlatformVersions = appliedMigrationVersions(platformFlyway);
        var appliedBillingVersions = appliedMigrationVersions(billingFlyway);

        assertThat(platformFlyway.migrate().migrationsExecuted).isZero();
        assertThat(billingFlyway.migrate().migrationsExecuted).isZero();
        assertThat(appliedMigrationVersions(platformFlyway))
                .containsExactlyElementsOf(appliedPlatformVersions);
        assertThat(appliedMigrationVersions(billingFlyway))
                .containsExactlyElementsOf(appliedBillingVersions);
        assertNoPendingMigrations(platformFlyway);
        assertNoPendingMigrations(billingFlyway);
    }

    @Test
    void repeatedFixtureMigrationDoesNotReapplyCompletedMigration() {
        var appliedFixtureVersions = appliedMigrationVersions(securityFixtureFlyway);

        assertThat(securityFixtureFlyway.migrate().migrationsExecuted).isZero();
        assertThat(appliedMigrationVersions(securityFixtureFlyway))
                .containsExactlyElementsOf(appliedFixtureVersions);
        assertNoPendingMigrations(securityFixtureFlyway);
    }

    @Test
    void centralStartupDoesNotExecuteTenantMigrationLocations() {
        assertThat(locations(platformFlyway))
                .noneMatch(location -> location.contains("/tenant/"));
        assertThat(locations(billingFlyway))
                .noneMatch(location -> location.contains("/tenant/"));
        assertThat(applicationContext.getBeansOfType(Flyway.class)).hasSize(2);
    }

    @Test
    void runtimeConnectsAsRestrictedIdentityToTestDatabase() {
        var identity = jdbcTemplate.queryForMap(
                "SELECT current_user AS username, current_database() AS database_name");

        assertThat(identity.get("username")).isEqualTo(testRuntimeUsername);
        assertThat(identity.get("database_name")).isEqualTo("hich_erp_test");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rolsuper = false AND rolcreaterole = false "
                        + "AND rolcreatedb = false AND rolreplication = false "
                        + "AND rolbypassrls = false AND rolcanlogin "
                        + "FROM pg_roles WHERE rolname = current_user",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) = 6 AND bool_and("
                        + "rolsuper = false AND rolcreaterole = false "
                        + "AND rolcreatedb = false AND rolreplication = false "
                        + "AND rolbypassrls = false) "
                        + "FROM pg_roles WHERE rolname IN "
                        + "('hich_migrator_dev', 'hich_migrator_test', 'hich_runtime', "
                        + "'hich_runtime_dev', 'hich_runtime_test', 'hich_provisioner')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT bool_and(rolinherit = "
                        + "(rolname IN ('hich_runtime_dev', 'hich_runtime_test'))) "
                        + "FROM pg_roles WHERE rolname IN "
                        + "('hich_migrator_dev', 'hich_migrator_test', 'hich_runtime', "
                        + "'hich_runtime_dev', 'hich_runtime_test', 'hich_provisioner')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT NOT rolcanlogin FROM pg_roles WHERE rolname = 'hich_provisioner'",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_has_role(current_user, 'hich_runtime', 'member')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_has_role(current_user, 'hich_migrator_dev', 'member') "
                        + "OR pg_has_role(current_user, 'hich_migrator_test', 'member')",
                Boolean.class)).isFalse();
        assertThat(Arrays.stream(new String[] {
                    testRuntimePassword,
                    testMigrationPassword,
                    developmentRuntimePassword,
                    developmentMigrationPassword
                }).distinct().count()).isEqualTo(4L);
    }

    @Test
    void developmentRuntimeConnectsOnlyAsRestrictedIdentity() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                developmentDatabaseUrl,
                developmentRuntimeUsername,
                developmentRuntimePassword);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT current_user AS username, "
                                + "current_database() AS database_name, "
                                + "rolsuper = false AND rolcreaterole = false "
                                + "AND rolcreatedb = false AND rolreplication = false "
                                + "AND rolbypassrls = false AS is_restricted "
                                + "FROM pg_roles WHERE rolname = current_user")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("username")).isEqualTo("hich_runtime_dev");
            assertThat(result.getString("database_name")).isEqualTo("hich_erp");
            assertThat(result.getBoolean("is_restricted")).isTrue();
        }
    }

    @Test
    void runtimeCanPerformOnlyExplicitlyGrantedFixtureOperations() throws SQLException {
        var fixtureId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO platform.security_privilege_fixture (fixture_id, payload) "
                            + "VALUES (?, ?)")) {
                insert.setObject(1, fixtureId);
                insert.setString(2, "before");
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE platform.security_privilege_fixture SET payload = ? "
                            + "WHERE fixture_id = ?")) {
                update.setString(1, "after");
                update.setObject(2, fixtureId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT payload FROM platform.security_privilege_fixture "
                            + "WHERE fixture_id = ?")) {
                select.setObject(1, fixtureId);
                try (var result = select.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("after");
                }
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM platform.security_privilege_fixture WHERE fixture_id = ?")) {
                delete.setObject(1, fixtureId);
                assertThat(delete.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
        }
    }

    @Test
    void migrationRoleExecutesTestMigrationAndOwnsItsIndependentHistory()
            throws SQLException {
        assertThat(securityFixtureFlyway.getConfiguration().getUser())
                .isEqualTo(testMigrationUsername);
        assertThat(securityFixtureFlyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(testMigrationUsername).isEqualTo("hich_migrator_test");
        try (Connection connection = DriverManager.getConnection(
                testMigrationUrl, testMigrationUsername, testMigrationPassword);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT current_user AS username, "
                                + "current_database() AS database_name, "
                                + "(SELECT installed_by "
                                + "FROM platform.privilege_test_schema_history "
                                + "WHERE version = '1' AND success) AS installed_by, "
                                + "(SELECT installed_by "
                                + "FROM platform.privilege_test_schema_history "
                                + "WHERE version = '0' AND type = 'BASELINE' AND success) "
                                + "AS baseline_installed_by, "
                                + "EXISTS (SELECT 1 "
                                + "FROM platform.privilege_test_schema_history "
                                + "WHERE version = '0' AND type = 'BASELINE' AND success) "
                                + "AS baseline_zero_applied, "
                                + "(SELECT pg_get_userbyid(relowner) FROM pg_class "
                                + "WHERE oid = 'platform.security_privilege_fixture'::regclass) "
                                + "AS fixture_owner, "
                                + "(SELECT pg_get_userbyid(relowner) FROM pg_class "
                                + "WHERE oid = 'platform.privilege_test_schema_history'::regclass) "
                                + "AS fixture_history_owner, "
                                + "NOT has_database_privilege(current_user, "
                                + "current_database(), 'CREATE') AS cannot_create_schemas, "
                                + "has_schema_privilege(current_user, 'platform', 'CREATE') "
                                + "AS can_migrate")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("username")).isEqualTo(testMigrationUsername);
            assertThat(result.getString("database_name")).isEqualTo(testDatabaseName);
            assertThat(result.getString("installed_by")).isEqualTo(testMigrationUsername);
            assertThat(result.getString("baseline_installed_by"))
                    .isEqualTo(testMigrationUsername);
            assertThat(result.getBoolean("baseline_zero_applied")).isTrue();
            assertThat(result.getString("fixture_owner")).isEqualTo(testMigrationUsername);
            assertThat(result.getString("fixture_history_owner")).isEqualTo(testMigrationUsername);
            assertThat(result.getBoolean("cannot_create_schemas")).isTrue();
            assertThat(result.getBoolean("can_migrate")).isTrue();
        }
        assertNoPendingMigrations(securityFixtureFlyway);
    }

    @Test
    void developmentMigrationIdentityOwnsDevelopmentMigrationSchemas()
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                developmentMigrationUrl,
                developmentMigrationUsername,
                developmentMigrationPassword);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT current_user AS username, "
                                + "current_database() AS database_name, "
                                + "has_schema_privilege(current_user, 'platform', 'CREATE') "
                                + "AND has_schema_privilege(current_user, 'billing', 'CREATE') "
                                + "AS can_migrate, "
                                + "NOT has_database_privilege(current_user, "
                                + "current_database(), 'CREATE') AS cannot_create_schemas, "
                                + "NOT EXISTS (SELECT 1 FROM pg_default_acl defaults "
                                + "CROSS JOIN LATERAL aclexplode(defaults.defaclacl) acl "
                                + "WHERE defaults.defaclrole = current_user::regrole "
                                + "AND defaults.defaclobjtype IN ('r', 'S', 'f', 'T') "
                                + "AND (acl.grantee = 0 OR (acl.grantee IN "
                                + "(SELECT oid FROM pg_roles WHERE rolname IN "
                                + "('hich_migrator_dev', 'hich_migrator_test', "
                                + "'hich_runtime', 'hich_runtime_dev', "
                                + "'hich_runtime_test', 'hich_provisioner')) "
                                + "AND acl.grantee <> defaults.defaclrole))) "
                                + "AS defaults_are_restricted, "
                                + "EXISTS (SELECT 1 FROM pg_default_acl defaults "
                                + "WHERE defaults.defaclrole = current_user::regrole "
                                + "AND defaults.defaclnamespace = 0 "
                                + "AND defaults.defaclobjtype = 'f') "
                                + "AS global_function_defaults_configured, "
                                + "EXISTS (SELECT 1 FROM pg_default_acl defaults "
                                + "WHERE defaults.defaclrole = current_user::regrole "
                                + "AND defaults.defaclnamespace = 0 "
                                + "AND defaults.defaclobjtype = 'T') "
                                + "AS global_type_defaults_configured")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("username")).isEqualTo("hich_migrator_dev");
            assertThat(result.getString("database_name")).isEqualTo("hich_erp");
            assertThat(result.getBoolean("can_migrate")).isTrue();
            assertThat(result.getBoolean("cannot_create_schemas")).isTrue();
            assertThat(result.getBoolean("defaults_are_restricted")).isTrue();
            assertThat(result.getBoolean("global_function_defaults_configured")).isTrue();
            assertThat(result.getBoolean("global_type_defaults_configured")).isTrue();
        }
    }

    @Test
    void defaultPrivilegesDoNotExposeUnapprovedObjectsToRuntime() {
        var defaults = jdbcTemplate.queryForMap(
                "SELECT "
                        + "has_table_privilege(current_user, "
                        + "'platform.security_ungranted_fixture', 'SELECT') "
                        + "AS can_read_ungranted_table, "
                        + "has_function_privilege(current_user, "
                        + "'platform.security_privilege_function()', 'EXECUTE') "
                        + "AS can_execute_new_function, "
                        + "has_type_privilege(current_user, "
                        + "'platform.security_privilege_type', 'USAGE') "
                        + "AS can_use_new_type, "
                        + "has_table_privilege(current_user, "
                        + "'platform.flyway_schema_history', 'UPDATE') "
                        + "AS can_update_flyway_history, "
                        + "has_table_privilege(current_user, "
                        + "'platform.flyway_schema_history', 'SELECT') "
                        + "AS can_read_flyway_history, "
                        + "has_table_privilege(current_user, "
                        + "'platform.privilege_test_schema_history', 'UPDATE') "
                        + "AS can_update_fixture_history, "
                        + "NOT EXISTS (SELECT 1 FROM pg_default_acl defaults "
                        + "CROSS JOIN LATERAL aclexplode(defaults.defaclacl) acl "
                        + "WHERE defaults.defaclrole = 'hich_migrator_test'::regrole "
                        + "AND defaults.defaclobjtype IN ('r', 'S', 'f', 'T') "
                        + "AND (acl.grantee = 0 OR (acl.grantee IN "
                        + "(SELECT oid FROM pg_roles WHERE rolname IN "
                        + "('hich_migrator_dev', 'hich_migrator_test', "
                        + "'hich_runtime', 'hich_runtime_dev', "
                        + "'hich_runtime_test', 'hich_provisioner')) "
                        + "AND acl.grantee <> defaults.defaclrole))) "
                        + "AS defaults_are_restricted, "
                        + "EXISTS (SELECT 1 FROM pg_default_acl defaults "
                        + "WHERE defaults.defaclrole = 'hich_migrator_test'::regrole "
                        + "AND defaults.defaclnamespace = 0 "
                        + "AND defaults.defaclobjtype = 'f') "
                        + "AS global_function_defaults_configured, "
                        + "EXISTS (SELECT 1 FROM pg_default_acl defaults "
                        + "WHERE defaults.defaclrole = 'hich_migrator_test'::regrole "
                        + "AND defaults.defaclnamespace = 0 "
                        + "AND defaults.defaclobjtype = 'T') "
                        + "AS global_type_defaults_configured");

        assertThat(defaults.get("can_read_ungranted_table")).isEqualTo(false);
        assertThat(defaults.get("can_execute_new_function")).isEqualTo(false);
        assertThat(defaults.get("can_use_new_type")).isEqualTo(false);
        assertThat(defaults.get("can_update_flyway_history")).isEqualTo(false);
        assertThat(defaults.get("can_read_flyway_history")).isEqualTo(false);
        assertThat(defaults.get("can_update_fixture_history")).isEqualTo(false);
        assertThat(defaults.get("defaults_are_restricted")).isEqualTo(true);
        assertThat(defaults.get("global_function_defaults_configured")).isEqualTo(true);
        assertThat(defaults.get("global_type_defaults_configured")).isEqualTo(true);
    }

    @Test
    void runtimeCannotCreateSchemasOrTables() {
        assertPermissionDenied("CREATE SCHEMA runtime_schema_denied");
        assertPermissionDenied(
                "CREATE TABLE platform.runtime_table_denied (id integer PRIMARY KEY)");
        assertPermissionDenied("CREATE TEMP TABLE runtime_temp_table_denied (id integer)");
    }

    @Test
    void runtimeCannotAlterOrDropMigrationOwnedTables() {
        assertPermissionDenied(
                "ALTER TABLE platform.security_privilege_fixture ADD COLUMN denied integer");
        assertPermissionDenied("DROP TABLE platform.security_privilege_fixture");
    }

    @Test
    void runtimeCannotCreateRolesOrModifyFlywayHistory() {
        assertPermissionDenied("CREATE ROLE runtime_role_denied LOGIN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.company_memberships', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.lock_mfa_transaction_state(uuid,text,text)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertPermissionDenied(
                "UPDATE platform.flyway_schema_history SET success = success WHERE version = '1'");
        assertPermissionDenied(
                "UPDATE platform.privilege_test_schema_history "
                        + "SET success = success WHERE version = '1'");
    }

    @Test
    void runtimeCannotMutateAuthorizationWorkflowsOrAuditTablesDirectly() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.platform_administrator_sessions', 'SELECT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.platform_administrator_sessions', 'INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.platform_administrator_sessions', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.platform_account_roles', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.authorize_platform_session(uuid,uuid,text,text,text)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.assign_company_role(uuid,uuid,uuid,text,uuid,text,text,uuid,text,uuid)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.authorization_role_permissions', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.step_up_authorizations', 'INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.step_up_authorizations', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.dual_approvals', 'INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.dual_approvals', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.security_audit_events', 'INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, 'platform.security_audit_events', 'DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.authorize_company_session(uuid,uuid,uuid,text,text)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.create_step_up_authorization(uuid,uuid,uuid,uuid,text,text,text,text,text,text,uuid)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.create_dual_approval(uuid,uuid,text,uuid,uuid,text,text,text,text,text,uuid,uuid)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regprocedure('platform.cancel_dual_approval(uuid,uuid,uuid,uuid,text)') IS NOT NULL",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_function_privilege(current_user, "
                        + "'platform.cancel_dual_approval(uuid,uuid,uuid,uuid,text,uuid)', 'EXECUTE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regprocedure('platform.consume_dual_approval(uuid,uuid,uuid,uuid,text,text,text,text,text)') IS NOT NULL",
                Boolean.class)).isFalse();
    }

    @Test
    void obsoleteAuthorizationOverloadsAreAbsentAndV9KeepsLeastPrivilege() {
        assertExpectedMigrationApplied(platformFlyway, "9");
        for (String signature : List.of(
                "consume_dual_approval(uuid,uuid,uuid,uuid,text,text,text,text,text)",
                "cancel_dual_approval(uuid,uuid,uuid,uuid,text)",
                "expire_dual_approval(uuid)")) {
            assertThat(jdbcTemplate.queryForObject("SELECT to_regprocedure(?) IS NULL",
                    Boolean.class, "platform." + signature)).isTrue();
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT has_function_privilege(current_user,
                    'platform.company_role_operation_digest(uuid,uuid,text,text)', 'EXECUTE')
                """, Boolean.class)).isFalse();
        assertPermissionDenied("SELECT platform.company_role_operation_digest(gen_random_uuid(),gen_random_uuid(),'EMPLOYEE','OWNER')");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT has_function_privilege(current_user,
                    'platform.consume_dual_approval(uuid,uuid,uuid,uuid,text,text,text,text,text,uuid)', 'EXECUTE')
                """, Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) acl
                WHERE n.nspname='platform' AND p.proname IN (
                    'company_role_operation_digest','assign_company_role','consume_dual_approval',
                    'approve_dual_approval','cancel_dual_approval','expire_dual_approval',
                    'create_dual_approval','create_step_up_authorization')
                AND acl.grantee=0 AND acl.privilege_type='EXECUTE'
                """, Long.class)).isZero();
    }

    @Test
    void runtimeCannotAssumeAdministratorOrMigrationRoles() {
        assertPermissionDenied("SET ROLE hich_dev");
        assertPermissionDenied("SET ROLE hich_migrator_dev");
        assertPermissionDenied("SET ROLE hich_migrator_test");
        assertPermissionDenied("SET ROLE hich_runtime");
    }

    @Test
    void testAndDevelopmentRuntimeIdentitiesCannotCrossConnect() {
        assertDatabaseConnectionDenied(
                developmentDatabaseUrl, testRuntimeUsername, testRuntimePassword);
        assertDatabaseConnectionDenied(
                testMigrationUrl, developmentRuntimeUsername, developmentRuntimePassword);
        assertDatabaseConnectionDenied(
                testMigrationUrl, developmentMigrationUsername, developmentMigrationPassword);
        assertDatabaseConnectionDenied(
                developmentMigrationUrl, testMigrationUsername, testMigrationPassword);
    }

    @Test
    void restrictedIdentitiesCannotConnectToPostgresMaintenanceDatabases() {
        assertDatabaseConnectionDenied(
                postgresDatabaseUrl, testRuntimeUsername, testRuntimePassword);
        assertDatabaseConnectionDenied(
                postgresDatabaseUrl, developmentRuntimeUsername, developmentRuntimePassword);
        assertDatabaseConnectionDenied(
                postgresDatabaseUrl, testMigrationUsername, testMigrationPassword);
        assertDatabaseConnectionDenied(
                postgresDatabaseUrl, developmentMigrationUsername, developmentMigrationPassword);
        assertDatabaseConnectionDenied(
                templateDatabaseUrl, testRuntimeUsername, testRuntimePassword);
        assertDatabaseConnectionDenied(
                templateDatabaseUrl, developmentRuntimeUsername, developmentRuntimePassword);
        assertDatabaseConnectionDenied(
                templateDatabaseUrl, testMigrationUsername, testMigrationPassword);
        assertDatabaseConnectionDenied(
                templateDatabaseUrl, developmentMigrationUsername, developmentMigrationPassword);
    }

    @Test
    void centralV1HistoryIsEitherPreservedSqlOrExplicitVerifiedBaseline() {
        for (Flyway flyway : new Flyway[] {platformFlyway, billingFlyway}) {
            flyway.validate();
            var v1 = Arrays.stream(flyway.info().applied())
                    .filter(migration -> migration.getVersion() != null
                            && "1".equals(migration.getVersion().getVersion()))
                    .toList();
            assertThat(v1).hasSize(1);
            MigrationInfo migration = v1.getFirst();
            if (migration.getState() == MigrationState.BASELINE) {
                assertThat(migration.getType().name()).isEqualTo("BASELINE");
                assertThat(migration.getDescription()).isEqualTo("Administrator-provisioned central schema V1");
                assertThat(migration.getInstalledBy()).isEqualTo(testMigrationUsername);
                assertThat(migration.getChecksum()).isNull();
            } else {
                assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS);
                assertThat(migration.getType().name()).isEqualTo("SQL");
                assertThat(migration.getChecksum()).isNotNull();
                // Legacy SQL histories may have the original administrator as installer.
                assertThat(migration.getInstalledBy()).isNotBlank();
            }
        }
    }

    @Test
    void bothEnvironmentsDenyDatabaseCreateAndTempToMigratorsAndRuntime() throws SQLException {
        assertDatabaseCreationDenied(testMigrationUrl, testMigrationUsername, testMigrationPassword,
                testRuntimeUsername, "hich_erp_test");
        assertDatabaseCreationDenied(developmentMigrationUrl, developmentMigrationUsername,
                developmentMigrationPassword, developmentRuntimeUsername, "hich_erp");
    }

    @Test
    void migrationIdentitiesCannotCreateDatabasesOrInheritRuntimePrivileges() throws SQLException {
        assertRestrictedMigrationRole(testMigrationUrl, testMigrationUsername, testMigrationPassword);
        assertRestrictedMigrationRole(developmentMigrationUrl, developmentMigrationUsername,
                developmentMigrationPassword);
    }

    private static void assertRestrictedMigrationRole(String url, String user, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT current_user = ?::name AND current_user = session_user
                            AND r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolcreatedb
                            AND NOT r.rolcreaterole AND NOT r.rolreplication AND NOT r.rolbypassrls
                            AND NOT r.rolinherit
                            AND NOT pg_catalog.pg_has_role(current_user, 'hich_runtime', 'MEMBER')
                        FROM pg_catalog.pg_roles r WHERE r.rolname = current_user
                        """)) {
            statement.setQueryTimeout(5);
            statement.setString(1, user);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
            }
        }
    }

    private static void assertDatabaseCreationDenied(String url, String migrationUser, String migrationPassword,
            String runtimeUser, String database) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, migrationUser, migrationPassword);
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT current_user = ?::name AND current_user = session_user
                            AND pg_catalog.current_database() = ?::name
                            AND pg_catalog.pg_get_userbyid(d.datdba) = 'hich_dev'
                            AND NOT pg_catalog.has_database_privilege(current_user, d.datname::text, 'CREATE,TEMP')
                            AND NOT pg_catalog.has_database_privilege(?::name, d.datname::text, 'CREATE,TEMP')
                        FROM pg_catalog.pg_database d WHERE d.datname = pg_catalog.current_database()
                        """)) {
            statement.setString(1, migrationUser);
            statement.setString(2, database);
            statement.setString(3, runtimeUser);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
            }
        }
    }

    private boolean schemaAndHistoryExist(String schema) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                schema + ".flyway_schema_history"));
    }

    private boolean fixtureHistoryTableExists() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                testMigrationUrl, testMigrationUsername, testMigrationPassword);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT current_user AS username, "
                                + "current_database() AS database_name, "
                                + "to_regclass('platform.privilege_test_schema_history') "
                                + "IS NOT NULL AS history_exists")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("username")).isEqualTo("hich_migrator_test");
            assertThat(result.getString("database_name")).isEqualTo("hich_erp_test");
            return result.getBoolean("history_exists");
        }
    }

    private static void assertExpectedMigrationApplied(
            Flyway flyway, String expectedVersion) {
        var applied = Arrays.stream(flyway.info().applied())
                .filter(MigrationInfo::isVersioned)
                .toList();

        assertThat(applied)
                .extracting(migration -> migration.getVersion().getVersion())
                .contains(expectedVersion);
        assertThat(applied)
                .allSatisfy(migration ->
                        assertThat(migration.getState().isApplied()).isTrue());
        assertNoPendingMigrations(flyway);
    }

    private static List<String> appliedMigrationVersions(Flyway flyway) {
        return Arrays.stream(flyway.info().applied())
                .filter(MigrationInfo::isVersioned)
                .map(migration -> migration.getVersion().getVersion())
                .toList();
    }

    private static void assertNoPendingMigrations(Flyway flyway) {
        assertThat(flyway.info().pending()).isEmpty();
    }

    private void assertPermissionDenied(String sql) {
        var exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                } catch (SQLException failure) {
                    connection.rollback();
                    throw failure;
                }
                connection.rollback();
            }
        });
        assertThat(exception.getSQLState()).isEqualTo("42501");
    }

    private static void assertDatabaseConnectionDenied(
            String url, String username, String password) {
        var exception = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(url, username, password));
        assertThat(exception.getSQLState()).isEqualTo("42501");
    }

    private void requireTestDatabase() {
        if (!"hich_erp_test".equals(testDatabaseName)) {
            throw new IllegalStateException(
                    "Database security fixtures are restricted to hich_erp_test");
        }
        if (!"hich_erp".equals(developmentDatabaseName)) {
            throw new IllegalStateException(
                    "Development credential checks are restricted to hich_erp");
        }
    }

    private void requireExpectedIdentities() {
        if (!"hich_runtime_test".equals(testRuntimeUsername)
                || !"hich_migrator_test".equals(testMigrationUsername)
                || !"hich_runtime_dev".equals(developmentRuntimeUsername)
                || !"hich_migrator_dev".equals(developmentMigrationUsername)) {
            throw new IllegalStateException(
                    "Database security tests require the dedicated runtime and migration identities");
        }
    }

    private void requireIndependentPasswords() {
        if (Arrays.stream(new String[] {
                    testRuntimePassword,
                    testMigrationPassword,
                    developmentRuntimePassword,
                    developmentMigrationPassword
                }).distinct().count() != 4L) {
            throw new IllegalStateException(
                    "Development and testing database credentials must be independent");
        }
    }

    private static void requireNonBlankCredential(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured and non-blank");
        }
    }

    private static List<String> locations(Flyway flyway) {
        return Arrays.stream(flyway.getConfiguration().getLocations())
                .map(Location::getDescriptor)
                .toList();
    }
}
