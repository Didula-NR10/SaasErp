package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.authorization.domain.PlatformSessionContext;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import java.util.Objects;

public final class PlatformAuthorizationService {
    private static final String PLATFORM_HOST = "admin.hich.com";
    private final PlatformAuthorizationRepository repository;
    private final SecurityAudit audit;

    public PlatformAuthorizationService(PlatformAuthorizationRepository repository, SecurityAudit audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    public boolean permits(PlatformSessionContext session, Permission permission) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(permission);
        boolean permitted = PLATFORM_HOST.equals(session.hostname())
            && repository.permitsPlatform(session, permission);
        if (!permitted) {
            audit.record(new SecurityAuditEvent(session.accountId(), null, Action.AUTHORIZATION_DENIED,
                Target.PERMISSION, permission.value(), Outcome.DENIED));
        }
        return permitted;
    }

    public void require(PlatformSessionContext session, Permission permission) {
        if (!permits(session, permission)) {
            throw new PlatformAuthorizationDenied();
        }
    }

    public static final class PlatformAuthorizationDenied extends RuntimeException {
        public PlatformAuthorizationDenied() { super("Access denied"); }
    }
}
