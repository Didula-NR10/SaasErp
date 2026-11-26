package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/** For a future tenant persistence unit only; never install on the central unit. */
public final class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContextHolder.requireCurrent().tenantId().toString();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(String tenantIdentifier) {
        return false;
    }
}
