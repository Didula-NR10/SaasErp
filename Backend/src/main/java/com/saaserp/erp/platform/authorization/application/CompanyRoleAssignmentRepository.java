package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.CompanyRole;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRoleAssignmentRepository {
    Optional<CompanyRole> currentRole(UUID companyId, UUID accountId);

    boolean assign(SessionGrant requester, UUID targetAccountId, CompanyRole expectedRole,
            CompanyRole requestedRole, UUID approvalId, String detailsDigest);
}
