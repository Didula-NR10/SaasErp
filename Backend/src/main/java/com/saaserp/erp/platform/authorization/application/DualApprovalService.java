package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class DualApprovalService {
    private final AuthorizationService authorization;
    private final DualApprovalRepository repository;
    private final SecurityAudit audit;

    public DualApprovalService(AuthorizationService authorization, DualApprovalRepository repository,
            SecurityAudit audit) {
        this.authorization = Objects.requireNonNull(authorization);
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    public UUID request(SessionGrant requester, OperationIntent intent, String idempotencyKey, UUID stepUpId) {
        authorization.requireCompany(requester, requester.companyId(), intent.permission());
        String detailsDigest = intent.digest();
        String idempotencyDigest = digestIdempotency(idempotencyKey);
        var existing = repository.findByIdempotency(requester, idempotencyDigest);
        if (existing.isPresent()) {
            if (!matches(existing.get(), intent, detailsDigest)) {
                denied(requester, existing.get().id());
                throw new IllegalStateException("Idempotency conflict");
            }
            return existing.get().id();
        }
        try {
            UUID id = repository.request(requester, intent, detailsDigest, idempotencyDigest, stepUpId);
            return id;
        } catch (IllegalStateException unavailable) {
            audit.record(new SecurityAuditEvent(requester.accountId(), requester.companyId(),
                Action.DUAL_APPROVAL_REQUESTED, Target.APPROVAL, null, Outcome.FAILED));
            throw unavailable;
        }
    }

    public boolean approve(SessionGrant approver, UUID approvalId, OperationIntent intent, UUID stepUpId) {
        authorization.requireCompany(approver, approver.companyId(), intent.permission());
        var approval = repository.find(approvalId).orElse(null);
        if (approval == null || !matches(approval, intent, intent.digest())
                || !approval.companyId().equals(approver.companyId())
                || !approval.hostname().equals(approver.hostname())
                || approval.requesterId().equals(approver.accountId())) {
            denied(approver, approvalId);
            throw new AuthorizationService.AuthorizationDenied();
        }
        if (!repository.approve(approver, approvalId, intent.digest(), stepUpId)) {
            denied(approver, approvalId);
            throw new AuthorizationService.AuthorizationDenied();
        }
        return true;
    }

    public void cancel(SessionGrant requester, UUID approvalId) {
        var approval = repository.find(approvalId).orElse(null);
        if (approval == null) {
            denied(requester, approvalId);
            throw new AuthorizationService.AuthorizationDenied();
        }
        authorization.requireCompany(requester, requester.companyId(),
            new com.saaserp.erp.platform.authorization.domain.Permission(approval.permission()));
        if (!approval.companyId().equals(requester.companyId()) || !approval.hostname().equals(requester.hostname())
                || !repository.cancel(requester, approvalId)) {
            denied(requester, approvalId);
            throw new AuthorizationService.AuthorizationDenied();
        }
    }

    public void expire(UUID approvalId) {
        repository.expire(approvalId);
    }

    public void consume(SessionGrant requester, UUID approvalId, OperationIntent intent) {
        authorization.requireCompany(requester, requester.companyId(), intent.permission());
        if (!repository.consume(requester, approvalId, intent, intent.digest())) {
            audit.record(new SecurityAuditEvent(requester.accountId(), requester.companyId(),
                Action.AUTHORIZATION_DENIED, Target.APPROVAL, approvalId.toString(), Outcome.DENIED));
            throw new AuthorizationService.AuthorizationDenied();
        }
    }

    private void denied(SessionGrant actor, UUID approvalId) {
        audit.record(new SecurityAuditEvent(actor.accountId(), actor.companyId(), Action.AUTHORIZATION_DENIED,
            Target.APPROVAL, approvalId.toString(), Outcome.DENIED));
    }

    private static boolean matches(DualApprovalRepository.Approval approval, OperationIntent intent, String digest) {
        return approval.permission().equals(intent.permission().value())
            && approval.targetType().equals(intent.target().name())
            && approval.targetId().equals(intent.targetId())
            && MessageDigest.isEqual(approval.detailsDigest().getBytes(StandardCharsets.US_ASCII),
                digest.getBytes(StandardCharsets.US_ASCII));
    }

    private static String digestIdempotency(String key) {
        if (key == null || key.length() < 16 || key.length() > 128
                || !key.matches("[A-Za-z0-9._~-]+")) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
