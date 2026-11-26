package com.saaserp.erp.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.saaserp.erp.shared.cache.application.TenantCache;
import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier;
import com.saaserp.erp.shared.tenant.infrastructure.persistence.TenantConnectionProvider;
import com.saaserp.erp.shared.tenant.infrastructure.persistence.TenantIdentifierResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Read-only PostgreSQL fixture probes paired with expiring, explicitly owned Redis test keys. */
@SpringBootTest
@ActiveProfiles("test")
@Timeout(value = 45, unit = TimeUnit.SECONDS)
class CrossTenantSecurityIntegrationTest {

    private static final TenantId A = TenantId.parse("00000000-0000-0000-0000-000000000501");
    private static final TenantId B = TenantId.parse("00000000-0000-0000-0000-000000000502");
    private static final TenantId DENIED = TenantId.parse("00000000-0000-0000-0000-000000000503");
    private static final TenantId MISSING = TenantId.parse("00000000-0000-0000-0000-000000000504");
    private static final String KEY = "same_record";
    private final String namespace = "security_" + UUID.randomUUID().toString().replace("-", "");
    private final Set<TenantId> written = ConcurrentHashMap.newKeySet();
    private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

    @Autowired
    @Qualifier("dataSource")
    private HikariDataSource applicationRuntimePool;

    @Autowired
    private TenantCache cache;

    private HikariDataSource pool;
    private TenantConnectionProvider provider;

