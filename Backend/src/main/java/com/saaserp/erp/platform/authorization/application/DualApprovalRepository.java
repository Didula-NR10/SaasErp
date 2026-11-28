package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DualApprovalRepository {
    enum Status { REQUESTED, APPROVED, CANCELLED, EXPIRED, CONSUMED }
    record Approval(UUID id, UUID companyId, String hostname, UUID requesterId, UUID approverId,
            String permission, String targetType, String targetId, String detailsDigest,
            Status status, Instant expiresAt) { }

    Optional<Approval> findByIdempotency(SessionGrant requester, String idempotencyDigest);
    Optional<Approval> find(UUID id);
    UUID request(SessionGrant requester, OperationIntent intent, String detailsDigest,
            String idempotencyDigest, UUID stepUpId);
    boolean approve(SessionGrant approver, UUID approvalId, String detailsDigest, UUID stepUpId);
    boolean cancel(SessionGrant requester, UUID approvalId);
    boolean expire(UUID approvalId);
    boolean consume(SessionGrant requester, UUID approvalId, OperationIntent intent, String detailsDigest);
}
