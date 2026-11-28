package com.saaserp.erp.platform.authorization.infrastructure;

import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import com.saaserp.erp.platform.authorization.application.CompanyRoleAssignmentRepository;
import com.saaserp.erp.platform.authorization.domain.CompanyRole;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCompanyRoleAssignmentRepository implements CompanyRoleAssignmentRepository {
    private final JdbcTemplate jdbc;

    public JdbcCompanyRoleAssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public java.util.Optional<CompanyRole> currentRole(UUID companyId, UUID accountId) {
        return jdbc.query("""
            SELECT auth_role FROM platform.company_memberships
            WHERE company_id=? AND account_id=? AND active AND NOT privileged
              AND auth_role<>'PLATFORM_ADMIN'
            """, (rs, row) -> CompanyRole.valueOf(rs.getString(1)), companyId, accountId)
            .stream().findFirst();
    }

    @Override public boolean assign(SessionGrant requester, UUID targetAccountId, CompanyRole expectedRole,
            CompanyRole requestedRole, UUID approvalId, String detailsDigest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.assign_company_role(?,?,?,?,?,?,?,?,?,?)
            """, Boolean.class, requester.id(), requester.accountId(), requester.companyId(),
            requester.hostname(), targetAccountId, expectedRole.name(), requestedRole.name(),
            approvalId, detailsDigest, CorrelationIdContext.current()));
    }
}
