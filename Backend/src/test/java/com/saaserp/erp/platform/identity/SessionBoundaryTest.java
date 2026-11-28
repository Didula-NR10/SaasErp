package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.application.IdentityRepository;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.identity.infrastructure.*;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionBoundaryTest {
    private final IdentityRepository identities = mock(IdentityRepository.class);
    private final HostPolicy hosts = new HostPolicy("https://a.test,https://b.test");
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final AuthenticationThrottle throttle = mock(AuthenticationThrottle.class);
    @AfterEach void clean() { SecurityContextHolder.clearContext(); assertThat(TenantContextHolder.current()).isEmpty(); }
    @Test void rejectsHttpUnknownAmbiguousAndForgedHostOrOrigin() {
        for (String host : new String[] {"evil.test", "a.test:443", "A.test", "a.test.evil"}) {
            var request = request(); request.removeHeader("Host"); request.addHeader("Host", host);
            assertThatIllegalArgumentException().isThrownBy(() -> hosts.requireHost(request));
        }
        var insecure = request(); insecure.setSecure(false);
        assertThatIllegalArgumentException().isThrownBy(() -> hosts.requireHost(insecure));
        var duplicate = request(); duplicate.addHeader("Host", "b.test");
        assertThatIllegalArgumentException().isThrownBy(() -> hosts.requireHost(duplicate));
        var forwarded = request(); forwarded.removeHeader("Host"); forwarded.addHeader("Host", "evil.test"); forwarded.addHeader("X-Forwarded-Host", "a.test");
        assertThatIllegalArgumentException().isThrownBy(() -> hosts.requireHost(forwarded));
        for (String origin : new String[] {"https://b.test", "null", "https://a.test.evil", "http://a.test"}) {
            var wrong = request(); wrong.setMethod("POST"); wrong.addHeader("Origin", origin);
            assertThatIllegalArgumentException().isThrownBy(() -> hosts.requireHost(wrong));
        }
        var absent = request(); absent.setMethod("POST");
        assertThatIllegalArgumentException().isThrownBy(() -> hosts.requireHost(absent));
    }
    @Test void currentAuthorityBindsAndClosesTheTenantScopeEvenOnDownstreamFailure() throws Exception {
        var grant = grant("a.test", clock.instant());
        when(identities.isCurrent(grant)).thenReturn(true);
        authenticate(grant); var request = request(); request.getSession();
        var filter = filter();
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req,res) -> {
            assertThat(TenantContextHolder.requireCurrent().tenantId().toString()).isEqualTo(grant.companyId().toString());
            throw new IllegalStateException("downstream");
        })).isInstanceOf(IllegalStateException.class);
        verify(identities).isCurrent(grant);
    }
    @Test void crossCompanyStaleRevokedAndAbsolutelyExpiredSessionsNeverReachTheEndpoint() throws Exception {
        for (var grant : List.of(grant("b.test", clock.instant()), grant("a.test", clock.instant()),
                grant("a.test", clock.instant().minusSeconds(43200)))) {
            authenticate(grant); var request = request(); var session = request.getSession();
            var response = new MockHttpServletResponse();
            filter().doFilter(request, response, (req,res) -> { throw new AssertionError("Denied authority reached endpoint"); });
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(((MockHttpSession)session).isInvalid()).isTrue();
        }
    }
    @Test void requiredPostgresAndRedisFailuresReturnGenericUnavailableWithoutCallingEndpoint() throws Exception {
        for (String detail : List.of("PostgreSQL secret detail", "Redis secret detail")) {
            var response = new MockHttpServletResponse();
            new SecurityFailureBoundary().doFilter(request(), response, (req,res) -> { throw new DataAccessResourceFailureException(detail); });
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"security_unavailable\"}");
        }
    }
    @Test void postgresFailureWhileCheckingARealPrincipalCannotReachEndpoint() {
        var grant = grant("a.test", clock.instant()); authenticate(grant);
        when(identities.isCurrent(grant)).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var request = request(); request.getSession();
        assertThatThrownBy(() -> filter().doFilter(request, new MockHttpServletResponse(), (req,res) -> {
            throw new AssertionError("No fallback is allowed");
        })).isInstanceOf(DataAccessResourceFailureException.class);
    }
    private SessionAuthorityFilter filter() {
        return new SessionAuthorityFilter(identities, hosts, clock, throttle, mock(com.saaserp.erp.platform.audit.application.SecurityAudit.class));
    }
    private static void authenticate(SessionGrant grant) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(grant, null, List.of()));
    }
    private SessionGrant grant(String host, Instant issued) {
        return new SessionGrant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), host,1,1,1,issued,false);
    }
    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/session"); request.setSecure(true);
        request.addHeader("Host", "a.test"); return request;
    }
}
