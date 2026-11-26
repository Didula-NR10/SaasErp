package com.saaserp.erp.shared.cache.infrastructure.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.saaserp.erp.shared.cache.application.TenantCache;
import com.saaserp.erp.shared.cache.application.TenantCacheKey;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Infrastructure adapter, wired with a dedicated string template by RedisTenantCacheConfig. */
public final class RedisTenantCache implements TenantCache {

    private final StringRedisTemplate redis;

    public RedisTenantCache(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "Redis string template is required");
    }

    @Override
    public Optional<String> get(String namespace, String logicalKey) {
        String key = TenantCacheKey.current(namespace, logicalKey);
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public void put(String namespace, String logicalKey, String value, Duration ttl) {
        String key = TenantCacheKey.current(namespace, logicalKey);
        if (value == null) {
            throw new IllegalArgumentException("Tenant cache value is required");
        }
        if (ttl == null || ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0
                || ttl.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("Tenant cache TTL must be 1ms to 24h in whole milliseconds");
        }
        // A single SET with expiration: never write a persistent value followed by a separate EXPIRE.
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public boolean delete(String namespace, String logicalKey) {
        String key = TenantCacheKey.current(namespace, logicalKey);
        Boolean deleted = redis.delete(key);
        if (deleted == null) {
            throw new IllegalStateException("Redis deletion did not return a confirmed result");
        }
        return deleted;
    }
}
