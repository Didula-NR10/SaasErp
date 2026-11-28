package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.UUID;

public interface AuthorizationRepository {
    boolean permitsCompany(SessionGrant grant, UUID requestedCompany, String permission);
}
