package com.saaserp.erp.platform.identity.infrastructure;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityResponses {
    private SecurityResponses() { }
    public static void error(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        String code = switch (status) {
            case 401 -> "authentication_failed";
            case 403 -> "access_denied";
            case 429 -> "too_many_requests";
            case 413 -> "request_too_large";
            case 503 -> "security_unavailable";
            default -> "invalid_request";
        };
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
