package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.MultiTenancySettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Requires the explicitly reviewed test-only fixtures in docs/architecture/hibernate-tenant-routing.md. */
@SpringBootTest
@ActiveProfiles("test")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TenantRoutingDatabaseIntegrationTest {

    private static final TenantId FIRST = TenantId.parse("00000000-0000-0000-0000-000000000501");
    private static final TenantId SECOND = TenantId.parse("00000000-0000-0000-0000-000000000502");
    private static final TenantId DENIED = TenantId.parse("00000000-0000-0000-0000-000000000503");
    private static final TenantId MISSING = TenantId.parse("00000000-0000-0000-0000-000000000504");

    @Autowired
    @Qualifier("dataSource")
    private HikariDataSource applicationRuntimePool;

    @Autowired
    private EntityManagerFactory centralEntityManagerFactory;

    @Autowired
    private ApplicationContext applicationContext;

    private HikariDataSource testPool;
    private TenantConnectionProvider provider;

    @BeforeEach
    void requireRestrictedTestDatabaseAndCreateSingleConnectionTestPool() throws SQLException {
        assertThat(TenantContextHolder.current()).isEmpty();
        // Check the actual connected identity before performing any test routing.
        try (Connection connection = applicationRuntimePool.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT pg_catalog.current_database(), current_user")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("hich_erp_test");
            assertThat(rows.getString(2)).isEqualTo("hich_runtime_test");
        }
        // Test resource only, never a Spring bean. Reuse exactly the configured restricted
        // runtime credentials, with a one-connection pool to make physical reuse deterministic.
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(applicationRuntimePool.getJdbcUrl());
        config.setUsername(applicationRuntimePool.getUsername());
        config.setPassword(applicationRuntimePool.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("TenantRoutingIntegrationTest");
        testPool = new HikariDataSource(config);
        provider = new TenantConnectionProvider(testPool);
    }

    @AfterEach
    void closeTestPoolAndAssertContextCleanup() {
        try {
            if (testPool != null) {
                testPool.close();
            }
        } finally {
            assertThat(TenantContextHolder.current()).isEmpty();
        }
    }

    @Test
    void centralPersistenceStillStartsAndQueriesWithoutTenantRegistration() {
        assertThat(centralEntityManagerFactory.isOpen()).isTrue();
        assertThat(centralEntityManagerFactory.getProperties()).doesNotContainKeys(
                MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER,
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER);
        assertThat(applicationContext.getBeansOfType(TenantConnectionProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(TenantIdentifierResolver.class)).isEmpty();
        EntityManager manager = centralEntityManagerFactory.createEntityManager();
        try {
            assertThat(manager.createNativeQuery("SELECT pg_catalog.current_database()").getSingleResult())
                    .isEqualTo("hich_erp_test");
            assertThat(manager.createNativeQuery("""
                    SELECT pg_catalog.has_schema_privilege(current_user, 'platform', 'USAGE')
                        AND pg_catalog.has_schema_privilege(current_user, 'billing', 'USAGE')
                    """).getSingleResult()).isEqualTo(true);
        } finally {
            manager.close();
        }
    }

    @Test
    void bootstrapIsCatalogOnlyAndDoesNotNeedCustomerContext() throws SQLException {
        Connection connection = provider.getAnyConnection();
        try {
            assertBaseline(connection);
        } finally {
            provider.releaseAnyConnection(connection);
        }
    }

    @Test
    void twoTenantFixturesRouteUnqualifiedReadsToDistinctData() throws SQLException {
        requireReadableFixture(FIRST);
        requireReadableFixture(SECOND);
        assertThat(readMarker(FIRST)).isEqualTo("task05-a");
        assertThat(readMarker(SECOND)).isEqualTo("task05-b");
    }

    @Test
    void releaseRestoresBaselineOnTheSamePhysicalConnection() throws SQLException {
        requireReadableFixture(FIRST);
        long tenantBackend;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            try {
                tenantBackend = backendId(connection);
                assertThat(connection.getSchema()).isEqualTo(TenantSchemaIdentifier.from(FIRST).value());
            } finally {
                provider.releaseConnection(FIRST.toString(), connection);
            }
        }
        // Read the pooled state directly: bootstrap acquisition would reset the
        // path itself and could conceal a broken release reset.
        try (Connection baseline = testPool.getConnection()) {
            assertThat(backendId(baseline)).isEqualTo(tenantBackend);
            assertBaseline(baseline);
        }
    }

    @Test
    void rollbackOfOwnedTransactionCannotUndoSubsequentReleaseReset() throws SQLException {
        requireReadableFixture(FIRST);
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT pg_catalog.set_config('search_path', 'billing', false)");
                } finally {
                    // The test owns this transaction, unlike the provider.
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            } finally {
                provider.releaseConnection(FIRST.toString(), connection);
            }
        }
        // Inspect the reset before another provider acquisition can repair it.
        try (Connection baseline = testPool.getConnection()) {
            assertBaseline(baseline);
        }
    }

    @Test
    void outstandingTransactionalReleaseEvictsThePhysicalConnection() throws SQLException {
        requireReadableFixture(FIRST);
        long discardedBackend;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            connection.setAutoCommit(false);
            // This query actually starts the test-owned transaction.
            discardedBackend = backendId(connection);
            assertThrows(SQLException.class, () -> provider.releaseConnection(FIRST.toString(), connection));
        }
        Connection replacement = provider.getAnyConnection();
        try {
            assertThat(backendId(replacement)).isNotEqualTo(discardedBackend);
            assertBaseline(replacement);
        } finally {
            provider.releaseAnyConnection(replacement);
        }
    }

    @Test
    void nonexistentSchemaFailsWithoutFallbackAndLeavesPoolUsable() throws SQLException {
        assertThat(schemaExists(MISSING)).as("Test-only missing schema must stay absent").isFalse();
        try (var scope = TenantContextHolder.bind(new TenantContext(MISSING))) {
            assertThrows(SQLException.class, () -> provider.getConnection(MISSING.toString()));
        }
        Connection replacement = provider.getAnyConnection();
        try {
            assertBaseline(replacement);
        } finally {
            provider.releaseAnyConnection(replacement);
        }
    }

    @Test
    void existingButUnauthorizedSchemaIsRejected() throws SQLException {
        assertThat(schemaExists(DENIED)).as("Review and install Task 05 test-only fixture schema 503").isTrue();
        try (Connection connection = testPool.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT pg_catalog.has_schema_privilege(current_user, ?, 'USAGE')")) {
            statement.setString(1, TenantSchemaIdentifier.from(DENIED).value());
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
        }
        try (var scope = TenantContextHolder.bind(new TenantContext(DENIED))) {
            assertThrows(SQLException.class, () -> provider.getConnection(DENIED.toString()));
        }
    }

    private String readMarker(TenantId tenant) throws SQLException {
        try (var scope = TenantContextHolder.bind(new TenantContext(tenant))) {
            Connection connection = provider.getConnection(tenant.toString());
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT marker FROM routing_probe")) {
                assertThat(rows.next()).isTrue();
                String marker = rows.getString(1);
                assertThat(rows.next()).isFalse();
                return marker;
            } finally {
                provider.releaseConnection(tenant.toString(), connection);
            }
        }
    }

    private void requireReadableFixture(TenantId tenant) throws SQLException {
        assertThat(schemaExists(tenant)).as("Review and install Task 05 test-only tenant fixtures").isTrue();
        try (Connection connection = testPool.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT pg_catalog.pg_get_userbyid(c.relowner) = 'hich_migrator_test'
                            AND pg_catalog.has_schema_privilege(current_user, n.oid, 'USAGE')
                            AND NOT pg_catalog.has_schema_privilege(current_user, n.oid, 'CREATE')
                            AND pg_catalog.has_table_privilege(current_user, c.oid, 'SELECT')
                        FROM pg_catalog.pg_namespace n
                        JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid
                        WHERE n.nspname = ? AND c.relname = 'routing_probe' AND c.relkind = 'r'
                        """)) {
            statement.setString(1, TenantSchemaIdentifier.from(tenant).value());
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).as("Task 05 routing_probe fixture is required").isTrue();
                assertThat(rows.getBoolean(1)).as("Task 05 fixture owner and grants must match the proposal").isTrue();
            }
        }
    }

    private boolean schemaExists(TenantId tenant) throws SQLException {
        try (Connection connection = testPool.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = ?")) {
            statement.setString(1, TenantSchemaIdentifier.from(tenant).value());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static long backendId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT pg_catalog.pg_backend_pid()")) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static void assertBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT pg_catalog.current_setting('search_path') = 'pg_catalog, pg_temp'
                            AND pg_catalog.current_schema() = 'pg_catalog'
                            AND pg_catalog.current_schemas(true)::text[] = ARRAY['pg_catalog']::text[]
                            AND pg_catalog.pg_my_temp_schema() = 0
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isTrue();
        }
    }
}
