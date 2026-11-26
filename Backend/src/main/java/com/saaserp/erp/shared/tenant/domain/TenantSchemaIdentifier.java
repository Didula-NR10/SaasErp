package com.saaserp.erp.shared.tenant.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical tenant schema identifier derived from an internal company UUID.
 * This mapping is a stable compatibility contract, not proof of authorization
 * or schema existence. Raw schema strings cannot be used to construct values.
 */
public final class TenantSchemaIdentifier {

    private static final Pattern CANONICAL = Pattern.compile("\\Atenant_[0-9a-f]{32}\\z");
    private static final int IDENTIFIER_LENGTH = 39;
    private static final int POSTGRESQL_IDENTIFIER_BYTE_LIMIT = 63;

    private final String value;

    private TenantSchemaIdentifier(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "Tenant identity is required");
        String generated = "tenant_" + tenantId.value().toString().replace("-", "");
        // The ASCII-only pattern makes every accepted character exactly one UTF-8 byte.
        if (!CANONICAL.matcher(generated).matches()
                || generated.length() != IDENTIFIER_LENGTH
                || generated.getBytes(StandardCharsets.UTF_8).length > POSTGRESQL_IDENTIFIER_BYTE_LIMIT) {
            throw new IllegalArgumentException("Tenant schema identifier must be canonical and within the byte limit");
        }
        this.value = generated;
    }

    /**
     * Preserves all 128 UUID bits, including leading zeros, without locale dependence.
     * Callers must independently establish tenant authorization before database use.
     */
    public static TenantSchemaIdentifier from(TenantId tenantId) {
        return new TenantSchemaIdentifier(tenantId);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TenantSchemaIdentifier identifier && value.equals(identifier.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
