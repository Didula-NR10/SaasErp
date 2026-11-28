package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.application.IdentityRepository;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Owner-run tests: real existing PostgreSQL/Redis, no container creation or administrator login. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");
    private static final String A = "a-" + RUN + ".test";
    private static final String B = "b-" + RUN + ".test";
    private static final String NS = "hich:test:auth:" + RUN;
    private static final String PASSWORD = "a long unique integration passphrase";
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("app.security.password-blocklist", AuthenticationIntegrationTest::blocklist);
        registry.add("app.security.origins", () -> "https://" + A + ",https://" + B);
        registry.add("app.security.session-namespace", () -> NS);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate runtime;
    @Autowired PasswordEncoder encoder;
    @Autowired IdentityRepository identities;
    @Autowired SessionRepository<? extends Session> sessions;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;
    @Value("${app.datasource.migration.url}") String url;
    @Value("${app.datasource.migration.username}") String user;
    @Value("${app.datasource.migration.password}") String password;
    private JdbcTemplate fixtures;
    private UUID account, companyA, companyB;
    private String email, source;
    private final Set<String> ownedLimiterIdentities = new HashSet<>();
    private final Set<String> ownedSessions = new HashSet<>();

    @BeforeEach void seedUsingRestrictedTestMigrator() {
        assertThat(runtime.queryForObject("SELECT current_database()", String.class)).isEqualTo("hich_erp_test");
        assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("hich_runtime_test");
        assertThat(user).isEqualTo("hich_migrator_test");
        fixtures = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        assertThat(fixtures.queryForObject("SELECT current_database()", String.class)).isEqualTo("hich_erp_test");
        account = UUID.randomUUID(); companyA = UUID.randomUUID(); companyB = UUID.randomUUID();
        email = account + "@example.test"; source = "source-" + account; ownedLimiterIdentities.add(email);
        fixtures.update("INSERT INTO platform.accounts(id, normalized_email, password_hash, status, email_verified) VALUES (?,?,?,'ACTIVE',true)", account,email,encoder.encode(PASSWORD));
        fixtures.update("INSERT INTO platform.auth_companies(id,hostname,status,entitled,mfa_required) VALUES (?,?,'ACTIVE',true,false),(?,?,'ACTIVE',true,false)",companyA,A,companyB,B);
        fixtures.update("INSERT INTO platform.company_memberships(account_id,company_id,active,mfa_required) VALUES (?,?,true,false),(?,?,true,false)",account,companyA,account,companyB);
    }
    @AfterEach void deleteOnlyOwnedRowsAndKeys() throws Exception {
        try {
            for (String id : ownedSessions) sessions.deleteById(id);
            if (account != null && fixtures != null) {
                fixtures.update("DELETE FROM platform.authentication_sessions WHERE account_id = ?", account);
                fixtures.update("DELETE FROM platform.company_memberships WHERE account_id = ?", account);
                fixtures.update("DELETE FROM platform.accounts WHERE id = ?", account);
                fixtures.update("DELETE FROM platform.accounts WHERE normalized_email = ?", "new-" + email);
                fixtures.update("DELETE FROM platform.auth_companies WHERE id IN (?,?)",companyA,companyB);
                for (String value : ownedLimiterIdentities) redis.delete(NS + ":limits:account:" + digest(value));
                redis.delete(NS + ":limits:source:" + digest(source));
            }
        } finally { assertThat(TenantContextHolder.current()).isEmpty(); }
    }
    @Test void loginRotatesSessionStoresNoPasswordAndSetsHostCookie() throws Exception {
        var csrf = csrf(A);
        var result = login(A, csrf, PASSWORD).andExpect(status().isNoContent()).andReturn();
        Cookie authenticated = cookie(result);
        assertThat(authenticated.getValue()).isNotEqualTo(csrf.cookie().getValue());
        String set = result.getResponse().getHeader("Set-Cookie");
        assertThat(set).contains("__Host-HICHSESSION=", "Secure", "HttpOnly", "Path=/", "SameSite=Strict").doesNotContain("Domain=");
        String id = decode(authenticated);
        Session stored = sessions.findById(id);
        assertThat(stored).isNotNull();
        var context = (SecurityContextImpl) stored.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(SessionGrant.class);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(authenticated)).andExpect(status().isOk())
            .andExpect(jsonPath("$.companyId").value(companyA.toString()));
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(csrf.cookie())).andExpect(status().isUnauthorized());
    }
    @Test void invalidPasswordReturnsGenericFailureAndNoAuthorizedSession() throws Exception {
        var csrf = csrf(A);
        UUID correlation=UUID.randomUUID();
        mvc.perform(form("/api/v1/auth/login",A).cookie(csrf.cookie()).header("X-CSRF-TOKEN",csrf.token())
            .header("X-Correlation-ID",correlation.toString()).param("email",email).param("password","incorrect"))
            .andExpect(status().isUnauthorized()).andExpect(content().json("{\"code\":\"authentication_failed\"}"))
            .andExpect(header().string("X-Correlation-ID",correlation.toString()));
        assertThat(fixtures.queryForObject("""
            SELECT count(*) FROM platform.security_audit_events
            WHERE correlation_id=? AND action='LOGIN_DENIED' AND outcome='DENIED'
            """,Long.class,correlation)).isEqualTo(1L);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(csrf.cookie())).andExpect(status().isUnauthorized());
    }
    @Test void apiResponsesSetSecurityHeadersAndAllowOnlyTheExactOrigin() throws Exception {
        UUID correlation=UUID.randomUUID();
        var result=mvc.perform(request(get("/api/v1/auth/csrf"),A)
            .header("Origin","https://"+A).header("X-Correlation-ID",correlation.toString()))
            .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("https://"+A);
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(result.getResponse().getHeader("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(result.getResponse().getHeader("X-Correlation-ID")).isEqualTo(correlation.toString());
    }
    @Test void csrfIsRequiredForLoginAndRegistration() throws Exception {
        mvc.perform(form("/api/v1/auth/login",A).param("email",email).param("password",PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(form("/api/v1/auth/register",A).param("email",email).param("password",PASSWORD)).andExpect(status().isForbidden());
    }
    @Test void requestDtosRejectUnknownPrivilegeFields() throws Exception {
        var csrf=csrf(A);
        mvc.perform(request(post("/api/v1/auth/account/email/request"),A).header("Origin","https://"+A).cookie(csrf.cookie())
            .header("X-CSRF-TOKEN",csrf.token()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"email\":\"unknown@example.test\",\"role\":\"OWNER\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void unauthorizedAndUnlistedEndpointsRemainDenied() throws Exception {
        mvc.perform(request(get("/api/v1/auth/session"),A)).andExpect(status().isUnauthorized());
        Cookie cookie = authenticated();
        mvc.perform(request(get("/api/v1/anything"),A).cookie(cookie)).andExpect(status().isForbidden());
    }
    @Test void cookieCannotBeManuallyReusedAtAnotherCompany() throws Exception {
        Cookie cookie = authenticated();
        mvc.perform(request(get("/api/v1/auth/session"),B).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void siblingOriginUnknownHostAndHttpAreRejected() throws Exception {
        mvc.perform(request(get("/api/v1/auth/csrf"),A).header("Origin","https://"+B)).andExpect(status().isForbidden());
        mvc.perform(request(get("/api/v1/auth/csrf"),"unknown.test")).andExpect(status().isForbidden());
        mvc.perform(request(get("/api/v1/auth/csrf"),A).secure(false)).andExpect(status().isForbidden());
    }
    @Test void committedRevocationRejectsEvenAValidRedisSession() throws Exception {
        Cookie cookie = authenticated();
        fixtures.update("UPDATE platform.authentication_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE account_id = ?",account);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void membershipRemovalAndReactivationCannotResurrectAnOldSession() throws Exception {
        Cookie cookie = authenticated();
        fixtures.update("UPDATE platform.company_memberships SET active = false WHERE account_id = ?",account);
        fixtures.update("UPDATE platform.company_memberships SET active = true WHERE account_id = ?",account);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void accountDisabledAndReenabledCannotResurrectAnOldSession() throws Exception {
        Cookie cookie = authenticated();
        fixtures.update("UPDATE platform.accounts SET status = 'DISABLED' WHERE id = ?",account);
        fixtures.update("UPDATE platform.accounts SET status = 'ACTIVE' WHERE id = ?",account);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void suspendedCompanyAndWithdrawnEntitlementDenyCurrentSessions() throws Exception {
        Cookie cookie = authenticated();
        fixtures.update("UPDATE platform.auth_companies SET entitled = false WHERE id = ?",companyA);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
        fixtures.update("UPDATE platform.auth_companies SET status = 'SUSPENDED' WHERE id = ?",companyA);
        login(A,csrf(A),PASSWORD).andExpect(status().isUnauthorized());
    }
    @Test void requiredMfaAtAnyPolicyLevelDeniesPasswordOnlyAccess() throws Exception {
        for (String table : List.of("accounts","company_memberships","auth_companies")) {
            String where = table.equals("company_memberships") ? "account_id" : "id";
            UUID id = table.equals("auth_companies") ? companyA : account;
            // Test-only table/column constants, never external identifiers.
            fixtures.update("UPDATE platform." + table + " SET mfa_required = true WHERE " + where + " = ?",id);
            login(A,csrf(A),PASSWORD).andExpect(status().isUnauthorized());
            fixtures.update("UPDATE platform." + table + " SET mfa_required = false WHERE " + where + " = ?",id);
        }
        Cookie cookie = authenticated();
        fixtures.update("UPDATE platform.company_memberships SET privileged = true WHERE account_id = ?",account);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void logoutCommitsRevocationAndDeletesTheRedisSession() throws Exception {
        Cookie cookie = authenticated(); String id = decode(cookie);
        var fresh = mvc.perform(request(get("/api/v1/auth/csrf"),A).cookie(cookie)).andReturn();
        String token = json.readTree(fresh.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(form("/api/v1/auth/logout",A).cookie(cookie).header("X-CSRF-TOKEN",token)).andExpect(status().isNoContent());
        assertThat(sessions.findById(id)).isNull();
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.authentication_sessions WHERE account_id = ? AND revoked_at IS NOT NULL",Long.class,account)).isEqualTo(1L);
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void realRedisIdleExpirationAndExplicitInvalidationAreRejected() throws Exception {
        Cookie cookie = authenticated(); String id = decode(cookie);
        Session stored = sessions.findById(id);
        stored.setLastAccessedTime(Instant.now().minusSeconds(1800));
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
        Cookie another = authenticated(); sessions.deleteById(decode(another));
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(another)).andExpect(status().isUnauthorized());
    }
    @Test void absoluteExpirationWinsEvenWhenRedisActivityIsFresh() throws Exception {
        Cookie cookie = authenticated(); Session stored = sessions.findById(decode(cookie));
        var context = (SecurityContextImpl) stored.getAttribute("SPRING_SECURITY_CONTEXT");
        var g = (SessionGrant)context.getAuthentication().getPrincipal();
        var expired = new SessionGrant(g.id(),g.accountId(),g.companyId(),g.hostname(),g.accountVersion(),g.membershipVersion(),g.companyVersion(),Instant.now().minusSeconds(43200),false);
        stored.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(expired,null,List.of(new SimpleGrantedAuthority("COMPANY_SESSION")))));
        mvc.perform(request(get("/api/v1/auth/session"),A).cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void runtimeCannotSelfActivateAccountsOrGrantMembership() {
        assertThatThrownBy(() -> runtime.update("UPDATE platform.accounts SET status = 'ACTIVE' WHERE id = ?",account))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.update("UPDATE platform.company_memberships SET active = true WHERE account_id = ?",account))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void normalizedUniqueConstraintPreventsDuplicateAccounts() {
        assertThat(identities.registerPending(UUID.randomUUID(),email,encoder.encode(PASSWORD))).isFalse();
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.accounts WHERE normalized_email = ?",Long.class,email)).isEqualTo(1L);
    }
    @Test void registrationIsPendingUnverifiedAndGenericOnDuplicate() throws Exception {
        String registered = "new-" + email; ownedLimiterIdentities.add(registered);
        var csrf = csrf(A);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(form("/api/v1/auth/register",A).cookie(csrf.cookie()).header("X-CSRF-TOKEN",csrf.token())
                .param("email",registered.toUpperCase(java.util.Locale.ROOT)).param("password",PASSWORD))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        }
        var row = runtime.queryForMap("SELECT status,email_verified,password_hash,id FROM platform.accounts WHERE normalized_email = ?",registered);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("email_verified")).isEqualTo(false);
        assertThat((String)row.get("password_hash")).startsWith("$argon2id$");
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.company_memberships WHERE account_id = ?",Long.class,row.get("id"))).isZero();
    }
    @Test void invalidAndCompromisedRegistrationIsRejected() throws Exception {
        var csrf = csrf(A);
        for (String supplied : List.of("short", "known compromised passphrase")) {
            mvc.perform(form("/api/v1/auth/register",A).cookie(csrf.cookie()).header("X-CSRF-TOKEN",csrf.token())
                .param("email",email).param("password",supplied)).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"code\":\"invalid_request\"}"));
        }
    }
    @Test void repeatedAuthenticationIsRateLimited() throws Exception {
        var csrf = csrf(A);
        for (int attempt=0; attempt<10; attempt++) login(A,csrf,"incorrect").andExpect(status().isUnauthorized());
        login(A,csrf,"incorrect").andExpect(status().isTooManyRequests());
    }
    private static String blocklist() {
        try {
            var path = java.nio.file.Files.createTempFile("hich-auth-test-blocklist-", ".txt");
            java.nio.file.Files.writeString(path,digest("known compromised passphrase"));
            path.toFile().deleteOnExit(); return path.toString();
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    @Test void concurrentRegistrationHasExactlyOneWinningInsert() throws Exception {
        String registered = "new-" + email;
        String hash = encoder.encode(PASSWORD);
        var ready = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { ready.await(); return identities.registerPending(UUID.randomUUID(),registered,hash); });
            var second = workers.submit(() -> { ready.await(); return identities.registerPending(UUID.randomUUID(),registered,hash); });
            ready.countDown();
            int winners = (first.get(10,java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                + (second.get(10,java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertThat(winners).isEqualTo(1);
        }
    }
    private Cookie authenticated() throws Exception { return cookie(login(A,csrf(A),PASSWORD).andExpect(status().isNoContent()).andReturn()); }
    private Csrf csrf(String host) throws Exception {
        var result = mvc.perform(request(get("/api/v1/auth/csrf"),host)).andExpect(status().isOk()).andReturn();
        return new Csrf(cookie(result),json.readTree(result.getResponse().getContentAsString()).get("token").asText());
    }
    private ResultActions login(String host, Csrf csrf, String suppliedPassword) throws Exception {
        return mvc.perform(form("/api/v1/auth/login",host).cookie(csrf.cookie()).header("X-CSRF-TOKEN",csrf.token())
            .param("email",email).param("password",suppliedPassword));
    }
    private MockHttpServletRequestBuilder form(String path, String host) {
        return request(post(path),host).header("Origin","https://"+host).contentType("application/x-www-form-urlencoded");
    }
    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder, String host) {
        return builder.secure(true).header("Host",host).with(req -> {
            req.setServerName(host); req.setRemoteAddr(source); req.setServletPath(req.getRequestURI()); return req;
        });
    }
    private Cookie cookie(MvcResult result) {
        String value = result.getResponse().getHeaders("Set-Cookie").stream().filter(s -> s.startsWith("__Host-HICHSESSION="))
            .findFirst().orElseThrow().split(";",2)[0].substring("__Host-HICHSESSION=".length());
        var cookie = new Cookie("__Host-HICHSESSION",value); ownedSessions.add(decode(cookie)); return cookie;
    }
    private static String decode(Cookie cookie) { return new String(Base64.getDecoder().decode(cookie.getValue()),StandardCharsets.UTF_8); }
    private record Csrf(Cookie cookie, String token) { }
}
