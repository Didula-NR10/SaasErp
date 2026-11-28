package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class AuthorizationService {
    private final AuthorizationRepository repository;
    private final SecurityAudit audit;

    public AuthorizationService(AuthorizationRepository repository, SecurityAudit audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    public boolean permitsCompany(SessionGrant grant, UUID requestedCompany, Permission permission) {
        Objects.requireNonNull(grant);
        Objects.requireNonNull(requestedCompany);
        Objects.requireNonNull(permission);
        boolean permitted = grant.companyId().equals(requestedCompany)
            && repository.permitsCompany(grant, requestedCompany, permission.value());
        if (!permitted) {
            audit.record(new SecurityAuditEvent(grant.accountId(), grant.companyId(), Action.AUTHORIZATION_DENIED,
                Target.PERMISSION, permission.value(), Outcome.DENIED));
        }
        return permitted;
    }

    public void requireCompany(SessionGrant grant, UUID requestedCompany, Permission permission) {
        if (!permitsCompany(grant, requestedCompany, permission)) {
            throw new AuthorizationDenied();
        }
    }

    public Set<String> readableFields(SessionGrant grant, UUID requestedCompany, String resource,
            Set<String> candidateFields) {
        Objects.requireNonNull(candidateFields);
        var permitted = candidateFields.stream()
            .filter(field -> permitsCompany(grant, requestedCompany,
                new Permission(resource + "." + field + ".read")))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return permitted;
    }

    public Map<String, ?> filterReadableFields(SessionGrant grant, UUID requestedCompany, String resource,
            Map<String, ?> values) {
        Objects.requireNonNull(values);
        var result = new java.util.LinkedHashMap<String, Object>();
        values.forEach((field, value) -> {
            if (permitsCompany(grant, requestedCompany,
                    new Permission(resource + "." + field + ".read"))) {
                result.put(field, value);
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    public static final class AuthorizationDenied extends RuntimeException {
        public AuthorizationDenied() { super("Access denied"); }
    }
}
