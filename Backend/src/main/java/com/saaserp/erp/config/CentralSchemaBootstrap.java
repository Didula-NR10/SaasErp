package com.saaserp.erp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit offline bootstrap command; never a Spring bean or an application startup fallback. */
public final class CentralSchemaBootstrap {

    static final String DESCRIPTION = "Administrator-provisioned central schema V1";
    private static final List<String> SCHEMAS = List.of("platform", "billing");
    private static final String IDENTITY = """
            SELECT current_user = ?::name AND current_user = session_user
                AND pg_catalog.current_database() = ?::name
                AND pg_catalog.pg_get_userbyid(d.datdba) = 'hich_dev'
                AND m.rolcanlogin AND NOT m.rolinherit AND NOT m.rolsuper
                AND NOT m.rolcreaterole AND NOT m.rolcreatedb AND NOT m.rolreplication AND NOT m.rolbypassrls
                AND r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolcreaterole
                AND NOT r.rolcreatedb AND NOT r.rolreplication AND NOT r.rolbypassrls
                AND NOT pg_catalog.has_database_privilege(current_user, d.datname::text, 'CREATE,TEMP')
                AND NOT pg_catalog.has_database_privilege(r.rolname, d.datname::text, 'CREATE,TEMP')
                AND NOT pg_catalog.pg_has_role(current_user, 'hich_runtime', 'MEMBER')
                AND pg_catalog.pg_has_role(r.rolname, 'hich_runtime', 'MEMBER')
            FROM pg_catalog.pg_database d
            JOIN pg_catalog.pg_roles m ON m.rolname = current_user
            JOIN pg_catalog.pg_roles r ON r.rolname = ?::name
            WHERE d.datname = pg_catalog.current_database()
            """;
    private static final String SCHEMA_STATE = """
            SELECT pg_catalog.pg_get_userbyid(n.nspowner) = ?::name
                AND pg_catalog.has_schema_privilege(current_user, n.oid, 'USAGE')
                AND pg_catalog.has_schema_privilege(current_user, n.oid, 'CREATE')
                AND pg_catalog.has_schema_privilege(?::name, n.oid, 'USAGE')
                AND NOT pg_catalog.has_schema_privilege(?::name, n.oid, 'CREATE')
                AND NOT EXISTS (
                    SELECT 1 FROM pg_catalog.aclexplode(
                        COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
                    WHERE a.grantee = 0)
                AND NOT EXISTS (
                    SELECT 1 FROM pg_catalog.pg_default_acl defaults
                    CROSS JOIN LATERAL pg_catalog.aclexplode(defaults.defaclacl) a
                    WHERE defaults.defaclrole = n.nspowner
                        AND defaults.defaclnamespace IN (0, n.oid)
                        AND defaults.defaclobjtype = 'r' AND a.grantee <> n.nspowner)
                AND NOT EXISTS (
                    SELECT 1 FROM pg_catalog.pg_class c
                    WHERE c.relnamespace = n.oid AND c.relname = 'flyway_schema_history'
                        AND (c.relkind <> 'r' OR c.relowner <> n.nspowner
                            OR pg_catalog.has_table_privilege(?::name, c.oid, 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER'))),
                EXISTS (SELECT 1 FROM pg_catalog.pg_class c
                    WHERE c.relnamespace = n.oid AND c.relname = 'flyway_schema_history'),
                NOT EXISTS (SELECT 1 FROM pg_catalog.pg_depend dependency
                    WHERE dependency.refclassid = 'pg_catalog.pg_namespace'::regclass
                        AND dependency.refobjid = n.oid
                        AND dependency.classid <> 'pg_catalog.pg_default_acl'::regclass)
            FROM pg_catalog.pg_namespace n WHERE n.nspname = ?::name
            """;

    private CentralSchemaBootstrap() {
    }

