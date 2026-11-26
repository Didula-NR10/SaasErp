package com.saaserp.erp.shared.cache.application;

import java.time.Duration;
import java.util.Optional;

/**
 * Tenant-scoped UTF-8 string storage. Every call requires the active trusted tenant context.
 * Infrastructure failures propagate; callers must deliberately choose any business-level fallback.
 * No persistent writes, arbitrary tenant selection, raw keys or bulk invalidation are supported.
 */
public interface TenantCache {

    Duration MIN_TTL = Duration.ofMillis(1);
    Duration MAX_TTL = Duration.ofHours(24);

    Optional<String> get(String namespace, String logicalKey);

    /** TTL must be between MIN_TTL and MAX_TTL inclusive, with whole-millisecond precision. */
    void put(String namespace, String logicalKey, String value, Duration ttl);

    /** Returns true only if this tenant's exact key was deleted; false means it was absent. */
    boolean delete(String namespace, String logicalKey);
}
