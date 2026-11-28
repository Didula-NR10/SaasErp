package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.identity.infrastructure.ApiRequestQuotaFilter;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class ApiRequestQuotaFilterTest {
    @AfterEach void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test void usesOnlyServerObservedAddressAndCompanyAccountForQuotaScopes() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        var grant = new SessionGrant(UUID.randomUUID(), accountId, companyId, "shop.example.test",
            1, 1, 1, Instant.now(), false, "WEBAUTHN");
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken(grant, null, "COMPANY_SESSION"));
        var request = request();
        request.setRemoteAddr("198.51.100.27");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        var response = new MockHttpServletResponse();
        var calls = new AtomicInteger();

        new ApiRequestQuotaFilter((address, scope) -> {
            assertThat(address).isEqualTo("198.51.100.27");
            assertThat(scope).isEqualTo(companyId + ":" + accountId);
            return true;
        }).doFilter(request, response, (req, res) -> calls.incrementAndGet());

        assertThat(calls).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test void limitsAndDependencyFailuresFailClosed() throws Exception {
        var limitedResponse = new MockHttpServletResponse();
        new ApiRequestQuotaFilter((address, scope) -> false).doFilter(request(), limitedResponse,
            (req, res) -> fail("limited API request must not reach the chain"));
        assertThat(limitedResponse.getStatus()).isEqualTo(429);
        assertThat(limitedResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limitedResponse.getContentAsString()).contains("too_many_requests");

        var unavailableResponse = new MockHttpServletResponse();
        new ApiRequestQuotaFilter((address, scope) -> {
            throw new IllegalStateException("quota store unavailable");
        }).doFilter(request(), unavailableResponse,
            (req, res) -> fail("unavailable quota store must not reach the chain"));
        assertThat(unavailableResponse.getStatus()).isEqualTo(503);
        assertThat(unavailableResponse.getContentAsString()).contains("security_unavailable");

        var storageResponse = new MockHttpServletResponse();
        new ApiRequestQuotaFilter((address, scope) -> {
            throw new DataAccessResourceFailureException("Redis unavailable");
        }).doFilter(request(), storageResponse,
            (req, res) -> fail("Redis failure must not reach the chain"));
        assertThat(storageResponse.getStatus()).isEqualTo(503);
    }

    @Test void leavesNonApiRoutesOutsideApplicationQuota() throws Exception {
        var request = new MockHttpServletRequest("GET", "/health");
        var response = new MockHttpServletResponse();
        var calls = new AtomicInteger();
        new ApiRequestQuotaFilter((address, scope) -> {
            fail("non-API request must not consume the API quota");
            return false;
        }).doFilter(request, response, (req, res) -> calls.incrementAndGet());
        assertThat(calls).hasValue(1);
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