    /** Requires exactly: --baseline-central-v1 hich_erp[_test]. Reads only the selected migrator credentials. */
    public static void main(String[] args) throws SQLException, IOException {
        if (args.length != 2 || !"--baseline-central-v1".equals(args[0])) {
            throw new IllegalArgumentException("Explicit --baseline-central-v1 and a supported database are required");
        }
        Target target = Target.forDatabase(args[1]);
        DataSource source = dataSource(target, System.getenv());
        baseline(migration(source, "platform"), migration(source, "billing"), target);
    }

    static DataSource dataSource(Target target, Map<String, String> environment) {
        String prefix = target.prefix;
        if (!target.database.equals(required(environment, prefix + "NAME"))
                || !target.migrator.equals(required(environment, prefix + "MIGRATION_USER"))) {
            throw new IllegalArgumentException("Bootstrap environment must match the selected database and migrator");
        }
        String host = required(environment, prefix + "HOST");
        String port = required(environment, prefix + "PORT");
        if (!host.matches("[A-Za-z0-9.-]+") || !port.matches("[0-9]{1,5}")
                || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            throw new IllegalArgumentException("A valid bootstrap database host and port are required");
        }
        return new DriverManagerDataSource("jdbc:postgresql://" + host + ":" + port + "/" + target.database,
                target.migrator, required(environment, prefix + "MIGRATION_PASSWORD"));
    }

    static Flyway migration(DataSource source, String schema) {
        requireSchema(schema);
        return Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/" + schema)
                .schemas(schema).defaultSchema(schema)
                .baselineVersion("1").baselineDescription(DESCRIPTION)
                .baselineOnMigrate(false).createSchemas(false)
                .validateOnMigrate(true).cleanDisabled(true)
                .failOnMissingLocations(true)
                // Standalone validation precedes normal migrate; only unapplied work may remain pending.
                .ignoreMigrationPatterns("*:pending").load();
    }

