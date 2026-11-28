package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.ApiRequestQuota;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ApiRequestQuotaFilter extends OncePerRequestFilter {
    private final ApiRequestQuota quota;

    public ApiRequestQuotaFilter(ApiRequestQuota quota) {
        this.quota = quota;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.equals("/api/v1") && !path.startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String accountScope = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SessionGrant grant
            ? grant.companyId() + ":" + grant.accountId() : null;
        final boolean allowed;
        try {
            allowed = quota.allow(request.getRemoteAddr(), accountScope);
        } catch (DataAccessException | IllegalStateException unavailable) {
            SecurityResponses.error(response, 503);
            return;
        }
        if (!allowed) {
            response.setHeader("Retry-After", "60");
            SecurityResponses.error(response, 429);
            return;
        }
        chain.doFilter(request, response);
    }
}
