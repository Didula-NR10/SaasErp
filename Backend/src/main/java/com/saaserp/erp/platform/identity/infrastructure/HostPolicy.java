package com.saaserp.erp.platform.identity.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/** Canonical external origins are operator configuration, never inferred from forwarded headers. */
public final class HostPolicy {
    private final Map<String, String> origins;
    public HostPolicy(String configuredOrigins) {
        origins = Arrays.stream(configuredOrigins.split(",")).filter(s -> !s.isBlank()).map(String::strip)
            .collect(Collectors.toUnmodifiableMap(HostPolicy::hostname, s -> s));
    }
    private static String hostname(String origin) {
        URI uri = URI.create(origin);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != -1
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.getRawPath().isEmpty() || !origin.equals("https://" + uri.getHost())
                || !uri.getHost().matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) {
            throw new IllegalArgumentException("Only canonical HTTPS origins without ports are allowed");
        }
        return uri.getHost();
    }
    public String requireHost(HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders("Host"));
        if (headers.size() != 1 || !origins.containsKey(headers.getFirst()) || !request.isSecure()) {
            throw new IllegalArgumentException("Invalid host");
        }
        // Forwarded headers have no authority. The connector itself must see TLS and canonical Host.
        String host = headers.getFirst();
        var origin = Collections.list(request.getHeaders("Origin"));
        boolean safe = request.getMethod().equals("GET") || request.getMethod().equals("HEAD")
            || request.getMethod().equals("OPTIONS");
        if (origin.size() > 1 || (!safe && origin.size() != 1)
                || (!origin.isEmpty() && !origins.get(host).equals(origin.getFirst()))) {
            throw new IllegalArgumentException("Invalid origin");
        }
        return host;
    }
}
