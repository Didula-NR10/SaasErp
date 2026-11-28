package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.identity.application.ProtectionFailure;
import com.saaserp.erp.platform.identity.application.ProtectionRepository;
import com.saaserp.erp.platform.identity.application.WebAuthnCeremonies;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.Objects;
import java.util.UUID;

public final class StepUpAuthorizationService {
    public record Challenge(UUID id, String publicKey) { }

    private final AuthorizationService authorization;
    private final StepUpRepository repository;
    private final ProtectionRepository protection;
    private final WebAuthnCeremonies webAuthn;
    private final SecurityAudit audit;

    public StepUpAuthorizationService(AuthorizationService authorization, StepUpRepository repository,
            ProtectionRepository protection, WebAuthnCeremonies webAuthn, SecurityAudit audit) {
        this.authorization = Objects.requireNonNull(authorization);
        this.repository = Objects.requireNonNull(repository);
        this.protection = Objects.requireNonNull(protection);
        this.webAuthn = Objects.requireNonNull(webAuthn);
        this.audit = Objects.requireNonNull(audit);
    }

    public Challenge begin(SessionGrant grant, OperationIntent intent) {
        authorization.requireCompany(grant, grant.companyId(), intent.permission());
        var state = protection.currentState(grant);
        if (!state.policy().accepts("WEBAUTHN") || protection.credentials(state).isEmpty()) {
            throw new ProtectionFailure();
        }
        String options = webAuthn.options(state, false);
        UUID id = repository.create(grant, intent, intent.digest(), options);
        return new Challenge(id, options);
    }

    public void verify(SessionGrant grant, UUID authorizationId, String credential) {
        String options = repository.burnChallenge(grant, authorizationId);
        if (options == null || options.isEmpty()) {
            audit.record(new SecurityAuditEvent(grant.accountId(), grant.companyId(), Action.STEP_UP_DENIED,
                Target.STEP_UP, authorizationId.toString(), Outcome.DENIED));
            throw new ProtectionFailure();
        }
        try {
            var state = protection.currentState(grant);
            if (!state.policy().accepts("WEBAUTHN")) throw new ProtectionFailure();
            webAuthn.authenticate(state, options, credential);
            if (!repository.complete(grant, authorizationId)) throw new ProtectionFailure();
        } catch (ProtectionFailure denied) {
            audit.record(new SecurityAuditEvent(grant.accountId(), grant.companyId(), Action.STEP_UP_DENIED,
                Target.STEP_UP, authorizationId.toString(), Outcome.DENIED));
            throw denied;
        }
    }

    public void consume(SessionGrant grant, UUID authorizationId, OperationIntent intent) {
        authorization.requireCompany(grant, grant.companyId(), intent.permission());
        if (!repository.consume(grant, authorizationId, intent, intent.digest())) {
            audit.record(new SecurityAuditEvent(grant.accountId(), grant.companyId(), Action.STEP_UP_DENIED,
                Target.STEP_UP, authorizationId.toString(), Outcome.DENIED));
            throw new ProtectionFailure();
        }
    }
}
