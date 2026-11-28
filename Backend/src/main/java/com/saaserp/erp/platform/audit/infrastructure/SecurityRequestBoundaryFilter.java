package com.saaserp.erp.platform.audit.infrastructure;

import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SecurityRequestBoundaryFilter extends OncePerRequestFilter {
    public static final int MAX_JSON_BODY_BYTES = 128 * 1024;
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        UUID correlationId = correlationId(request);
        try (var ignored = CorrelationIdContext.bind(correlationId)) {
            if (isJsonMutation(request)) {
                var lengths = java.util.Collections.list(request.getHeaders("Content-Length"));
                if (lengths.size() > 1) {
                    error(response, 400);
                    return;
                }
                if (!lengths.isEmpty()) {
                    try {
                        if (Long.parseLong(lengths.getFirst()) > MAX_JSON_BODY_BYTES) {
                            error(response, 413);
                            return;
                        }
                    } catch (NumberFormatException invalid) {
                        error(response, 400);
                        return;
                    }
                }
                byte[] body = request.getInputStream().readNBytes(MAX_JSON_BODY_BYTES + 1);
                if (body.length > MAX_JSON_BODY_BYTES) {
                    error(response, 413);
                    return;
                }
                chain.doFilter(new BoundedBodyRequest(request, body), response);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            applyHeaders(request, response, correlationId);
        }
    }

    private static boolean isJsonMutation(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String type = request.getContentType();
        return path.startsWith("/api/v1/") && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
            && type != null && type.split(";", 2)[0].strip().equalsIgnoreCase("application/json");
    }

    private static UUID correlationId(HttpServletRequest request) {
        List<String> supplied = java.util.Collections.list(request.getHeaders(CORRELATION_HEADER));
        if (supplied.size() == 1) {
            try {
                UUID parsed = UUID.fromString(supplied.getFirst());
                if (parsed.toString().equals(supplied.getFirst())) return parsed;
            } catch (IllegalArgumentException ignored) { }
        }
        return UUID.randomUUID();
    }

    private static void applyHeaders(HttpServletRequest request, HttpServletResponse response, UUID correlationId) {
        response.setHeader(CORRELATION_HEADER, correlationId.toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        if (request.isSecure()) response.setHeader("Strict-Transport-Security", "max-age=31536000");
        if (request.getRequestURI().startsWith("/api/v1/")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Content-Security-Policy",
                "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; object-src 'none'");
        }
    }

    private static void error(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        String code = status == 413 ? "request_too_large" : "invalid_request";
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private BoundedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    if (listener == null) throw new IllegalArgumentException("Read listener is required");
                    try {
                        listener.onDataAvailable();
                        if (input.available() == 0) listener.onAllDataRead();
                    } catch (IOException failure) {
                        listener.onError(failure);
                    }
                }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