    @BeforeEach
    void requireRestrictedTestIdentityAndExistingFixtures() throws SQLException {
        assertThat(TenantContextHolder.current()).isEmpty();
        try (Connection connection = applicationRuntimePool.getConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery("""
                    SELECT pg_catalog.current_database() = 'hich_erp_test'
                        AND current_user = 'hich_runtime_test' AND current_user = session_user
                        AND NOT pg_catalog.has_database_privilege(current_user, pg_catalog.current_database(), 'CREATE,TEMP')
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
            }
            for (TenantId tenant : new TenantId[] {A, B, DENIED, MISSING}) {
                try (PreparedStatement fixture = connection.prepareStatement("""
                        SELECT pg_catalog.has_schema_privilege(current_user, n.oid, 'USAGE')
                        FROM pg_catalog.pg_namespace n WHERE n.nspname = ?
                        """)) {
                    fixture.setQueryTimeout(5);
                    fixture.setString(1, TenantSchemaIdentifier.from(tenant).value());
                    try (ResultSet rows = fixture.executeQuery()) {
                        if (tenant.equals(MISSING)) {
                            assertThat(rows.next()).as("Fixture 504 must stay absent").isFalse();
                        } else {
                            assertThat(rows.next()).as("Existing Task 05 fixtures are required").isTrue();
                            assertThat(rows.getBoolean(1)).isEqualTo(!tenant.equals(DENIED));
                        }
                    }
                }
            }
        }
        pool = createPool(1);
        provider = new TenantConnectionProvider(pool);
    }

    @AfterEach
    void cleanOnlyOwnedCacheKeysAndCloseTestPool() throws Exception {
        var failures = new ArrayList<RuntimeException>();
        try {
            for (TenantId tenant : written) {
                try (var scope = bind(tenant)) {
                    cache.delete(namespace, KEY);
                } catch (RuntimeException failure) {
                    failures.add(failure);
                }
            }
        } finally {
            try {
                if (pool != null) {
                    pool.close();
                }
            } finally {
                assertThat(TenantContextHolder.current()).isEmpty();
            }
        }
        if (!failures.isEmpty()) {
            Exception failure = new Exception("Explicit cross-component test-key cleanup failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    @Test
    void eachTenantCachesOnlyItsOwnRoutedPostgresRepresentation() throws Exception {
        seedBothTenants();
        assertOwnCachedRepresentation(A);
        assertOwnCachedRepresentation(B);
    }

    @Test
    void tenantACacheUpdateDoesNotChangeTenantBOrEitherDatabaseFixture() throws Exception {
        seedBothTenants();
        try (var scope = bind(A)) {
            writeCurrentCache("A updated cache representation");
            assertThat(cache.get(namespace, KEY)).contains("A updated cache representation");
            assertThat(readCurrent(provider, null).marker()).isEqualTo("task05-a");
        }
        assertOwnCachedRepresentation(B);
    }

    @Test
    void tenantACacheDeletionPreservesTenantBAndBothRoutedFixtures() throws Exception {
        seedBothTenants();
        try (var scope = bind(A)) {
            assertThat(cache.delete(namespace, KEY)).isTrue();
            assertThat(cache.get(namespace, KEY)).isEmpty();
            assertThat(readCurrent(provider, null).marker()).isEqualTo("task05-a");
        }
        assertOwnCachedRepresentation(B);
    }

    @Test
    void reusedWorkerAndPhysicalConnectionSelectTheNewTenantInBothStores() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<WorkerResult> first = worker.submit(() -> workerRoundTrip(A, provider, null));
            WorkerResult a = first.get(15, TimeUnit.SECONDS);
            // Inspect release directly before another provider acquisition could repair it.
            assertPooledBaseline(pool, a.probe().backend());
            Future<WorkerResult> second = worker.submit(() -> workerRoundTrip(B, provider, null));
            WorkerResult b = second.get(15, TimeUnit.SECONDS);
            assertThat(b.worker()).isEqualTo(a.worker());
            assertThat(b.probe().backend()).isEqualTo(a.probe().backend());
            assertPooledBaseline(pool, b.probe().backend());
            assertOwnCachedRepresentation(A);
            assertOwnCachedRepresentation(B);
        } finally {
            shutdown(worker);
        }
    }

    @Test
    void simultaneousPostgresLeasesAndCacheWritesRespectIndependentContexts() throws Exception {
        try (HikariDataSource concurrentPool = createPool(2)) {
            var concurrentProvider = new TenantConnectionProvider(concurrentPool);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            CyclicBarrier bothLeased = new CyclicBarrier(2);
            try {
                Future<WorkerResult> first = workers.submit(() -> workerRoundTrip(A, concurrentProvider, bothLeased));
                Future<WorkerResult> second = workers.submit(() -> workerRoundTrip(B, concurrentProvider, bothLeased));
                WorkerResult a = first.get(20, TimeUnit.SECONDS);
                WorkerResult b = second.get(20, TimeUnit.SECONDS);
                assertThat(a.probe().backend()).isNotEqualTo(b.probe().backend());
                assertThat(a.worker()).isNotEqualTo(b.worker());
                assertThat(concurrentPool.getHikariPoolMXBean().getActiveConnections()).isZero();
                assertOwnCachedRepresentation(A);
                assertOwnCachedRepresentation(B);
            } finally {
                shutdown(workers);
            }
        }
    }

    @Test
    void missingContextRejectsDatabaseResolutionAcquisitionAndAllCacheOperations() {
        assertNoTenantOperations();
    }

    @Test
    void mismatchedDatabaseIdentifierCannotSwitchTheActiveCacheIdentity() throws Exception {
        seedBothTenants();
        try (var scope = bind(A)) {
            assertThrows(IllegalStateException.class, () -> provider.getConnection(B.toString()));
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(A.toString());
            assertThat(cache.get(namespace, KEY)).contains(expected(A));
            assertThat(readCurrent(provider, null).marker()).isEqualTo("task05-a");
        }
        assertOwnCachedRepresentation(B);
    }

    @Test
    void postgresFailureClosesContextBeforeTheWorkerIsReused() throws Exception {
        seedBothTenants();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Void> failed = worker.submit(() -> {
                assertThat(TenantContextHolder.current()).isEmpty();
                try (var scope = bind(A)) {
                    Connection connection = provider.getConnection(resolver.resolveCurrentTenantIdentifier());
                    try (Statement statement = connection.createStatement()) {
                        statement.setQueryTimeout(5);
                        statement.execute("SELECT 1 / 0");
                    } finally {
                        provider.releaseConnection(A.toString(), connection);
                    }
                    writeCurrentCache("must never be written after failed SQL");
                    return null;
                } finally {
                    assertThat(TenantContextHolder.current()).isEmpty();
                }
            });
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> failed.get(15, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOf(SQLException.class);
            assertThat(((SQLException) failure.getCause()).getSQLState()).isEqualTo("22012");
            Future<WorkerResult> next = worker.submit(() -> {
                assertNoTenantOperations();
                return workerRoundTrip(B, provider, null);
            });
            WorkerResult result = next.get(15, TimeUnit.SECONDS);
            assertThat(result.probe().marker()).isEqualTo("task05-b");
            assertOwnCachedRepresentation(A);
            assertOwnCachedRepresentation(B);
        } finally {
            shutdown(worker);
        }
    }

    @Test
    void deniedAndMissingRoutesNeverFallBackToAnotherTenantsCachedData() throws Exception {
        seedBothTenants();
        for (TenantId tenant : new TenantId[] {DENIED, MISSING}) {
            try (var scope = bind(tenant)) {
                assertThrows(SQLException.class, () -> loadAndCache(provider, null));
                assertThat(TenantContextHolder.requireCurrent().tenantId()).isEqualTo(tenant);
                assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(tenant.toString());
                assertThat(cache.get(namespace, KEY)).isEmpty();
                // The cache contract independently requires context, not schema existence.
                // An explicit post-failure cache call must still stay under this exact identity.
                writeCurrentCache("failed-route-only");
                assertThat(cache.get(namespace, KEY)).contains("failed-route-only");
            }
            assertOwnCachedRepresentation(A);
            assertOwnCachedRepresentation(B);
        }
    }

    @Test
    void closedContextCannotAcquireDatabaseOrReuseItsCachedEntry() throws Exception {
        try (var scope = bind(A)) {
            loadAndCache(provider, null);
        }
        assertNoTenantOperations();
        assertOwnCachedRepresentation(B, false);
        assertOwnCachedRepresentation(A);
    }

    private void assertNoTenantOperations() {
        assertThat(TenantContextHolder.current()).isEmpty();
        assertThrows(IllegalStateException.class, resolver::resolveCurrentTenantIdentifier);
        assertThrows(IllegalStateException.class, () -> provider.getConnection(A.toString()));
        assertThrows(IllegalStateException.class, () -> cache.get(namespace, KEY));
        assertThrows(IllegalStateException.class, () -> cache.put(namespace, KEY, "forbidden", Duration.ofSeconds(60)));
        assertThrows(IllegalStateException.class, () -> cache.delete(namespace, KEY));
    }

    private void seedBothTenants() throws Exception {
        try (var scope = bind(A)) {
            loadAndCache(provider, null);
        }
        try (var scope = bind(B)) {
            assertThat(cache.get(namespace, KEY)).isEmpty();
            loadAndCache(provider, null);
        }
    }

    private Probe loadAndCache(TenantConnectionProvider routing, CyclicBarrier bothLeased) throws Exception {
        Probe probe = readCurrent(routing, bothLeased);
        // No write is attempted unless both the fixture read and lease release succeeded.
        writeCurrentCache(probe.representation());
        return probe;
    }

    private Probe readCurrent(TenantConnectionProvider routing, CyclicBarrier bothLeased) throws Exception {
        TenantId tenant = TenantContextHolder.requireCurrent().tenantId();
        String identifier = resolver.resolveCurrentTenantIdentifier();
        assertThat(identifier).isEqualTo(tenant.toString());
        Connection connection = routing.getConnection(identifier);
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery("""
                    SELECT pg_catalog.pg_backend_pid(), pg_catalog.current_schema(), marker,
                        pg_catalog.current_setting('search_path'),
                        pg_catalog.current_schemas(true)::text[] = ARRAY['pg_catalog', pg_catalog.current_schema()]::text[],
                        pg_catalog.current_database(), current_user, pg_catalog.pg_my_temp_schema()
                    FROM routing_probe
                    """)) {
                assertThat(rows.next()).isTrue();
                Probe probe = new Probe(rows.getLong(1), rows.getString(2), rows.getString(3));
                String schema = TenantSchemaIdentifier.from(tenant).value();
                assertThat(probe.schema()).isEqualTo(schema);
                assertThat(probe.marker()).isEqualTo(tenant.equals(A) ? "task05-a" : "task05-b");
                assertThat(rows.getString(4)).isEqualTo(schema + ", pg_temp");
                assertThat(rows.getBoolean(5)).isTrue();
                assertThat(rows.getString(6)).isEqualTo("hich_erp_test");
                assertThat(rows.getString(7)).isEqualTo("hich_runtime_test");
                assertThat(rows.getLong(8)).isZero();
                assertThat(rows.next()).isFalse();
                if (bothLeased != null) {
                    bothLeased.await(8, TimeUnit.SECONDS);
                }
                return probe;
            }
        } finally {
            routing.releaseConnection(identifier, connection);
        }
    }

    private void writeCurrentCache(String value) {
        written.add(TenantContextHolder.requireCurrent().tenantId());
        cache.put(namespace, KEY, value, Duration.ofSeconds(60));
    }

    private void assertOwnCachedRepresentation(TenantId tenant) throws Exception {
        assertOwnCachedRepresentation(tenant, true);
    }

    private void assertOwnCachedRepresentation(TenantId tenant, boolean cached) throws Exception {
        try (var scope = bind(tenant)) {
            Probe probe = readCurrent(provider, null);
            assertThat(probe.representation()).isEqualTo(expected(tenant));
            if (cached) {
                assertThat(cache.get(namespace, KEY)).contains(probe.representation());
            } else {
                assertThat(cache.get(namespace, KEY)).isEmpty();
            }
        }
    }

    private WorkerResult workerRoundTrip(TenantId tenant, TenantConnectionProvider routing,
            CyclicBarrier bothLeased) throws Exception {
        assertThat(TenantContextHolder.current()).isEmpty();
        try (var scope = bind(tenant)) {
            Probe probe = loadAndCache(routing, bothLeased);
            assertThat(cache.get(namespace, KEY)).contains(probe.representation());
            return new WorkerResult(Thread.currentThread().threadId(), probe);
        } finally {
            assertThat(TenantContextHolder.current()).isEmpty();
        }
    }

    private static TenantContextHolder.Scope bind(TenantId tenant) {
        return TenantContextHolder.bind(new TenantContext(tenant));
    }

    private static String expected(TenantId tenant) {
        return TenantSchemaIdentifier.from(tenant).value() + "|" + (tenant.equals(A) ? "task05-a" : "task05-b");
    }

    private HikariDataSource createPool(int size) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(applicationRuntimePool.getJdbcUrl());
        config.setUsername(applicationRuntimePool.getUsername());
        config.setPassword(applicationRuntimePool.getPassword());
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("CrossTenantSecurity-" + UUID.randomUUID());
        config.addDataSourceProperty("socketTimeout", "5");
        config.addDataSourceProperty("prepareThreshold", "1");
        return new HikariDataSource(config);
    }

    private static void assertPooledBaseline(HikariDataSource source, long backend) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery("""
                    SELECT pg_catalog.pg_backend_pid(),
                        pg_catalog.current_schema() = 'pg_catalog'
                        AND pg_catalog.current_setting('search_path') = 'pg_catalog, pg_temp'
                        AND pg_catalog.current_schemas(true)::text[] = ARRAY['pg_catalog']::text[]
                        AND pg_catalog.pg_my_temp_schema() = 0
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(backend);
                assertThat(rows.getBoolean(2)).isTrue();
            }
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private record Probe(long backend, String schema, String marker) {
        String representation() {
            return schema + "|" + marker;
        }
    }

    private record WorkerResult(long worker, Probe probe) {
    }
}