    static void baseline(Flyway platform, Flyway billing, Target target) throws SQLException, IOException {
        verifyLegacyMigration("platform");
        verifyLegacyMigration("billing");
        verifyConfiguration(platform, "platform");
        verifyConfiguration(billing, "billing");
        DataSource source = platform.getConfiguration().getDataSource();
        if (source != billing.getConfiguration().getDataSource()) {
            throw new IllegalArgumentException("Both central histories must use the same bootstrap datasource");
        }
        try (Connection connection = source.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new SQLException("Bootstrap requires an independent autocommit connection");
            }
            verifyIdentity(connection, target);
            SchemaState platformState = state(connection, "platform", target);
            SchemaState billingState = state(connection, "billing", target);
            if (platformState.history != billingState.history) {
                throw new SQLException("Partial central migration initialization requires operator review");
            }
            if (platformState.history) {
                // Existing histories are never baselined, repaired, deleted, or rewritten.
                verifyHistory(platform, target);
                verifyHistory(billing, target);
                platform.validate();
                billing.validate();
                return;
            }
            if (!platformState.pristine || !billingState.pristine) {
                throw new SQLException("Untracked central schema objects require operator review");
            }
            // Both streams pass preflight before either history can be created.
            platform.baseline();
            billing.baseline();
            verifyIdentity(connection, target);
            if (!state(connection, "platform", target).history || !state(connection, "billing", target).history) {
                throw new SQLException("Central baseline histories were not established");
            }
            verifyHistory(platform, target);
            verifyHistory(billing, target);
            platform.validate();
            billing.validate();
        }
    }

    static void verifyLegacyMigration(String schema) throws IOException {
        requireSchema(schema);
        String path = "/db/migration/" + schema + "/V1__create_" + schema + "_schema.sql";
        try (InputStream input = CentralSchemaBootstrap.class.getResourceAsStream(path)) {
            if (input == null || !new String(input.readAllBytes(), StandardCharsets.UTF_8).strip()
                    .equals("CREATE SCHEMA IF NOT EXISTS " + schema + ";")) {
                throw new IllegalStateException("Legacy V1 must contain only the reviewed central schema creation");
            }
        }
    }

    private static void verifyConfiguration(Flyway flyway, String schema) {
        var configuration = flyway.getConfiguration();
        if (configuration.isBaselineOnMigrate() || configuration.isCreateSchemas()
                || !configuration.isValidateOnMigrate() || !configuration.isCleanDisabled()
                || !"1".equals(configuration.getBaselineVersion().getVersion())
                || !DESCRIPTION.equals(configuration.getBaselineDescription())
                || !schema.equals(configuration.getDefaultSchema())
                || !Arrays.equals(configuration.getSchemas(), new String[] {schema})
                || !"flyway_schema_history".equals(configuration.getTable())
                || !Arrays.equals(configuration.getIgnoreMigrationPatterns(),
                        new ValidatePattern[] {ValidatePattern.fromPattern("*:pending")})
                || !Arrays.stream(configuration.getLocations()).map(Location::getDescriptor).toList()
                        .equals(List.of("classpath:db/migration/" + schema))) {
            throw new IllegalArgumentException("Unexpected central bootstrap Flyway configuration");
        }
    }

    private static void verifyIdentity(Connection connection, Target target) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(IDENTITY)) {
            statement.setQueryTimeout(10);
            statement.setString(1, target.migrator);
            statement.setString(2, target.database);
            statement.setString(3, target.runtime);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    throw new SQLException("Bootstrap database, ownership or restricted identities are invalid");
                }
            }
        }
    }

    private static SchemaState state(Connection connection, String schema, Target target) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SCHEMA_STATE)) {
            statement.setQueryTimeout(10);
            statement.setString(1, target.migrator);
            statement.setString(2, target.runtime);
            statement.setString(3, target.runtime);
            statement.setString(4, target.runtime);
            statement.setString(5, schema);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    throw new SQLException("Central schema ownership or access prerequisites are invalid");
                }
                return new SchemaState(rows.getBoolean(2), rows.getBoolean(3));
            }
        }
    }

    private static void verifyHistory(Flyway flyway, Target target) throws SQLException {
        boolean recognized = Arrays.stream(flyway.info().applied()).anyMatch(migration -> {
            if (migration.getVersion() == null || !"1".equals(migration.getVersion().getVersion())) {
                return false;
            }
            return (migration.getState() == MigrationState.SUCCESS && "SQL".equals(migration.getType().name())
                    && migration.getChecksum() != null)
                    || (migration.getState() == MigrationState.BASELINE && migration.getType().isBaseline()
                    && target.migrator.equals(migration.getInstalledBy())
                    && DESCRIPTION.equals(migration.getDescription()) && migration.getChecksum() == null);
        });
        if (!recognized) {
            throw new SQLException("Central V1 history is empty or unrecognized; operator review is required");
        }
    }

    private static void requireSchema(String schema) {
        if (!SCHEMAS.contains(schema)) {
            throw new IllegalArgumentException("Only platform and billing are supported");
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured and non-blank");
        }
        return value;
    }

    enum Target {
        DEVELOPMENT("hich_erp", "DB_", "hich_migrator_dev", "hich_runtime_dev"),
        TEST("hich_erp_test", "TEST_DB_", "hich_migrator_test", "hich_runtime_test");

        final String database;
        final String prefix;
        final String migrator;
        final String runtime;

        Target(String database, String prefix, String migrator, String runtime) {
            this.database = database;
            this.prefix = prefix;
            this.migrator = migrator;
            this.runtime = runtime;
        }

        static Target forDatabase(String database) {
            return switch (database) {
                case "hich_erp" -> DEVELOPMENT;
                case "hich_erp_test" -> TEST;
                default -> throw new IllegalArgumentException("Only explicitly selected Hich databases are supported");
            };
        }
    }

    private record SchemaState(boolean history, boolean pristine) {
    }
}
