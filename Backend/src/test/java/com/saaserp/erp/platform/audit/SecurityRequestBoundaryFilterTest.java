package com.saaserp.erp.platform.audit;

import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import com.saaserp.erp.platform.audit.infrastructure.SecurityRequestBoundaryFilter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class SecurityRequestBoundaryFilterTest {
    private final SecurityRequestBoundaryFilter filter = new SecurityRequestBoundaryFilter();

    @AfterEach void clearCorrelation() {
        assertThat(CorrelationIdContext.current()).isNotNull();
    }

    @Test void preservesValidatedCorrelationAndAddsRestrictiveApiHeaders() throws Exception {
        UUID correlation = UUID.randomUUID();
        var request = jsonRequest("{}", correlation.toString());
        var response = new MockHttpServletResponse();
        var reached = new boolean[1];

        filter.doFilter(request, response, (wrapped, result) -> {
            reached[0] = true;
            assertThat(CorrelationIdContext.current()).isEqualTo(correlation);
            assertThat(wrapped.getInputStream().readAllBytes()).containsExactly("{}".getBytes(StandardCharsets.UTF_8));
        });

        assertThat(reached[0]).isTrue();
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(correlation.toString());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(CorrelationIdContext.current()).isNotEqualTo(correlation);
    }

    @Test void rejectsOversizedJsonBodiesBeforeDispatching() throws Exception {
        var request = jsonRequest("x".repeat(SecurityRequestBoundaryFilter.MAX_JSON_BODY_BYTES + 1), "invalid");
        var response = new MockHttpServletResponse();
        var reached = new boolean[1];

        filter.doFilter(request, response, (wrapped, result) -> reached[0] = true);

        assertThat(reached[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("request_too_large");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Correlation-ID")).isNotEqualTo("invalid");
    }

    @Test void rejectsAmbiguousContentLength() throws Exception {
        var request = jsonRequest("{}", null);
        request.addHeader("Content-Length", "2");
        request.addHeader("Content-Length", "3");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrapped, result) -> fail("Request must be rejected"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("invalid_request");
    }

    private static MockHttpServletRequest jsonRequest(String body, String correlation) {
        var request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (correlation != null) request.addHeader("X-Correlation-ID", correlation);
        return request;
    }
}
