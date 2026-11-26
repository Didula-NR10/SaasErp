package com.saaserp.erp.shared.tenant.domain;

import java.util.Objects;
import java.util.UUID;

/** Internal company identity; neither an employee identity nor a database schema name. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "Tenant identity is required");
    }

    /** Accepts only the canonical lowercase, hyphenated UUID representation. */
    public static TenantId parse(String value) {
        Objects.requireNonNull(value, "Tenant identity is required");
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Tenant identity must be a canonical UUID");
        }
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
