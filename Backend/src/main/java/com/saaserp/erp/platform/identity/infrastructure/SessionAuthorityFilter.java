package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.IdentityRepository;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Mediate every restored session before logout, authentication or endpoint authorization. */
public final class SessionAuthorityFilter extends OncePerRequestFilter {
    private final IdentityRepository identities;
    private final HostPolicy hosts;
    private final Clock clock;
    private final AuthenticationThrottle throttle;
    private final SecurityAudit audit;
    public SessionAuthorityFilter(IdentityRepository identities, HostPolicy hosts, Clock clock,
            AuthenticationThrottle throttle, SecurityAudit audit) {
        this.identities = identities; this.hosts = hosts; this.clock = clock; this.throttle = throttle;
        this.audit = audit;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        final String hostname;
        try { hostname = hosts.requireHost(request); }
        catch (IllegalArgumentException invalid) { SecurityResponses.error(response, 403); return; }
        String path = request.getServletPath();
        if (request.getMethod().equals("POST") && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/mfa/start"))) {
            // Credentials are accepted only in a bounded form body, never URLs or ambiguous parameters.
            if (request.getQueryString() != null || request.getContentType() == null
                    || !request.getContentType().split(";", 2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")) {
                SecurityResponses.error(response, 400); return;
            }
            String[] emails = request.getParameterValues("email");
            String[] passwords = request.getParameterValues("password");
            if (emails == null || emails.length != 1 || emails[0].length() > 254
                    || passwords == null || passwords.length != 1 || passwords[0].isEmpty() || passwords[0].length() > 128) {
                SecurityResponses.error(response, 400); return;
            }
            String email = emails[0];
            if (!throttle.allow(request.getRemoteAddr(), email.strip().toLowerCase(java.util.Locale.ROOT))) {
                SecurityResponses.error(response, 429); return;
            }
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            var session = request.getSession(false);
            if (!(auth.getPrincipal() instanceof SessionGrant grant) || session == null
                    || !grant.hostname().equals(hostname)
                    || grant.expired(clock.instant(), Instant.ofEpochMilli(session.getLastAccessedTime()))
                    || !identities.isCurrent(grant)) {
                SecurityContextHolder.clearContext();
                if (auth.getPrincipal() instanceof SessionGrant invalid) {
                    identities.revoke(invalid.id());
                    audit.record(new SecurityAuditEvent(invalid.accountId(), invalid.companyId(),
                        Action.SESSION_REVOKED, Target.SESSION, invalid.id().toString(), Outcome.SUCCEEDED));
                }
                if (session != null) session.invalidate();
                SecurityResponses.error(response, 401); return;
            }
            session.setMaxInactiveInterval((int) grant.idleLimit().toSeconds());
            try (var scope = TenantContextHolder.bind(new TenantContext(TenantId.parse(grant.companyId().toString())))) {
                chain.doFilter(request, response);
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
