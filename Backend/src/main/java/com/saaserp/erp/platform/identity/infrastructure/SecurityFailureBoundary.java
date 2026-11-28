package com.saaserp.erp.platform.identity.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs outside Spring Session so Redis load/save failures cannot turn into an authenticated fallback. */
public final class SecurityFailureBoundary extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try { chain.doFilter(request, response); }
        catch (DataAccessException | org.springframework.data.redis.serializer.SerializationException | org.springframework.transaction.TransactionException failure) {
            SecurityContextHolder.clearContext();
            if (!response.isCommitted()) { response.reset(); SecurityResponses.error(response, 503); }
            else throw new ServletException("Required security storage unavailable");
        }
    }
}
