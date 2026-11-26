package com.saaserp.erp.shared.cache.infrastructure.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.saaserp.erp.config.RedisTenantCacheConfig;
import com.saaserp.erp.shared.cache.application.TenantCache;
import com.saaserp.erp.shared.cache.application.TenantCacheKey;
import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Uses the existing Redis service, never PostgreSQL, database flushing or pattern deletion. */
@SpringJUnitConfig(classes = {DataRedisAutoConfiguration.class, RedisTenantCacheConfig.class})
@TestPropertySource(properties = {
        "spring.data.redis.host=${REDIS_HOST}",
        "spring.data.redis.port=${REDIS_PORT}",
        "spring.data.redis.password=${REDIS_PASSWORD}",
        "spring.data.redis.database=0",
        "spring.data.redis.timeout=5s",
        "spring.data.redis.connect-timeout=5s"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantRedisCacheIntegrationTest {

    private static final Duration TEST_TTL = Duration.ofSeconds(60);
    private final String namespace = "it_" + UUID.randomUUID().toString().replace("-", "");
    private final TenantContext tenantA = new TenantContext(new TenantId(UUID.randomUUID()));
    private final TenantContext tenantB = new TenantContext(new TenantId(UUID.randomUUID()));
    private final Set<OwnedKey> created = ConcurrentHashMap.newKeySet();

    @Autowired
    private TenantCache cache;

    // White-box TTL/serialization observation only; application callers receive TenantCache.
    @Autowired
    private StringRedisTemplate strings;

    @AfterEach
    void deleteOnlyExplicitlyOwnedKeys() throws Exception {
        assertThat(TenantContextHolder.current()).isEmpty();
        var failures = new ArrayList<RuntimeException>();
        for (OwnedKey key : created) {
            try (var scope = TenantContextHolder.bind(key.tenant())) {
                cache.delete(namespace, key.logicalKey());
            } catch (RuntimeException failure) {
                failures.add(failure);
            }
        }
        if (!failures.isEmpty()) {
            Exception failure = new Exception("Explicit Redis test-key cleanup failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    @Test
    void twoTenantsStoreDifferentUtf8ValuesAtIdenticalLogicalKeys() {
        store(tenantA, "tenant A — café", TEST_TTL);
        store(tenantB, "tenant B — 茶", TEST_TTL);
        assertValue(tenantA, "tenant A — café");
        assertValue(tenantB, "tenant B — 茶");
        try (var scope = TenantContextHolder.bind(tenantA)) {
            assertThat(strings.opsForValue().get(TenantCacheKey.current(namespace, "shared")))
                    .isEqualTo("tenant A — café");
        }
    }

    @Test
    void tenantACannotRetrieveAnEntryStoredOnlyByTenantB() {
        store(tenantB, "B only", TEST_TTL);
        try (var scope = TenantContextHolder.bind(tenantA)) {
            assertThat(cache.get(namespace, "shared")).isEmpty();
        }
        assertValue(tenantB, "B only");
    }

    @Test
    void updatingTenantADoesNotModifyTenantB() {
        store(tenantA, "A original", TEST_TTL);
        store(tenantB, "B original", TEST_TTL);
        store(tenantA, "A updated", TEST_TTL);
        assertValue(tenantA, "A updated");
        assertValue(tenantB, "B original");
    }

    @Test
    void deletingTenantADoesNotDeleteTenantB() {
        store(tenantA, "A", TEST_TTL);
        store(tenantB, "B", TEST_TTL);
        try (var scope = TenantContextHolder.bind(tenantA)) {
            assertThat(cache.delete(namespace, "shared")).isTrue();
            assertThat(cache.get(namespace, "shared")).isEmpty();
            assertThat(cache.delete(namespace, "shared")).isFalse();
        }
        assertValue(tenantB, "B");
    }

    @Test
    void serverReportsAnExplicitBoundedTtl() {
        store(tenantA, "expires", Duration.ofSeconds(30));
        try (var scope = TenantContextHolder.bind(tenantA)) {
            Long ttl = strings.getExpire(TenantCacheKey.current(namespace, "shared"), TimeUnit.MILLISECONDS);
            assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(30_000L);
        }
    }

    @Test
    void expiredValuesBecomeUnavailableWithinABoundedDeadline() throws InterruptedException {
        store(tenantA, "short lived", Duration.ofSeconds(2));
        assertValue(tenantA, "short lived");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try (var scope = TenantContextHolder.bind(tenantA)) {
            while (cache.get(namespace, "shared").isPresent() && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(25);
            }
            assertThat(cache.get(namespace, "shared")).isEmpty();
            Long ttl = strings.getExpire(TenantCacheKey.current(namespace, "shared"), TimeUnit.MILLISECONDS);
            assertThat(ttl).isEqualTo(-2L);
        }
    }

    @Test
    void missingContextRejectsAllOperations() {
        assertThrows(IllegalStateException.class, () -> cache.get(namespace, "shared"));
        assertThrows(IllegalStateException.class, () -> cache.put(namespace, "shared", "value", TEST_TTL));
        assertThrows(IllegalStateException.class, () -> cache.delete(namespace, "shared"));
        assertThat(created).isEmpty();
    }

    @Test
    void sequentialOperationsOnAReusedWorkerDoNotRetainTenantContext() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = worker.submit(() -> roundTrip(tenantA, "A"));
            Boolean firstClean = first.get(10, TimeUnit.SECONDS);
            Future<Boolean> second = worker.submit(() -> roundTrip(tenantB, "B"));
            Boolean secondClean = second.get(10, TimeUnit.SECONDS);
            assertThat(firstClean).isTrue();
            assertThat(secondClean).isTrue();
            assertValue(tenantA, "A");
            assertValue(tenantB, "B");
        } finally {
            shutdown(worker);
        }
    }

    @Test
    void concurrentTenantWritesReadsAndDeletesRemainIsolated() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Boolean> first = workers.submit(() -> concurrentRoundTrips(tenantA, "A", barrier));
            Future<Boolean> second = workers.submit(() -> concurrentRoundTrips(tenantB, "B", barrier));
            Boolean firstClean = first.get(20, TimeUnit.SECONDS);
            Boolean secondClean = second.get(20, TimeUnit.SECONDS);
            assertThat(firstClean).isTrue();
            assertThat(secondClean).isTrue();
        } finally {
            shutdown(workers);
        }
    }

    private boolean roundTrip(TenantContext tenant, String value) {
        store(tenant, value, TEST_TTL);
        assertValue(tenant, value);
        return TenantContextHolder.current().isEmpty();
    }

    private boolean concurrentRoundTrips(TenantContext tenant, String prefix, CyclicBarrier barrier) throws Exception {
        for (int index = 0; index < 5; index++) {
            String value = prefix + index;
            store(tenant, value, TEST_TTL);
            barrier.await(5, TimeUnit.SECONDS);
            assertValue(tenant, value);
            barrier.await(5, TimeUnit.SECONDS);
            if (tenant.equals(tenantA)) {
                try (var scope = TenantContextHolder.bind(tenant)) {
                    assertThat(cache.delete(namespace, "shared")).isTrue();
                }
            }
            barrier.await(5, TimeUnit.SECONDS);
            if (tenant.equals(tenantB)) {
                assertValue(tenant, value);
            }
            barrier.await(5, TimeUnit.SECONDS);
        }
        return TenantContextHolder.current().isEmpty();
    }

    private void store(TenantContext tenant, String value, Duration ttl) {
        // Register before attempting SET so ambiguous write failures still trigger exact-key cleanup.
        created.add(new OwnedKey(tenant, "shared"));
        try (var scope = TenantContextHolder.bind(tenant)) {
            cache.put(namespace, "shared", value, ttl);
        }
    }

    private void assertValue(TenantContext tenant, String expected) {
        try (var scope = TenantContextHolder.bind(tenant)) {
            assertThat(cache.get(namespace, "shared")).contains(expected);
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private record OwnedKey(TenantContext tenant, String logicalKey) {
    }
}
