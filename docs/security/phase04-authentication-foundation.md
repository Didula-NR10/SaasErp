# Phase 04 authentication foundation — owner handoff

> Stage 02 historical handoff. The owner subsequently reported all 280 foundation tests passing. Current Stage 03 behavior, endpoints, dependencies and the consolidated owner procedure are in [MFA and account protection](phase04-stage03-mfa-account-protection.md). Historical statements below about disabled MFA/unverified foundation describe the earlier batch, not the current Stage 03 source.

2026-11-27. Implemented source batch on existing `feat/authentication-authorization` branch. **Not compiled or tested; not production-ready.** No Maven command, dependency download, database/Redis operation, Docker change, commit or push was executed. Static source review and cached public-API inspection do not demonstrate runtime correctness. The Spring Session data-redis artifact is not locally cached and must be resolved by the owner.

## Delivered behavior and architectural decisions

Native Spring Security verifies Argon2id passwords and integrates AuthenticationManager, SecurityContext, session rotation, CSRF and logout with Spring Session Redis. Platform JDBC ports use the existing restricted runtime datasource and fully qualified tables; no JPA entity is exposed or coupled to domain models. PostgreSQL records account identity, status/security version, company hostname/status/entitlement, membership policy/version and revocable session grants. Each request rechecks the writer and binds/closes the existing tenant scope only after authority succeeds. No tenant resource is provisioned. Shared tenant connection reset, Redis cache contracts, frontend and existing tests remain unchanged.

Ordinary limits are 30 minutes idle/12 hours absolute. Privileged policy retains 15 minutes/8 hours and five-minute transaction step-up freshness; privileged and mandatory-MFA authentication currently deny. All company-policy levels are checked at issuance and every subsequent request. An existing authenticated session cannot switch hostname, company, membership or assurance by supplying client fields. Unknown endpoints deny by default.

Cookies use `__Host-HICHSESSION`, Secure, HttpOnly, Path=/, SameSite=Strict, no Domain. There is no HTTP development bypass. Exact HTTPS company origins and company-specific WebAuthn RP IDs are fixed in [ADR-0004](../adr/0004-authentication-and-session-architecture.md). Native WebAuthn/TOTP flows remain disabled pending their next stages. Credentials are never stored in browser localStorage/sessionStorage or URLs. No browser JWT, DPoP, refresh tokens, authorization server or popup protocol is introduced.

A valid stolen cookie **may replay** at its issuing company host. HttpOnly, TLS, rotation, deadlines, revocation and future step-up mitigate but do not eliminate this accepted risk. No equivalent browser proof-of-possession protection is claimed. Origin/CSRF defenses cannot distinguish a legitimate browser from an attacker with its stolen cookie and CSRF token.

## Exact files changed by this batch

Security documents already had uncommitted owner edits; the requested architecture revision updates those working copies. Existing `.github/workflows/security-ci.yml`, `docs/security/security-principles.md` and `.vscode/` changes were not edited by this batch. No index/staging operation was performed.

