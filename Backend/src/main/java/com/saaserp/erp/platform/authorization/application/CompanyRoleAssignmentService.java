package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.CompanyRole;
import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CompanyRoleAssignmentService {
    private static final Permission ROLE_MANAGEMENT = new Permission("company.roles.manage");

    private final AuthorizationService authorization;
    private final DualApprovalService dualApprovals;
    private final CompanyRoleAssignmentRepository repository;
    private final SecurityAudit audit;

    public CompanyRoleAssignmentService(AuthorizationService authorization, DualApprovalService dualApprovals,
            CompanyRoleAssignmentRepository repository, SecurityAudit audit) {
        this.authorization = Objects.requireNonNull(authorization);
        this.dualApprovals = Objects.requireNonNull(dualApprovals);
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    public UUID request(SessionGrant requester, UUID targetAccountId, CompanyRole requestedRole,
            String idempotencyKey, UUID stepUpId) {
        authorization.requireCompany(requester, requester.companyId(), ROLE_MANAGEMENT);
        OperationIntent intent = intent(requester, targetAccountId, requestedRole);
        return dualApprovals.request(requester, intent, idempotencyKey, stepUpId);
    }

    public boolean approve(SessionGrant approver, UUID approvalId, UUID targetAccountId,
            CompanyRole requestedRole, UUID stepUpId) {
        authorization.requireCompany(approver, approver.companyId(), ROLE_MANAGEMENT);
        if (approver.accountId().equals(targetAccountId)) {
            throw denied(approver, targetAccountId);
        }
        return dualApprovals.approve(approver, approvalId, intent(approver, targetAccountId, requestedRole), stepUpId);
    }

    public void assign(SessionGrant requester, UUID approvalId, UUID targetAccountId, CompanyRole requestedRole) {
        authorization.requireCompany(requester, requester.companyId(), ROLE_MANAGEMENT);
        OperationIntent intent = intent(requester, targetAccountId, requestedRole);
        CompanyRole expectedRole = CompanyRole.valueOf(intent.details().get("currentRole"));
        if (!repository.assign(requester, targetAccountId, expectedRole, requestedRole, approvalId, intent.digest())) {
            throw denied(requester, targetAccountId);
        }
    }

    private OperationIntent intent(SessionGrant actor, UUID targetAccountId, CompanyRole requestedRole) {
        Objects.requireNonNull(targetAccountId);
        Objects.requireNonNull(requestedRole);
        if (actor.accountId().equals(targetAccountId)) {
            throw denied(actor, targetAccountId);
        }
        CompanyRole currentRole = repository.currentRole(actor.companyId(), targetAccountId)
            .orElseThrow(() -> denied(actor, targetAccountId));
        if (currentRole == requestedRole) {
            throw denied(actor, targetAccountId);
        }
        return new OperationIntent(ROLE_MANAGEMENT, OperationIntent.Target.MEMBERSHIP, targetAccountId.toString(),
            Map.of("companyId", actor.companyId().toString(),
                "currentRole", currentRole.name(),
                "requestedRole", requestedRole.name()));
    }

    private AuthorizationService.AuthorizationDenied denied(SessionGrant actor, UUID targetAccountId) {
        audit.record(new SecurityAuditEvent(actor.accountId(), actor.companyId(),
            Action.AUTHORIZATION_DENIED, Target.MEMBERSHIP, targetAccountId.toString(), Outcome.DENIED));
        return new AuthorizationService.AuthorizationDenied();
    }
}
