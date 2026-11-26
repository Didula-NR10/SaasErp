package com.saaserp.erp.shared.tenant.application;

import java.util.Objects;

import com.saaserp.erp.shared.tenant.domain.TenantId;

/** Current internal company identity. Its presence does not establish authorization. */
public record TenantContext(TenantId tenantId) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "Tenant identity is required");
    }
}