- `Backend/pom.xml`
- `Backend/src/main/java/com/saaserp/erp/config/AuthenticationConfig.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/api/AuthenticationController.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/api/AuthenticationErrors.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/IdentityRepository.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/PasswordPolicy.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/RegistrationService.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/domain/Account.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/domain/AccountIdentity.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/domain/SessionGrant.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/AccountUserDetails.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/AuthenticationThrottle.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/CompanyAuthenticationProvider.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/CompromisedPasswordPolicy.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/HostPolicy.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/JdbcIdentityRepository.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SecurityFailureBoundary.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SecurityResponses.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SessionAuthorityFilter.java`
- `Backend/src/main/resources/application.properties`
- `Backend/src/main/resources/db/migration/platform/V2__authentication_foundation.sql`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationStorageFailureTest.java`
- `Backend/src/test/java/com/saaserp/erp/config/CompanyAuthenticationProviderTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/IdentityPolicyTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/SessionBoundaryTest.java`
- `docs/adr/0004-authentication-and-session-architecture.md`
- `docs/architecture/overview.md`
- `docs/security/attack-surface-and-trust-boundaries.md`
- `docs/security/authentication-technology-qualification.md`
- `docs/security/phase04-authentication-foundation.md`
- `docs/security/security-acceptance-criteria.md`
- `docs/security/security-requirements-traceability-matrix.md`
- `docs/security/security-requirements.md`
- `docs/security/threat-model.md`

## Dependency changes

| Change | Reason and evidence limit |
| --- | --- |
| Add `org.springframework.session:spring-session-data-redis` | Native Redis HttpSession integration; version 4.2.0 is managed by the unchanged Spring Boot BOM. The artifact is not cached; owner resolution/compatibility verification remains required. |
| Add `org.bouncycastle:bcprov-jdk18on:1.86` | Native Spring Argon2PasswordEncoder requires the Argon2 implementation. Explicit version matches a local cached artifact; presence is not vulnerability clearance. Owner reviews dependency/security results. |
| Remove `spring-boot-starter-security-oauth2-resource-server` and its test starter | Superseded browser JWT/resource-server architecture; native security and security-test starters remain. Existing Java 21, Boot 4.2.0 and all unrelated dependencies remain. |

Supported public Security APIs were inspected from local 7.2.0 jars. Spring Session behavior follows the official [Redis configuration](https://docs.spring.io/spring-session/reference/configuration/redis.html) and [cookie customization](https://docs.spring.io/spring-session/reference/configuration/common.html) contracts. No actual new-dependency build is claimed.

## Migration and operator configuration

Only new `platform/V2__authentication_foundation.sql` is introduced. Existing V1 migrations, independent platform/billing Flyway histories and initialization ordering are unchanged. V2 creates `platform.accounts`, `auth_companies`, `company_memberships`, `authentication_sessions`, version-fencing triggers and an account-session index. It does not create schemas, roles, tenant resources or billing records.

The existing restricted platform migrator owns the new tables/functions and grants `hich_runtime` SELECT; account INSERT only for ID/email/hash; account UPDATE only for password hash; session INSERT only for grant snapshots; session UPDATE only for revocation. No runtime permission activates/verifies accounts, grants/changes membership, grants company entitlement, changes policy/version, deletes records, modifies history or creates schema objects. No administrator credential enters runtime code. Existing runtime group inheritance supplies dev/test privileges. Review these grants with the existing role scripts; do not broaden defaults or rerun old migrations.

Version triggers fence account/company/membership policy changes, including deactivate/reactivate. Preserve membership rows and revoke/deactivate them rather than deleting referenced audit history. Future password reset/change must increment the account security version in the same transaction; parameter-only rehash does not invalidate sessions. Activation/email verification/invitation/billing services will need explicit narrowly scoped write contracts and a reviewed future privilege migration. No production account should be activated manually as a substitute for those workflows.

| Configuration | Required action |
| --- | --- |
| Existing `DB_*`, `TEST_DB_*`, `REDIS_*` | Retain restricted environment credentials. Point authority reads at the PostgreSQL writer, never a stale replica. Never print environment secrets. |
| `AUTH_ALLOWED_ORIGINS` | Comma-separated exact canonical HTTPS origins, e.g. `https://abc.hich.com,https://royal.hich.com`. No wildcard, path, port, parent-domain cookie or customer-content origin. Empty configuration denies host access. Login also requires matching unique PostgreSQL hostname/company authority. |
| `AUTH_PASSWORD_BLOCKLIST` | Readable deployment file of lowercase 64-hex SHA-256 digests, one compromised full password per line. Nonempty, reviewed, current corpus; no raw passwords. Empty setting disables registration by failing closed; malformed configured data fails startup. Test-only corpus is generated in a temporary file by integration tests. |
| `AUTH_SESSION_NAMESPACE` | Environment-unique protected authentication root, default `hich:auth:sessions`; Spring Session keys and `:limits:` keys must not overlap tenant cache scopes. Use a fresh namespace when recovering lost security state. Tests override with a random test-only namespace. |
| HTTPS connector/proxy | The connector must see TLS and the canonical unmodified Host. Forwarded headers are ignored. Review end-to-end TLS/connector certificates and strip spoofable forwarding metadata; a different trusted-proxy arrangement needs its own reviewed adapter. Do not weaken Host/Origin/CSRF checks for local HTTP. |
| Redis | Review private transport/TLS, ACL key/command scope, credential separation from cache-only users, memory/non-eviction policy and session-data access. Current wiring reuses the existing RedisConnectionFactory; dedicated security/cache credential and transport separation remains an operator/integration release gate. Redis Java session serialization is server-trusted data and must not accept untrusted writes. No Redis configuration, ACL or data was changed here. |

Registration creates PENDING/unverified accounts and never grants membership/session. Existing production users cannot complete activation, email verification or mandatory MFA through this batch alone.

## Endpoint contracts

