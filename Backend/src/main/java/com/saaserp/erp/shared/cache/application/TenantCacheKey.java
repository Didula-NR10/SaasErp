package com.saaserp.erp.shared.cache.application;

import com.saaserp.erp.shared.tenant.application.TenantContextHolder;

/** Canonical tenant keys. Identity comes only from the active trusted execution context. */
public final class TenantCacheKey {

    public static final int MAX_NAMESPACE_LENGTH = 64;
    public static final int MAX_LOGICAL_KEY_LENGTH = 128;

    private TenantCacheKey() {
    }

    public static String current(String namespace, String logicalKey) {
        var tenant = TenantContextHolder.requireCurrent().tenantId();
        if (namespace == null || namespace.length() > MAX_NAMESPACE_LENGTH
                || !namespace.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid tenant cache namespace");
        }
        if (logicalKey == null || logicalKey.length() > MAX_LOGICAL_KEY_LENGTH
                || !logicalKey.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid tenant cache logical key");
        }
        return "hich:tenant:" + tenant + ":" + namespace + ":" + logicalKey;
    }
}
