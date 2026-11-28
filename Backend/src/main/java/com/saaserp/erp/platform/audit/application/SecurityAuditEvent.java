package com.saaserp.erp.platform.audit.application;

import java.util.Objects;
import java.util.UUID;

public record SecurityAuditEvent(UUID actorId, UUID companyId, Action action, Target target,
        String targetId, Outcome outcome) {
    public enum Action {
        LOGIN_SUCCEEDED, LOGIN_DENIED, AUTHORIZATION_DENIED, ROLE_CHANGED, FACTOR_ADDED,
        FACTOR_REMOVED, FACTOR_RECOVERY_STARTED, RECOVERY_CODES_REPLACED, PASSWORD_CHANGED,
        PASSWORD_RESET, EMAIL_VERIFIED, MFA_ENROLLMENT_APPROVED, SESSION_REVOKED,
        SESSIONS_REVOKED, STEP_UP_CREATED, STEP_UP_SUCCEEDED, STEP_UP_DENIED,
        STEP_UP_CONSUMED, DUAL_APPROVAL_REQUESTED, DUAL_APPROVAL_GRANTED,
        DUAL_APPROVAL_CANCELLED, DUAL_APPROVAL_EXPIRED, DUAL_APPROVAL_CONSUMED
    }

    public enum Target { ACCOUNT, COMPANY, MEMBERSHIP, SESSION, PERMISSION, CREDENTIAL, STEP_UP, APPROVAL, RESOURCE }
    public enum Outcome { SUCCEEDED, DENIED, FAILED, CANCELLED, EXPIRED, CONSUMED }

    public SecurityAuditEvent {
        Objects.requireNonNull(action);
        Objects.requireNonNull(target);
        Objects.requireNonNull(outcome);
        if (targetId != null && (targetId.isBlank() || targetId.length() > 128
                || !targetId.matches("[A-Za-z0-9._:-]+"))) {
            throw new IllegalArgumentException("Invalid audit target");
        }
    }
}