All endpoints are under the exact company HTTPS origin. State-changing requests require its exact `Origin` plus a current native CSRF token. Credential forms must have one `email` and one `password`, no URL query credentials; body/parameter limits apply. All responses must be treated as non-cacheable. Cookie credentials stay in the browser's native cookie jar. Frontend integration is still future work.

| Method/path | Input | Success | Rejection |
| --- | --- | --- | --- |
| GET `/api/v1/auth/csrf` | No credential required; existing host session cookie if any. | 200 JSON `{ "headerName": "X-CSRF-TOKEN", "token": "..." }`; native synchronizer state and host cookie created as needed. Keep token in memory. | Wrong Host/Origin/transport: 403. Required Redis failure: 503. |
| POST `/api/v1/auth/register` | `application/x-www-form-urlencoded`: `email`, `password`; exact Origin, cookie and CSRF header. | 202 empty for valid new or duplicate normalized identities; new account remains PENDING/unverified. | Invalid/compromised input: 400; CSRF/Origin: 403; budget: 429; required screening/storage unavailable: 503. |
| POST `/api/v1/auth/login` | Same form/Origin/CSRF contract; 1–128 password characters on verification, 15–128 on registration. | 204 empty, rotated `__Host-HICHSESSION`, credential-free server SecurityContext. Fetch a new CSRF token after successful login. | Generic 401 for wrong credentials/status/membership/company policy/MFA; malformed contract: 400; CSRF/Origin: 403; budget: 429; required storage/provider service: 503. |
| GET `/api/v1/auth/session` | Current cookie at issuing company host. | 200 JSON `accountId`, `companyId`, `hostname`, `assurance: "PASSWORD"` after writer checks. | Missing/expired/revoked/wrong-company session: 401; Host/Origin: 403; unavailable authority: 503. |
| POST `/api/v1/auth/logout` | Current cookie and fresh CSRF/exact Origin. | 204 only after durable grant revocation then native context/session/CSRF invalidation. Anonymous logout with valid CSRF is idempotent. | CSRF/Origin: 403; current invalid authority: 401; required revocation/storage failure: 503, no false success. |

Generic JSON security errors use `code`: `invalid_request`, `authentication_failed`, `access_denied`, `too_many_requests`, `security_unavailable`. Framework CORS rejects invalid cross-origin traffic before controllers; its 403 response is framework-generated. Internal exceptions, password hashes and credential values are not returned. Do not infer login outcome details from generic 401. All unlisted endpoints are denied (anonymous 401/authenticated 403 as appropriate).

## Test definitions and current results

Five new classes define 38 test methods; none was executed:

| Class | Coverage |
| --- | --- |
| `IdentityPolicyTest` | Normalized identities, invalid/compromised input, duplicate-safe registration contract, missing screening, exact ordinary/privileged idle/absolute deadlines. |
| `CompanyAuthenticationProviderTest` | Native salted Argon2id verification and parameter upgrade, generic invalid credentials, disabled/pending/unverified/MFA/privileged denial, missing company authority, credential-free company principal. |
| `SessionBoundaryTest` | Host/Origin/HTTPS/forwarded-host rejection, cross-company/stale/absolute-expired sessions, current PostgreSQL checks and tenant-scope cleanup, fail-closed storage error boundary. |
| `AuthenticationStorageFailureTest` | Redis limiter/load/save and PostgreSQL revocation failures with generic 503 and no success acknowledgement; injected adapter exceptions, not actual infrastructure outages. |
| `AuthenticationIntegrationTest` | Real PostgreSQL/Redis with MockMvc: login/fixation/cookie flags, CSRF, invalid/unauthorized requests, wrong company/Origin/Host, durable revocation and deactivate/reactivate fencing, company entitlement/suspension, all MFA policy levels, logout, Redis idle expiration/deletion, absolute age, runtime grant denials, registration validation/uniqueness and concurrent inserts, account throttling. |

Integration tests verify `hich_erp_test` and `hich_runtime_test` before fixture writes. They use only `hich_migrator_test` for owned test rows and exact cleanup; no administrator login, database recreation, flush, scan, container or volume operations occur. Redis cleanup uses only tracked sessions and exact limiter keys. Existing 163-test source baseline remains intact; total test methods and successful execution must be established by owner Maven reports rather than inferred from this handoff. No failing test was removed.

## One consolidated owner verification procedure

