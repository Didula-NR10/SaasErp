package com.saaserp.erp.shared.cache.infrastructure.redis;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import com.saaserp.erp.shared.cache.application.TenantCache;
import com.saaserp.erp.shared.cache.application.TenantCacheKey;
import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RedisTenantCacheTest {

    private final TenantContext tenant = new TenantContext(new TenantId(UUID.randomUUID()));
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private TenantCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new RedisTenantCache(redis);
    }

    @Test
    void readDelegatesOnlyTheContextDerivedKey() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            String key = TenantCacheKey.current("catalog", "item");
            when(values.get(key)).thenReturn("value");
            assertThat(cache.get("catalog", "item")).contains("value");
            verify(values).get(key);
        }
    }

    @Test
    void absentValueIsAnEmptyOptional() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            assertThat(cache.get("catalog", "item")).isEmpty();
        }
    }

    @Test
    void emptyStringRemainsADistinctStoredValue() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            when(values.get(TenantCacheKey.current("catalog", "item"))).thenReturn("");
            assertThat(cache.get("catalog", "item")).contains("");
        }
    }

    @Test
    void writeDelegatesValueAndTtlInOneSetOperation() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            cache.put("catalog", "item", "value", Duration.ofSeconds(30));
            verify(values).set(TenantCacheKey.current("catalog", "item"), "value", Duration.ofSeconds(30));
            verifyNoMoreInteractions(values);
        }
    }

    @Test
    void inclusiveTtlBoundsAreAccepted() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            for (Duration ttl : new Duration[] {TenantCache.MIN_TTL, TenantCache.MAX_TTL}) {
                cache.put("catalog", "item", "value", ttl);
                verify(values).set(TenantCacheKey.current("catalog", "item"), "value", ttl);
            }
        }
    }

    @Test
    void invalidTtlsFailBeforeRedisAccess() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            for (Duration ttl : new Duration[] {null, Duration.ZERO, Duration.ofMillis(-1),
                    Duration.ofNanos(1), Duration.ofNanos(1_000_001), TenantCache.MAX_TTL.plusMillis(1),
                    Duration.ofSeconds(Long.MAX_VALUE)}) {
                assertThrows(IllegalArgumentException.class, () -> cache.put("catalog", "item", "value", ttl));
            }
            verifyNoInteractions(redis, values);
        }
    }

    @Test
    void nullValueFailsBeforeRedisAccess() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            assertThrows(IllegalArgumentException.class,
                    () -> cache.put("catalog", "item", null, Duration.ofSeconds(1)));
            verifyNoInteractions(redis, values);
        }
    }

    @Test
    void everyOperationRequiresContextBeforeRedisAccess() {
        assertThrows(IllegalStateException.class, () -> cache.get("catalog", "item"));
        assertThrows(IllegalStateException.class, () -> cache.put("catalog", "item", "value", Duration.ofSeconds(1)));
        assertThrows(IllegalStateException.class, () -> cache.delete("catalog", "item"));
        verifyNoInteractions(redis, values);
    }

    @Test
    void everyOperationRejectsRawKeyInjectionBeforeRedisAccess() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            assertThrows(IllegalArgumentException.class, () -> cache.get("catalog", "hich:tenant:other"));
            assertThrows(IllegalArgumentException.class,
                    () -> cache.put("catalog:*", "item", "value", Duration.ofSeconds(1)));
            assertThrows(IllegalArgumentException.class, () -> cache.delete("catalog", "*"));
            verifyNoInteractions(redis, values);
        }
    }

    @Test
    void deletionTargetsOnlyTheActiveTenantEvenWithIdenticalComponents() {
        String firstKey;
        try (var scope = TenantContextHolder.bind(tenant)) {
            firstKey = TenantCacheKey.current("catalog", "item");
            when(redis.delete(firstKey)).thenReturn(true);
            assertThat(cache.delete("catalog", "item")).isTrue();
        }
        try (var scope = TenantContextHolder.bind(new TenantContext(new TenantId(UUID.randomUUID())))) {
            String secondKey = TenantCacheKey.current("catalog", "item");
            assertThat(secondKey).isNotEqualTo(firstKey);
            when(redis.delete(secondKey)).thenReturn(true);
            assertThat(cache.delete("catalog", "item")).isTrue();
            verify(redis).delete(firstKey);
            verify(redis).delete(secondKey);
            verifyNoMoreInteractions(redis);
        }
    }

    @Test
    void deletingAnAbsentKeyReturnsFalse() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            when(redis.delete(TenantCacheKey.current("catalog", "item"))).thenReturn(false);
            assertThat(cache.delete("catalog", "item")).isFalse();
        }
    }

    @Test
    void unavailableRedisReadPropagatesWithoutAnotherLookup() {
        var failure = new DataAccessResourceFailureException("Redis unavailable");
        try (var scope = TenantContextHolder.bind(tenant)) {
            String key = TenantCacheKey.current("catalog", "item");
            when(values.get(key)).thenThrow(failure);
            assertThat(assertThrows(DataAccessResourceFailureException.class,
                    () -> cache.get("catalog", "item"))).isSameAs(failure);
            verify(values).get(key);
            verifyNoMoreInteractions(values);
        }
    }

    @Test
    void timeoutPropagatesInsteadOfBecomingACacheMiss() {
        var failure = new QueryTimeoutException("Redis timeout");
        try (var scope = TenantContextHolder.bind(tenant)) {
            String key = TenantCacheKey.current("catalog", "item");
            when(values.get(key)).thenThrow(failure);
            assertThat(assertThrows(QueryTimeoutException.class,
                    () -> cache.get("catalog", "item"))).isSameAs(failure);
            verify(values).get(key);
            verifyNoMoreInteractions(values);
        }
    }

    @Test
    void failedWriteIsNotReportedAsSuccessOrRetried() {
        var failure = new DataAccessResourceFailureException("Redis write failed");
        try (var scope = TenantContextHolder.bind(tenant)) {
            String key = TenantCacheKey.current("catalog", "item");
            doThrow(failure).when(values).set(key, "value", Duration.ofSeconds(1));
            assertThat(assertThrows(DataAccessResourceFailureException.class,
                    () -> cache.put("catalog", "item", "value", Duration.ofSeconds(1)))).isSameAs(failure);
            verify(values).set(key, "value", Duration.ofSeconds(1));
            verifyNoMoreInteractions(values);
        }
    }

    @Test
    void failedDeleteIsNotReportedAsAbsentOrRetried() {
        var failure = new DataAccessResourceFailureException("Redis delete failed");
        try (var scope = TenantContextHolder.bind(tenant)) {
            String key = TenantCacheKey.current("catalog", "item");
            when(redis.delete(key)).thenThrow(failure);
            assertThat(assertThrows(DataAccessResourceFailureException.class,
                    () -> cache.delete("catalog", "item"))).isSameAs(failure);
            verify(redis).delete(key);
            verifyNoMoreInteractions(redis);
        }
    }

    @Test
    void unconfirmedDeletionFailsClosed() {
        try (var scope = TenantContextHolder.bind(tenant)) {
            when(redis.delete(TenantCacheKey.current("catalog", "item"))).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> cache.delete("catalog", "item"));
        }
    }

    @Test
    void applicationBoundaryExposesOnlyScopedStringOperations() {
        var methods = Arrays.stream(TenantCache.class.getDeclaredMethods()).toList();
        assertThat(methods).extracting(method -> method.getName()).containsExactlyInAnyOrder("get", "put", "delete");
        for (var method : methods) {
            Class<?>[] parameters = method.getParameterTypes();
            assertThat(parameters[0]).isEqualTo(String.class);
            assertThat(parameters[1]).isEqualTo(String.class);
            assertThat(parameters).allMatch(type -> type == String.class || type == Duration.class);
        }
    }
}
