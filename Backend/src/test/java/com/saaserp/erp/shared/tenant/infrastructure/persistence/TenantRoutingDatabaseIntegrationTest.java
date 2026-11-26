package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.postgresql.core.BaseConnection;
import org.postgresql.core.TransactionState;
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
        testPool = createTestPool(1);
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
                // Hikari can cache getSchema() after reset's setSchema(null).
                // Verify the effective PostgreSQL state independently of that cache.
                assertTenantPath(connection, FIRST);
                assertMarker(connection, "task05-a");
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

    @Test
    void mutableSqlSessionSettingsAreResetBeforePhysicalReuse() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        String defaultSeqScan;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            try {
                backend = backendId(connection);
                defaultSeqScan = scalar(connection, "SELECT pg_catalog.current_setting('enable_seqscan')");
                execute(connection, "SET search_path TO billing");
                execute(connection, "SET TimeZone TO 'Pacific/Auckland'");
                execute(connection, "SET application_name TO 'previous-tenant'");
                execute(connection, "SET statement_timeout TO '17s'");
                execute(connection, "SET lock_timeout TO '11s'");
                execute(connection, "SET idle_in_transaction_session_timeout TO '19s'");
                execute(connection, "SET enable_seqscan TO off");
                execute(connection, "SET default_transaction_isolation TO 'serializable'");
                execute(connection, "SET default_transaction_read_only TO on");
            } finally {
                provider.releaseConnection(FIRST.toString(), connection);
            }
        }
        // Inspect directly, before another provider checkout could repair bad release cleanup.
        try (Connection clean = testPool.getConnection()) {
            assertThat(backendId(clean)).isEqualTo(backend);
            assertBaseline(clean);
            assertSessionBaseline(clean);
            assertThat(scalar(clean, "SELECT pg_catalog.current_setting('enable_seqscan')"))
                    .isEqualTo(defaultSeqScan);
        }
    }

    @Test
    void jdbcPropertyChangesCannotUndoVerifiedBaselineOnProxyClose() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        int networkTimeout;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            try {
                backend = backendId(connection);
                networkTimeout = connection.getNetworkTimeout();
                connection.setNetworkTimeout(Runnable::run, 2000);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setReadOnly(true);
                connection.setSchema("billing");
            } finally {
                provider.releaseConnection(FIRST.toString(), connection);
            }
        }
        try (Connection clean = testPool.getConnection()) {
            assertThat(backendId(clean)).isEqualTo(backend);
            assertThat(clean.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(clean.getNetworkTimeout()).isEqualTo(networkTimeout);
            assertThat(clean.isReadOnly()).isFalse();
            assertBaseline(clean);
            assertSessionBaseline(clean);
        }
    }

    @Test
    void advisoryLocksListenAndQueuedNotificationsAreCleanedOnSameBackend() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            try {
                backend = backendId(connection);
                UUID key = UUID.randomUUID();
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT pg_catalog.pg_try_advisory_lock(?, ?)")) {
                    lock.setInt(1, (int) key.getMostSignificantBits());
                    lock.setInt(2, (int) key.getLeastSignificantBits());
                    try (ResultSet rows = lock.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getBoolean(1)).isTrue();
                    }
                }
                execute(connection, "LISTEN task06_session_probe");
                execute(connection, "SELECT pg_catalog.pg_notify('task06_session_probe', 'previous-tenant')");
            } finally {
                provider.releaseConnection(FIRST.toString(), connection);
            }
        }
        try (Connection clean = testPool.getConnection()) {
            assertThat(backendId(clean)).isEqualTo(backend);
            assertSessionBaseline(clean);
            var notifications = clean.unwrap(BaseConnection.class).getNotifications();
            assertThat(notifications == null || notifications.length == 0).isTrue();
        }
    }

    @Test
    void sqlPreparedStatementsAreRemovedAndDriverStatementsReparseAcrossTenants() throws SQLException {
        requireReadableFixture(FIRST);
        requireReadableFixture(SECOND);
        Long backend = null;
        for (TenantId tenant : new TenantId[] {FIRST, SECOND, FIRST}) {
            String marker = tenant.equals(FIRST) ? "task05-a" : "task05-b";
            try (var scope = TenantContextHolder.bind(new TenantContext(tenant))) {
                Connection connection = provider.getConnection(tenant.toString());
                try {
                    long currentBackend = backendId(connection);
                    if (backend == null) {
                        backend = currentBackend;
                    }
                    assertThat(currentBackend).isEqualTo(backend.longValue());
                    execute(connection, "PREPARE task06_named_probe AS SELECT marker FROM routing_probe");
                    assertThat(scalar(connection, "EXECUTE task06_named_probe")).isEqualTo(marker);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT marker FROM routing_probe WHERE marker = ?")) {
                        for (int execution = 0; execution < 7; execution++) {
                            statement.setString(1, marker);
                            try (ResultSet rows = statement.executeQuery()) {
                                assertThat(rows.next()).isTrue();
                                assertThat(rows.getString(1)).isEqualTo(marker);
                                assertThat(rows.next()).isFalse();
                            }
                        }
                    }
                } finally {
                    provider.releaseConnection(tenant.toString(), connection);
                }
            }
            try (Connection clean = testPool.getConnection()) {
                assertThat(scalar(clean, """
                        SELECT count(*)::text FROM pg_catalog.pg_prepared_statements
                        WHERE from_sql OR name = 'task06_named_probe'
                        """)).isEqualTo("0");
                assertSessionBaseline(clean);
            }
        }
    }

    @Test
    void repeatedCrossTenantReuseDoesNotRetainPreviousTenantSettings() throws SQLException {
        requireReadableFixture(FIRST);
        requireReadableFixture(SECOND);
        Long backend = null;
        for (int iteration = 0; iteration < 12; iteration++) {
            TenantId tenant = iteration % 2 == 0 ? FIRST : SECOND;
            try (var scope = TenantContextHolder.bind(new TenantContext(tenant))) {
                Connection connection = provider.getConnection(tenant.toString());
                try {
                    long currentBackend = backendId(connection);
                    if (backend == null) {
                        backend = currentBackend;
                    }
                    assertThat(currentBackend).isEqualTo(backend.longValue());
                    assertSessionBaseline(connection);
                    assertMarker(connection, tenant.equals(FIRST) ? "task05-a" : "task05-b");
                    execute(connection, "SET application_name TO 'prior-company'");
                } finally {
                    provider.releaseConnection(tenant.toString(), connection);
                }
            }
        }
        try (Connection clean = testPool.getConnection()) {
            assertBaseline(clean);
            assertSessionBaseline(clean);
        }
    }

    @Test
    void dirtyUnscopedPoolSessionIsResetBeforeTenantAdmission() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        try (Connection dirty = testPool.getConnection()) {
            backend = backendId(dirty);
            execute(dirty, "SET TimeZone TO 'Pacific/Auckland'");
            execute(dirty, "SET application_name TO 'unscoped-work'");
            execute(dirty, "SET search_path TO billing");
            execute(dirty, "LISTEN task06_dirty_checkout");
        }
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection admitted = provider.getConnection(FIRST.toString());
            try {
                assertThat(backendId(admitted)).isEqualTo(backend);
                assertSessionBaseline(admitted);
                assertMarker(admitted, "task05-a");
            } finally {
                provider.releaseConnection(FIRST.toString(), admitted);
            }
        }
    }

    @Test
    void manualSqlTransactionWithJdbcAutocommitTrueIsEvicted() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            backend = backendId(connection);
            execute(connection, "BEGIN");
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.unwrap(BaseConnection.class).getTransactionState()).isEqualTo(TransactionState.OPEN);
            assertThrows(SQLException.class, () -> provider.releaseConnection(FIRST.toString(), connection));
        }
        assertReplacedBackend(backend);
    }

    @Test
    void manualSqlFailedTransactionIsEvictedWithoutCleanupInsideTransaction() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            backend = backendId(connection);
            execute(connection, "BEGIN");
            SQLException failure = assertThrows(SQLException.class, () -> execute(connection, "SELECT 1 / 0"));
            assertThat(failure.getSQLState()).isEqualTo("22012");
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.unwrap(BaseConnection.class).getTransactionState()).isEqualTo(TransactionState.FAILED);
            assertThrows(SQLException.class, () -> provider.releaseConnection(FIRST.toString(), connection));
        }
        assertReplacedBackend(backend);
    }

    @Test
    void jdbcFailedTransactionIsEvictedWithoutReturningDirtyProxy() throws SQLException {
        requireReadableFixture(FIRST);
        long backend;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            backend = backendId(connection);
            connection.setAutoCommit(false);
            assertThrows(SQLException.class, () -> execute(connection, "SELECT 1 / 0"));
            assertThat(connection.unwrap(BaseConnection.class).getTransactionState()).isEqualTo(TransactionState.FAILED);
            assertThrows(SQLException.class, () -> provider.releaseConnection(FIRST.toString(), connection));
        }
        assertReplacedBackend(backend);
    }

    @Test
    void activeServerTransactionOnInitialCheckoutIsRejectedAndReplaced() throws SQLException {
        long backend;
        // This is an unowned test-pool checkout, not a provider lease. Deliberately
        // reproduce SQL transaction state that Hikari's JDBC autocommit flag misses.
        try (Connection dirty = testPool.getConnection()) {
            backend = backendId(dirty);
            execute(dirty, "BEGIN");
            assertThat(dirty.getAutoCommit()).isTrue();
        }
        assertThrows(SQLException.class, provider::getAnyConnection);
        assertReplacedBackend(backend);
    }

    @Test
    void timedOutStatementCannotLeakSettingsIntoTheNextTenant() throws SQLException {
        requireReadableFixture(FIRST);
        requireReadableFixture(SECOND);
        long backend;
        boolean evicted = false;
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            backend = backendId(connection);
            execute(connection, "SET statement_timeout TO '100ms'");
            // A deliberate long-running SQL statement tests cancellation, not thread synchronization.
            SQLException failure = assertThrows(SQLException.class,
                    () -> execute(connection, "SELECT pg_catalog.pg_sleep(1)"));
            assertThat(failure.getSQLState()).isEqualTo("57014");
            try {
                provider.releaseConnection(FIRST.toString(), connection);
            } catch (SQLException cleanupFailure) {
                // Extremely short inherited timeouts can also cancel cleanup. Only
                // eviction is acceptable then; the next backend assertion checks it.
                assertThat(cleanupFailure.getMessage()).isEqualTo("Unable to safely release a routing connection");
                evicted = true;
            }
        }
        try (var scope = TenantContextHolder.bind(new TenantContext(SECOND))) {
            Connection next = provider.getConnection(SECOND.toString());
            try {
                if (evicted) {
                    assertThat(backendId(next)).isNotEqualTo(backend);
                }
                assertSessionBaseline(next);
                assertMarker(next, "task05-b");
            } finally {
                provider.releaseConnection(SECOND.toString(), next);
            }
        }
    }

    @Test
    void forbiddenTemporaryObjectsAndSessionAuthorizationDoNotChangeIdentity() throws SQLException {
        requireReadableFixture(FIRST);
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            Connection connection = provider.getConnection(FIRST.toString());
            try {
                SQLException temporary = assertThrows(SQLException.class,
                        () -> execute(connection, "CREATE TEMP TABLE task06_denied_temp (id integer)"));
                assertThat(temporary.getSQLState()).isEqualTo("42501");
                SQLException authorization = assertThrows(SQLException.class,
                        () -> execute(connection, "SET SESSION AUTHORIZATION hich_dev"));
                assertThat(authorization.getSQLState()).isEqualTo("42501");
                assertThat(scalar(connection, "SELECT current_user::text")).isEqualTo("hich_runtime_test");
            } finally {
                provider.releaseConnection(FIRST.toString(), connection);
            }
        }
        try (Connection clean = testPool.getConnection()) {
            assertBaseline(clean);
            assertSessionBaseline(clean);
        }
    }

    @Test
    void simultaneousTenantsUseIndependentSessionsInABoundedPool() throws Exception {
        requireReadableFixture(FIRST);
        requireReadableFixture(SECOND);
        CountDownLatch bothActive = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (HikariDataSource concurrentPool = createTestPool(2)) {
            TenantConnectionProvider concurrentProvider = new TenantConnectionProvider(concurrentPool);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Long> first = executor.submit(concurrentTenant(
                        concurrentProvider, FIRST, "task05-a", bothActive, release));
                Future<Long> second = executor.submit(concurrentTenant(
                        concurrentProvider, SECOND, "task05-b", bothActive, release));
                assertThat(bothActive.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(TenantContextHolder.current()).isEmpty();
                release.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
                assertThat(concurrentPool.getHikariPoolMXBean().getActiveConnections()).isZero();
                // Borrow all physical sessions directly before another provider reset.
                try (Connection a = concurrentPool.getConnection(); Connection b = concurrentPool.getConnection()) {
                    assertBaseline(a);
                    assertBaseline(b);
                    assertSessionBaseline(a);
                    assertSessionBaseline(b);
                }
            } finally {
                release.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private HikariDataSource createTestPool(int size) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(applicationRuntimePool.getJdbcUrl());
        config.setUsername(applicationRuntimePool.getUsername());
        config.setPassword(applicationRuntimePool.getPassword());
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("TenantRoutingIntegrationTest-" + size);
        config.addDataSourceProperty("prepareThreshold", "1");
        config.addDataSourceProperty("socketTimeout", "5");
        return new HikariDataSource(config);
    }

    private void assertReplacedBackend(long discardedBackend) throws SQLException {
        Connection replacement = provider.getAnyConnection();
        try {
            assertThat(backendId(replacement)).isNotEqualTo(discardedBackend);
            assertBaseline(replacement);
            assertSessionBaseline(replacement);
        } finally {
            provider.releaseAnyConnection(replacement);
        }
    }

    private static Callable<Long> concurrentTenant(TenantConnectionProvider provider, TenantId tenant,
            String marker, CountDownLatch bothActive, CountDownLatch release) {
        return () -> {
            assertThat(TenantContextHolder.current()).isEmpty();
            try (var scope = TenantContextHolder.bind(new TenantContext(tenant))) {
                Connection connection = provider.getConnection(tenant.toString());
                try {
                    long backend = backendId(connection);
                    assertMarker(connection, marker);
                    assertSessionBaseline(connection);
                    bothActive.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT pg_catalog.set_config('application_name', ?, false)")) {
                        statement.setString(1, marker);
                        statement.execute();
                    }
                    assertMarker(connection, marker);
                    return backend;
                } finally {
                    provider.releaseConnection(tenant.toString(), connection);
                }
            } finally {
                assertThat(TenantContextHolder.current()).isEmpty();
            }
        };
    }

    private static void assertMarker(Connection connection, String expected) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT marker FROM routing_probe")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(expected);
            assertThat(rows.next()).isFalse();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            statement.execute(sql);
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static void assertSessionBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT pg_catalog.current_setting('TimeZone') = 'UTC'
                            AND pg_catalog.current_setting('application_name') = 'hich-erp'
                            AND pg_catalog.current_setting('statement_timeout')::interval = interval '30 seconds'
                            AND pg_catalog.current_setting('lock_timeout')::interval = interval '5 seconds'
                            AND pg_catalog.current_setting('idle_in_transaction_session_timeout')::interval = interval '30 seconds'
                            AND pg_catalog.current_setting('default_transaction_isolation') = 'read committed'
                            AND pg_catalog.current_setting('default_transaction_read_only') = 'off'
                            AND current_user = session_user AND session_user = 'hich_runtime_test'
                            AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_listening_channels())
                            AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_locks
                                WHERE pid = pg_catalog.pg_backend_pid() AND locktype = 'advisory')
                            AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_prepared_statements WHERE from_sql)
                            AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_cursors WHERE is_holdable)
                            AND pg_catalog.pg_my_temp_schema() = 0
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isTrue();
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

    private static void assertTenantPath(Connection connection, TenantId tenant) throws SQLException {
        String schema = TenantSchemaIdentifier.from(tenant).value();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pg_catalog.current_schema(),
                    pg_catalog.current_setting('search_path'),
                    pg_catalog.current_schemas(true)::text[] = ARRAY['pg_catalog', ?]::text[],
                    pg_catalog.pg_my_temp_schema()
                """)) {
            statement.setQueryTimeout(5);
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).as("Effective PostgreSQL tenant schema").isEqualTo(schema);
                assertThat(rows.getString(2)).as("Configured PostgreSQL tenant search path")
                        .isEqualTo(schema + ", pg_temp");
                assertThat(rows.getBoolean(3)).as("Exact effective PostgreSQL search path").isTrue();
                assertThat(rows.getLong(4)).as("Temporary namespace must be absent").isZero();
                assertThat(rows.next()).isFalse();
            }
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