1. Review the complete working tree, including preexisting owner edits separately. Confirm all original migrations, existing tests, frontend and CI protections remain intact. Review the accepted ADR and residual cookie replay risk, future MFA/authorization gates, and the exact V2 table grants.
2. In the existing Dev Container, configure the established restricted dev/test credentials and existing test tenant fixtures from the current repository setup. Confirm the central V1 histories are already correctly initialized using the established bootstrap procedure; never rebaseline a populated V2 database or alter a checksum. No rebuild, volume deletion, new production database or administrator runtime is needed.
3. Resolve the newly added managed Spring Session artifact and Bouncy Castle dependency personally if missing. Review dependency convergence/vulnerabilities and public API compatibility with unchanged Boot 4.2.0/Java 21. The agent did not download them. Keep frontend dependencies and lockfiles unchanged.
4. From `/workspace/Backend`, run the focused owner checks, then the full existing verification. Prefer offline Maven once dependencies are cached:

   ```bash
   ./mvnw -o -Dtest=IdentityPolicyTest,CompanyAuthenticationProviderTest,SessionBoundaryTest,AuthenticationStorageFailureTest,AuthenticationIntegrationTest test
   ./mvnw -o verify
   ```

   Missing dependencies or restricted test infrastructure are errors to resolve, not reasons to disable tests/security. V2 applies through the existing platform initializer under the restricted migrator when the owner starts tests/application. Inspect test reports and actual privileges; validate billing/platform histories remain independent and tenant locations remain excluded. Existing tenant/pool/Redis/ArchUnit regressions must pass unchanged.
5. On isolated, explicitly authorized test infrastructure, verify actual PostgreSQL writer loss, Redis load/write/limiter loss and response-loss interleavings. Active sessions and login must deny; logout must not acknowledge before durable revocation; a Redis failure after committed revocation must leave restored stale copies denied. Exercise expired sessions, concurrent logout, changed security policy and cross-company manual-cookie presentation. Do not use test exception stubs as evidence of complete real-outage behavior.
6. Configure local TLS and exact temporary test origins; inspect real browser/curl cookie flags, no Domain, fixation, fresh-CSRF-after-login behavior and host binding. Demonstrate that a copied valid cookie can replay at the same host until expired/revoked, and record that accepted limit. Ensure network/edge source attribution and budgets are usable behind the actual proxy. Verify browser storage and access/error logs contain no passwords/session credentials. Production integration is blocked until required origin/RP DNS ownership and connector/proxy trust are proven.
7. Perform the owner's full DevSecOps/CI verification with existing GitHub Actions, Gitleaks, Trivy and SonarCloud protections. Review Argon2 memory/latency and concurrent-load budgets; verify private Redis TLS/ACL scope/non-eviction and PostgreSQL writer/failover semantics. In an isolated restore rehearsal, keep ingress closed, fence stale writers, use a fresh Redis namespace, reconcile restored account/membership/revocation authority from independent evidence and require new authentication before reopening. A successful source build alone does not satisfy these gates.

## Outstanding security implementation and release blockers

- **Unverified build/runtime:** all new code/test/dependency/migration behavior awaits the owner's procedure; no passing results or production readiness claim.
- **Identity lifecycle:** email ownership/invitations/activation, reset/change/recovery, security notifications and structured durable audit/outbox remain unavailable. Pending registration intentionally cannot complete company onboarding. Blocklist completeness/update policy and enumeration/timing/load assessment remain required.
- **MFA and assurance:** implement native WebAuthn with the exact company RP/origin decision, atomic scoped challenges, UV and privileged hardware assurance, multiple-factor enrollment/removal and last-factor protection. TOTP only where policy permits, with encrypted secrets and accepted-step uniqueness. Recovery must preserve assurance with independent privileged approval. All required-MFA/privileged password sessions remain blocked in this batch.
- **Authorization:** `COMPANY_SESSION` is not RBAC. Implement current server roles/permissions, object/field ownership, secure membership/entitlement write contracts, independent worker authority, and explicit endpoint policies. No ERP business endpoint is opened by this foundation.
- **Sensitive business operations:** transaction-bound five-minute step-up, operation digest/amount/payee binding, atomic single-use consumption, independent dual approval and common central/tenant mutation/revocation fences remain mandatory before affected operations. Request-time checks alone cannot revoke already in-flight work.
- **Sessions and operations:** self-service session listing/revoke-all, meaningful-interaction idle semantics for polling UI, session-ledger retention, suspicious-use signals, Redis security/cache credential separation, production proxy/TLS/source attribution, restore epoch/fencing/reconciliation and independent audit preservation still need implementation/operational verification. Do not treat Redis namespace prefixes or shared runtime database credentials as hostile-process isolation.

No automatic infrastructure action or production account activation is authorized by this handoff. Finish and verify the remaining security stages before exposing production ERP functionality.
