package com.saaserp.erp.config;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.AccountIdentity;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import com.saaserp.erp.platform.audit.infrastructure.SecurityRequestBoundaryFilter;
import com.saaserp.erp.platform.identity.infrastructure.*;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.session.FlushMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration(proxyBeanMethods = false)
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800,
    redisNamespace = "${app.security.session-namespace:hich:auth:sessions}", flushMode = FlushMode.IMMEDIATE)
public class AuthenticationConfig {
    @Bean IdentityRepository identityRepository(JdbcTemplate jdbc) { return new JdbcIdentityRepository(jdbc); }
    @Bean Clock securityClock() { return Clock.systemUTC(); }
    @Bean HostPolicy hostPolicy(@Value("${app.security.origins:}") String origins) { return new HostPolicy(origins); }
    @Bean PasswordEncoder passwordEncoder() {
        // Argon2id: 16-byte salt, 32-byte hash, 64 MiB, 3 iterations, 1 lane; calibrate before release.
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }
    @Bean PasswordPolicy passwordPolicy(@Value("${app.security.password-blocklist:}") String path) throws IOException {
        return new CompromisedPasswordPolicy(path);
    }
    @Bean ApiRequestQuota apiRequestQuota(StringRedisTemplate redis,
            @Value("${app.security.session-namespace:hich:auth:sessions}") String namespace) {
        return new RedisApiRequestQuota(new AuthenticationThrottle(redis, namespace + ":requests"));
    }
    @Bean RegistrationService registrationService(IdentityRepository identities, PasswordPolicy policy, PasswordEncoder encoder) {
        return new RegistrationService(identities, policy, encoder::encode);
    }
    @Bean AuthenticationManager authenticationManager(IdentityRepository identities, PasswordEncoder encoder) {
        return new ProviderManager(new CompanyAuthenticationProvider(passwordProvider(identities,encoder), identities));
    }
    static DaoAuthenticationProvider passwordProvider(IdentityRepository identities,PasswordEncoder encoder) {
        var dao = new DaoAuthenticationProvider(email -> {
            try {
                return new AccountUserDetails(identities.findAccount(new AccountIdentity(email).value())
                    .orElseThrow(() -> new UsernameNotFoundException("Authentication failed")));
            } catch (IllegalArgumentException invalid) { throw new UsernameNotFoundException("Authentication failed"); }
        });
        dao.setPasswordEncoder(encoder);
        dao.setUserDetailsPasswordService((user, hash) -> {
            var details = (AccountUserDetails) user;
            if (!identities.upgradePassword(details.account().id(), details.getPassword(), hash)) {
                throw new BadCredentialsException("Authentication failed");
            }
            return new AccountUserDetails(identities.findAccount(user.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Authentication failed")));
        });
        return dao;
    }
    @Bean CookieSerializer cookieSerializer() {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName("__Host-HICHSESSION");
        cookie.setCookiePath("/");
        cookie.setUseSecureCookie(true);
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Strict");
        // Intentionally never configure Domain, domain patterns, persistent cookies or remember-me.
        return cookie;
    }
    @Bean FilterRegistrationBean<SecurityFailureBoundary> securityFailureBoundary() {
        var filter = new FilterRegistrationBean<>(new SecurityFailureBoundary());
        filter.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
        return filter;
    }
    @Bean FilterRegistrationBean<SecurityRequestBoundaryFilter> securityRequestBoundary() {
        var filter = new FilterRegistrationBean<>(new SecurityRequestBoundaryFilter());
        filter.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 2);
        return filter;
    }
    @Bean SecurityFilterChain authenticationSecurity(HttpSecurity http, AuthenticationManager manager,
            IdentityRepository identities, HostPolicy hosts, Clock securityClock, StringRedisTemplate redis,
            AccountProtectionService protection, SecurityAudit audit, ApiRequestQuota apiRequestQuota,
            @Value("${app.security.session-namespace:hich:auth:sessions}") String namespace) throws Exception {
        var contexts = new HttpSessionSecurityContextRepository();
        var csrf = new HttpSessionCsrfTokenRepository();
        var login = new UsernamePasswordAuthenticationFilter(manager);
        login.setRequiresAuthenticationRequestMatcher(PathPatternRequestMatcher.withDefaults()
            .matcher(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login"));
        login.setUsernameParameter("email");
        login.setAuthenticationDetailsSource(hosts::requireHost);
        login.setSecurityContextRepository(contexts);
        login.setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf))));
        login.setAuthenticationSuccessHandler((request, response, authentication) -> {
            var grant = (SessionGrant) authentication.getPrincipal();
            try {
                audit.record(new SecurityAuditEvent(grant.accountId(), grant.companyId(), Action.LOGIN_SUCCEEDED,
                    Target.SESSION, grant.id().toString(), Outcome.SUCCEEDED));
            } catch (RuntimeException auditFailure) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                try {
                    identities.revoke(grant.id());
                } catch (RuntimeException revokeFailure) {
                    auditFailure.addSuppressed(revokeFailure);
                }
                SecurityResponses.error(response, 503);
                return;
            }
            request.getSession().setMaxInactiveInterval((int) grant.idleLimit().toSeconds());
            response.setStatus(204);
        });
        login.setAuthenticationFailureHandler((request, response, failure) -> {
            audit.record(new SecurityAuditEvent(null, null, Action.LOGIN_DENIED, Target.ACCOUNT, null,
                failure instanceof AuthenticationServiceException ? Outcome.FAILED : Outcome.DENIED));
            SecurityResponses.error(response, failure instanceof AuthenticationServiceException ? 503 : 401);
        });
        http.securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(c -> c.disable())
            .csrf(c -> c.csrfTokenRepository(csrf))
            .cors(c -> c.configurationSource(request -> {
                // Same-origin only; do not trust another configured company origin.
                var config = new CorsConfiguration();
                try { config.setAllowedOrigins(List.of("https://" + hosts.requireHost(request))); }
                catch (IllegalArgumentException invalid) { return config; }
                config.setAllowedMethods(List.of("GET", "POST"));
                config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
                config.setAllowCredentials(true);
                return config;
            }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/v1/auth/mfa/start",
                    "/api/v1/auth/mfa/webauthn/registration/options",
                    "/api/v1/auth/mfa/webauthn/registration/complete",
                    "/api/v1/auth/mfa/webauthn/authentication/options",
                    "/api/v1/auth/mfa/webauthn/authentication/complete",
                    "/api/v1/auth/mfa/totp/enroll",
                    "/api/v1/auth/mfa/totp/confirm",
                    "/api/v1/auth/mfa/totp/verify",
                    "/api/v1/auth/mfa/finish",
                    "/api/v1/auth/mfa/cancel",
                    "/api/v1/auth/mfa/recovery-codes",
                    "/api/v1/auth/mfa/recover",
                    "/api/v1/auth/mfa/factors/list",
                    "/api/v1/auth/mfa/factors/remove",
                    "/api/v1/auth/account/password/change",
                    "/api/v1/auth/account/sessions/revoke-all",
                    "/api/v1/auth/account/mfa/request-enrollment",
                    "/api/v1/auth/account/mfa/approve-enrollment",
                    "/api/v1/auth/account/email/request",
                    "/api/v1/auth/account/email/verify",
                    "/api/v1/auth/account/password/request-reset",
                    "/api/v1/auth/account/password/reset").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/auth/session").hasAuthority("COMPANY_SESSION")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, failure) -> SecurityResponses.error(response, 401))
                .accessDeniedHandler((request, response, failure) -> SecurityResponses.error(response, 403)))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'")))
            .logout(l -> l.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                    .matcher(org.springframework.http.HttpMethod.POST, "/api/v1/auth/logout"))
                .addLogoutHandler((request, response, authentication) -> {
                    var pending=request.getSession(false);
                    if(pending!=null && pending.getAttribute(ProtectionSessionState.TRANSACTION) instanceof java.util.UUID transaction
                            && pending.getAttribute(ProtectionSessionState.BINDING) instanceof String binding) {
                        protection.cancel(transaction,binding,request.getHeader("Host"));
                    }
                    if (authentication != null && authentication.getPrincipal() instanceof SessionGrant grant) {
                        // Commit PostgreSQL revocation before the framework invalidates Redis state or acknowledges logout.
                        identities.revoke(grant.id());
                        audit.record(new SecurityAuditEvent(grant.accountId(), grant.companyId(),
                            Action.SESSION_REVOKED, Target.SESSION, grant.id().toString(), Outcome.SUCCEEDED));
                    }
                })
                .addLogoutHandler(new CsrfLogoutHandler(csrf))
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .addFilterBefore(new SessionAuthorityFilter(identities, hosts, securityClock,
                new AuthenticationThrottle(redis, namespace), audit), CsrfFilter.class)
            .addFilterAfter(new ApiRequestQuotaFilter(apiRequestQuota), SessionAuthorityFilter.class)
            .addFilterAt(login, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
