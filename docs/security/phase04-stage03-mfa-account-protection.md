# Phase 04 — Stage 03: MFA and account protection

Date: 2026-11-28. Current consolidated implementation and owner verification handoff.

**The owner reports all 280 preexisting tests passed before this batch. Those test source files are preserved unchanged. Stage 03 has not been compiled, tested or browser-verified by the agent. It is not production-ready.** No Maven, dependency installation, Docker/infrastructure change, database/Redis execution, Git commit or push was performed. Static source/public-API review is not runtime verification.

## Exact changed files

This list is relative to `/workspace` and describes only Stage 03. Preexisting uncommitted changes remain in place. No CI file, old test, applied V1/V2 migration, tenant routing/cache implementation or unrelated ERP UI is changed. The existing React entrypoint selects the new security route and renders the original App for all other paths.

- `Backend/pom.xml`
- `Backend/src/main/java/com/saaserp/erp/config/AccountProtectionConfig.java`
- `Backend/src/main/java/com/saaserp/erp/config/AuthenticationConfig.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/api/AccountProtectionController.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/api/AuthenticationController.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/api/AuthenticationErrors.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/AccountProtectionService.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/FactorCryptography.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/PasswordVerification.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/ProtectionFailure.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/ProtectionRateLimit.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/ProtectionRepository.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/ProtectionSessionState.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/SecurityNotifications.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/application/WebAuthnCeremonies.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/domain/MfaPolicy.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/domain/SessionGrant.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/JdbcIdentityRepository.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/JdbcProtectionRepository.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/NativePasswordVerification.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/NativeWebAuthnCeremonies.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/ProtectedFactorCryptography.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SecurityFailureBoundary.java`
- `Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SessionAuthorityFilter.java`
- `Backend/src/main/resources/application.properties`
- `Backend/src/main/resources/db/migration/platform/V3__mfa_and_account_protection.sql`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionServiceTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/MfaPolicyAndCryptographyTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/NativeWebAuthnCeremoniesTest.java`
- `Backend/src/test/java/com/saaserp/erp/platform/identity/WebAuthnTestAuthenticator.java`
- `Frontend/src/main.jsx`
- `Frontend/src/security/AccountProtection.css`
- `Frontend/src/security/AccountProtection.jsx`
- `Frontend/src/security/accountProtection.js`
- `docs/adr/0004-authentication-and-session-architecture.md`
- `docs/security/attack-surface-and-trust-boundaries.md`
- `docs/security/authentication-technology-qualification.md`
- `docs/security/phase04-authentication-foundation.md`
- `docs/security/phase04-stage03-mfa-account-protection.md`
- `docs/security/security-acceptance-criteria.md`
- `docs/security/security-requirements-traceability-matrix.md`
- `docs/security/security-requirements.md`
- `docs/security/threat-model.md`

## Architecture and resulting behavior

The accepted native Spring Security/Spring Session Redis architecture is unchanged. PostgreSQL is authoritative for account/company/membership policy, short-lived MFA state, challenges, factors, accepted TOTP steps, token/code consumption, session grants and revocation. Existing company hostname binding, host-only cookies, exact Origin/CORS, CSRF, tenant context cleanup, runtime/migrator separation and deny-by-default routing remain. No JWT, DPoP, OAuth authorization server, microservice or experimental project is added.

A dedicated password-verified MFA transaction is bound to account, company, exact hostname, current security versions and a random secret held only in its Redis HttpSession. The database stores the secret's digest. It lasts five minutes and grants no `COMPANY_SESSION` authority. Start, factor completion and final authentication rotate session IDs; final authentication uses native ChangeSessionIdAuthenticationStrategy, CsrfAuthenticationStrategy and HttpSessionSecurityContextRepository. Cancel/logout durably marks the pending transaction terminal and revokes any grant it already produced before invalidating browser state, including a cancel/completion race. A restored stale partial state cannot finish.

Native Webauthn4JRelyingPartyOperations uses only supported public credential/user repository interfaces, option/response APIs and the Jackson 3 WebauthnJacksonModule. Each operation has a separate scoped RP instance, company credential set, exact company hostname RP ID and `https://<hostname>` allowed origin. No parent-domain RP/cookie, wildcard, related-origin delegation or shared central RP is introduced. Both ceremonies require UV; cross-origin embedding, topOrigin, wrong credential owner/type/ID, malformed/oversized input, origin/RP/challenge/signature errors fail. PostgreSQL consumes a two-minute account/transaction/purpose-bound challenge in an independently committed transaction before cryptographic verification; a failed response does not restore it. The mutation then locks and rechecks current account/company/membership authority. Public key, attestation/client data and counters persist under the issuing company; private keys never reach the server.

**Registration is not authentication.** A successful registration clears verified state, consumes first-enrollment approval when applicable and revokes existing account sessions. A separate fresh signed assertion must succeed before ERP authority can be issued. First-factor setup requires a current, company-bound 15-minute enrollment approval established by a valid email-ownership/enrollment token or a trusted provisioning workflow. Password alone cannot approve first-factor setup. The approval is consumed once. First enrollment durably records MFA opt-in and the member MFA requirement; restricted recovery never reopens password-only login or email-only first-factor approval. Existing-factor addition/removal, backup-code replacement and password change require recent MFA within the five-minute transaction. Removal cannot delete the last usable factor and clears assurance until a remaining factor is verified. Replace a factor by enrolling first, proving an allowed factor and removing the obsolete credential.

| Current server policy | Result |
| --- | --- |
| `PLATFORM_ADMIN`, account privileged or membership privileged | Hardware-backed assurance is required. No reviewed attestation/metadata trust policy exists yet, so password, ordinary WebAuthn, TOTP, code recovery and email enrollment cannot grant access. Independent privileged recovery remains blocked. |
| Company `OWNER`, `ADMIN`, `MANAGER` | MFA is mandatory even if boolean MFA flags are false. Successful permitted MFA uses 15-minute idle/8-hour absolute session limits. These roles do not inherit platform authority. |
| `EMPLOYEE` | MFA is required if account/company/membership policy requires it or any factor is enrolled. Otherwise the existing password-only flow remains available, with 30-minute idle/12-hour absolute limits. |
| TOTP alternative | Both current company AND membership must explicitly allow it. It never satisfies hardware-required policy or silently replaces WebAuthn. |

None attestation is used only for ordinary/company WebAuthn. UV, brand names, backup flags or a successful native ceremony are not claimed as hardware-bound assurance. No platform-admin RP flow is opened. Generic recent account MFA is not a business transaction-bound step-up or independent dual approval.

TOTP uses maintained java-otp (RFC 6238), a random 160-bit secret, HMAC-SHA1, 30-second steps, six ASCII digits and ±one-step tolerance. An atomic increasing accepted-step update rejects replay across requests/nodes; confirmation also consumes its accepted step. AES-256-GCM protects active/pending secrets, with random 96-bit nonces, 128-bit tags, operator key IDs and account/company/purpose AAD. The only intentional plaintext disclosure is a one-time setup URI to the approved, CSRF-protected enrollment browser; it stays transient and private. No plaintext secret enters database/outbox/logs or ordinary APIs. Throttling uses existing Redis atomic budgets plus a durable ten-attempt limit per five-minute transaction; exhausting a transaction never disables the account or permanently locks it.

Ten recovery codes are independently generated 256-bit values, shown once, hashed and scoped to account/company with one-year expiry. Replacement invalidates the previous set. Password plus one current code atomically clears only that company's factors, revokes account sessions and enters restricted reenrollment; the code cannot itself mint ERP authority. Current policy and a new confirmed factor still apply. Hardware-required recovery denies until independent verified recovery is implemented.

Email verification, initial-enrollment approval and password reset use 256-bit random, address/purpose/hostname/account-version-bound, fifteen-minute single-use tokens with original company/policy snapshots. Only digests persist. The restricted completion function verifies the raw token using PostgreSQL's built-in SHA-256; passing its stored hash does not count as proof. Reset preserves existing factors/MFA policy and revokes sessions. Hardware-required password reset is denied pending independent privileged recovery. Ordinary MFA in another company also cannot replace a hardware-privileged identity’s global password. Authenticated password change verifies the current password, applies the existing screening/Argon2 policy and advances the security version atomically. Native parameter-only rehash preserves its existing semantics. Factor changes/code replacement/password changes revoke current account sessions and fence other pending transactions; current company/membership versions and policy still apply to every request.

SecurityNotifications is an application port with a transactional PostgreSQL outbox adapter. Factor, code, password, verification and session-revocation changes queue metadata events with the transition. Sensitive email-delivery payloads are encrypted separately from database credentials; protected recipient/hostname metadata preserves the intended delivery context. Generic email requests return 202 without returning the delivery token or disclosing account existence. **No external email sender/provider is implemented or verified.** Legitimate verification/enrollment/reset completion therefore requires a separately reviewed delivery adapter; a queued record is not proof of email delivery.

## Dependencies and public API review

| Dependency | Reason / status |
| --- | --- |
| Add `org.springframework.security:spring-security-webauthn` | Native RP operations and protocol repositories/DTOs; version remains BOM-managed (locally inspected 7.2.0). Managed WebAuthn4J 0.31.11.RELEASE performs verification. Both cached artifacts were inspected; no private APIs or default built-in unqualified JDBC schema are used. |
| Add `com.eatthepath:java-otp:1.0.0` | Maintained RFC 6238 implementation instead of custom OTP code. Its versioned public source/API was reviewed. Artifact was not cached during inspection and must be resolved personally by the owner; nothing was downloaded by the agent. |
| Existing Bouncy Castle 1.86, Spring Session, Jackson 3, JDBC, Redis, Java 21 / Boot 4.2.0 | Reused. Bouncy Castle supplies Base32 and the existing Argon2 dependency; JCA supplies AES/GCM, SecureRandom and digests. No Node dependency/lockfile changes. |

Public API references: [native RP operation](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/webauthn/management/Webauthn4JRelyingPartyOperations.html), [versioned java-otp source](https://github.com/jchambers/java-otp/tree/java-otp-1.0.0), [PostgreSQL built-in binary hashes](https://www.postgresql.org/docs/16/functions-binarystring.html). Cached javap review checked public builders/repositories, nullable timestamps, Jackson 3 module and native counter updates. Compilation is still owner verification. The existing configuration test stays in `com.saaserp.erp.config`; no production method was made public for testing.

## Migration, configuration and operator actions

`platform/V4__mfa_lock_and_webauthn_options.sql` adds the lock-only function; the applied V1-V3 migrations remain unchanged. V3 creates MFA transactions/challenges, company-scoped native credentials/TOTP, recovery codes, purpose tokens and notification outbox; adds authentication roles, explicit TOTP permissions, enrollment deadline and session assurance/privileged snapshots. Policy/enrollment changes increment membership/company versions through the existing trigger contract.

The existing restricted migrator owns new objects. Runtime receives explicit column-scoped workflow writes and necessary session INSERT columns. It cannot extend token/challenge/transaction deadlines, change token purpose/address snapshots or mark delivery; token consumption remains confined to the proof-verifying function. Notification outbox is INSERT-only for runtime; it cannot mark delivery or read encrypted delivery payloads through this grant. Direct runtime activation/email verification, role/entitlement/membership assignment and account-security-version UPDATE are not granted. Four SECURITY DEFINER functions, owned by the restricted migrator with pg_catalog search_path and qualified SQL, permit only proof-bound account token completion, security-version advance/revocation, consumption (never creation) of an enrollment approval, and locking the currently authorized MFA transaction rows. The lock function performs no writes; runtime still has no UPDATE privilege on company memberships. PUBLIC execute is revoked. No administrator credential, schema CREATE right, pgcrypto extension or broad default grant is added. A compromised shared runtime/process is still outside the isolation guarantee; explicit ports/grants are not a hostile-code sandbox.

WebAuthn option JSON is generated by the Spring Security RP and persisted with the one-use challenge. Completion reconstructs the native creation/request option models through their public builders, including challenge, RP/user identity, algorithm set, credential allow/exclude lists, timeout, user-verification selection, attestation and extensions. The Spring WebAuthn Jackson module remains in use for supported credential serialization; native signature verification is unchanged.

| Setting / responsibility | Required action |
| --- | --- |
| Existing restricted DB/test/Redis credentials | Retain established role separation and test-database guards. Current authority must use the PostgreSQL writer. Apply pending V4 through the existing Flyway migration identity on startup/deployment; do not run migration SQL manually, rebaseline, or use Flyway repair. |
| `AUTH_ALLOWED_ORIGINS` | Exact approved HTTPS company origins with verified DNS/TLS ownership; no ports, wildcards, customer-content origin, parent RP or implicit admin RP. Connector must see secure TLS and canonical Host; forwarded headers remain untrusted. |
| `AUTH_PROTECTION_ACTIVE_KEY` | Active key identifier, e.g. a secrets-manager version label. No secret default. |
| `AUTH_PROTECTION_KEYS` | Comma-separated `id:base64-32-byte-AES-key` entries from a protected secret store, with distinct environment keys. Never put keys in Git, logs, screenshots or command history. New writes use the active ID; keep required historical keys for old TOTP/outbox ciphertext. Empty settings disable encryption-dependent operations by failing closed; malformed configured keys fail startup. Missing historical keys deny dependent operations. Re-encryption/retirement automation is not implemented. |
| Existing `AUTH_PASSWORD_BLOCKLIST` / session namespace | Supply current private compromised-password digests and retain environment-separated Redis namespaces/TLS/ACL/non-eviction protection. No Redis ACL or infrastructure was changed. |
| Role/TOTP assignment | Trusted platform/provisioning workflows must assign correct auth roles and explicitly approve TOTP at BOTH company and membership. Defaults are EMPLOYEE/TOTP denied. Client role storage grants nothing. Do not weaken a hardware requirement to make login succeed. |
| First enrollment | Use valid email verification/enrollment-token consumption against an already authorized active membership, or an approved trusted provisioning process. A new account still gains no membership/provisioning automatically. Initial approval lasts fifteen minutes and is consumed; production invitation/provisioning lifecycle remains separately gated. |
| External notifications | Implement/review a provider adapter and least-privilege outbox reader/delivery process, ciphertext access, destination verification, redaction, retry/idempotency and delivery monitoring before claiming email flows work. No sender credential or network email call exists in this batch. |
| Frontend hosting | Serve the existing React app at the approved company origin, with `/account-security` routed to its index and `/api/v1` to the same monolith. Use production-equivalent TLS/Host forwarding. Existing ERP rendering elsewhere is preserved; its local prototype roles/data remain untrusted. |
| Time/retention/recovery operations | Review trusted clocks, bounded request/transaction timeouts, outbox/challenge/token/session retention, load budgets and restore fencing. Security mutations use ten-second transaction budgets; independent attempt/challenge commits use five-second budgets. Expiry guards use current database wall time and pending state is checked again after acquiring locks. Apply a fresh Redis namespace and reconcile durable revocation before reopening restored security state. |

## Endpoint contracts

All paths below begin `/api/v1/auth`. Every POST requires the exact current company Origin, native session cookie and CSRF token from GET `/csrf`. The current company is resolved from the validated hostname and database mapping, never a client company/role field. Email/password at `/mfa/start` are form-only, bounded and rejected in URL queries/duplicate fields; other bodies use validated JSON with DTO/native-credential limits. Review edge/connector pre-parse body limits before deployment. Pending transaction/binding are derived from the HttpSession, never supplied as client authority.

| POST suffix | Input | Success and authority |
| --- | --- | --- |
| `/mfa/start` | Form `email`, `password` | 202 empty; native password verification, restricted pending state, ID rotation and removal/revocation of any prior authenticated/pending state. No ERP access. |
| `/mfa/webauthn/registration/options` | No business fields | 200 `{challengeId, publicKey}` W3C creation options; requires first-enrollment approval or recent existing MFA. |
| `/mfa/webauthn/registration/complete` | `{challengeId, credential}`; credential is the JSON string from browser `credential.toJSON()` | 204; one-use native verification, persisted credential, consumed approval/version fencing and ID rotation. Still no ERP authority; requires subsequent signed assertion. |
| `/mfa/webauthn/authentication/options` | No business fields | 200 `{challengeId, publicKey}` request options scoped to this account/company, UV required. |
| `/mfa/webauthn/authentication/complete` | Same reply envelope | 204; fresh native signed assertion and UV establish restricted verified state; ID rotates. |
| `/mfa/totp/enroll` | No business fields | 200 `{provisioningUri}` once to approved browser; existing MFA or first-enrollment approval and explicit TOTP policy required. Keep this setup secret private; it is not an ordinary account read endpoint. |
| `/mfa/totp/confirm` | `{code}` six ASCII digits | 204; encrypted pending secret activated and accepted step consumed; enrollment approval consumed/version fenced. |
| `/mfa/totp/verify` | `{code}` | 204; current explicit TOTP policy, replay fence and restricted verified state; ID rotates. |
| `/mfa/finish` | No business fields | 204 only after fresh allowed factor/current writer checks; committed company grant, rotated ID/CSRF, credential-free native SecurityContext in Redis. Fetch fresh CSRF afterward. |
| `/mfa/cancel` | No business fields | 204; durably terminal pending state, attributes removed and ID rotated. |
| `/mfa/factors/list` | No business fields | 200 factor type/public credential IDs only, under recent MFA. No private key/secret/attestation data. |
| `/mfa/factors/remove` | `{credentialId}`; `TOTP` identifies its permitted alternative | 204; recent MFA, last-usable-factor guard, session/version fencing; prior verified state cleared. Prove a remaining factor to continue. |
| `/mfa/recovery-codes` | No business fields | 200 `{codes:[...]}` once after fresh MFA; old set invalidated, hashes only at rest, sessions/version fenced. |
| `/mfa/recover` | `{code}` | 204; password-verified transaction plus current code permits restricted reenrollment only, clears this company's factors and revokes sessions. Hardware policy denies. |
| `/account/password/change` | `{currentPassword,newPassword}` | 204 after fresh MFA, current-password verification and password screening; security version advanced and sessions revoked; start again. |
| `/account/sessions/revoke-all` | No business fields | 204 after fresh MFA; account sessions/pending authority fenced. |
| `/account/email/request` | `{email}` | Generic 202; applicable verification token encrypted/queued, never returned. |
| `/account/email/verify` | `{code}` raw email token | 204 after one-use address/purpose/host/version proof; verified status, scoped initial approval when eligible, security event and revocation. No company membership is created. |
| `/account/mfa/request-enrollment` | `{email}` | Generic 202; encrypted/queued company-host enrollment token. |
| `/account/mfa/approve-enrollment` | `{code}` raw token | 204 after proof; eligible current membership gets one fifteen-minute first-enrollment approval. Begin a new pending transaction afterward. |
| `/account/password/request-reset` | `{email}` | Generic 202; applicable purpose-bound reset token encrypted/queued. |
| `/account/password/reset` | `{token,newPassword}` | 204 after screening and one-use proof; password/security version changed, sessions revoked, factors/MFA preserved. |

Original `/register`, `/login`, `/session`, `/csrf`, `/logout` remain. Password-only `/login` rejects policy-required or enrolled MFA; `/session` reports its actual PASSWORD/WEBAUTHN/TOTP assurance. `/logout` now also durably cancels any partial transaction. Unlisted APIs deny. Generic error codes: 400 invalid_request; 401 authentication_failed for invalid credentials/factor/token/policy/state; 403 CSRF/Origin/Host denial; 429 too_many_requests; 503 security_unavailable for required storage/keys. Framework CORS errors remain framework-generated 403. There is no secret-bearing redirect, query, authentication browser storage or automatic POST retry.

## Automated coverage and evidence

44 new unexecuted test methods in four test classes, plus a reusable test-only authenticator:

| Source | Defined coverage |
| --- | --- |
| `NativeWebAuthnCeremoniesTest` | Native positive UV registration and signed EC assertion; wrong origin/RP/challenge, missing UV, owner/key/signature substitution, cross-origin embedding, malformed/duplicate registration and unconfigured RP rejection. |
| `MfaPolicyAndCryptographyTest` | Role/hardware/TOTP policy, AES-GCM tag/AAD/key failures, published RFC 6238 vector, adjacent-window/invalid/replay inputs and high-entropy token generation. |
| `AccountProtectionServiceTest` | No password-only/recovery-only grant; fresh-MFA management restrictions; hardware downgrade rejection; TOTP replay; burn-before-verify ordering; last-factor/stale-MFA denial; writer outage. |
| `AccountProtectionIntegrationTest` | Real restricted PostgreSQL and Redis with native EC fixtures/MockMvc: full HTTP creation+signed assertion+finish, partial and full logout, fixation/CSRF/assurance, one-use/expired challenge, binding/host/version isolation, initial approval, TOTP replay, concurrent recovery consumption, factor replacement, password/reset/email token fencing, outbox rollback, administrative deadlines and bounded attempts without disabling account. |
| `WebAuthnTestAuthenticator` | In-memory JCA P-256 signatures and native WebAuthn4J CBOR models; no production private key or test dependency added. |

Negative assertions name the exact rejection types/statuses and inspect persisted state; no broad Exception/RuntimeException security assertions, skipped tests or suppressed failures were introduced. Test fixtures verify `hich_erp_test`/`hich_runtime_test` and use only `hich_migrator_test` for owned rows/approval setup and exact cleanup. They create no roles/schemas/containers, do not alter production records and clean only tracked Redis sessions/limiter keys. Static integrity review found all original test/migration/CI files unchanged. That is not evidence the new suite passes.

## One consolidated owner verification procedure

1. Review this exact working-tree batch separately from preexisting changes. Confirm all 280 original tests, security assertions/architecture rules, V1/V2 checksums, tenant/cache behavior and CI protections remain. Review V3 typed SECURITY DEFINER functions/explicit grants and the role matrix. Never grant runtime direct activation/membership rights or weaken hardware policy to pass a test.
2. In the existing Dev Container, personally resolve the newly declared managed WebAuthn and java-otp artifacts if needed. Verify unchanged Java 21/Boot 4.2.0 and dependency convergence/security advisories. No agent download occurred. Configure restricted test credentials and the established tenant fixtures; do not rebuild images, recreate databases/volumes or rebaseline an initialized history.
3. Run the new focused tests and then the entire preserved suite yourself, preferring offline Maven once cached:

   ```bash
   cd /workspace/Backend
   ./mvnw -o -Dtest=NativeWebAuthnCeremoniesTest,MfaPolicyAndCryptographyTest,AccountProtectionServiceTest,AccountProtectionIntegrationTest test
   ./mvnw -o verify
   ```

   Inspect compilation, Surefire reports, exact test totals and all ArchUnit/tenant/database/Redis regressions. V3 must apply once under the existing restricted platform migrator. Missing dependency/infrastructure/permission errors are blockers to resolve, not tests to skip. No Maven invocation has been performed by the agent.
4. Configure production-equivalent HTTPS/canonical company Host, exact origins, strong encryption keyring, screening corpus and protected PostgreSQL writer/Redis access on isolated owner-controlled infrastructure. Verify new grants deny direct runtime account activation, email/version/role/membership assignment, schema/history modification and outbox delivery claims. Review key retention/rotation and clock/transaction/request budgets. Do not expose real account onboarding until trusted membership provisioning and notification delivery are ready.
5. Wire and verify the external notification adapter separately before testing real mailbox flows: generic existing/unknown requests, address/purpose/host/version-bound email/enrollment/reset proof, expiry, one use, redaction and queued-vs-delivered status. Never send production secrets to an unreviewed provider. Raw delivery tokens must not be returned by request APIs or appear in URLs/logs/analytics. Database fixture decryption in automated tests is not a production email-verification workaround.
6. Browser-verify the actual existing React `/account-security` route at each approved company HTTPS origin. Use eligible ordinary employees plus OWNER/ADMIN/MANAGER and hardware-required identities. Obtain/consume a scoped enrollment email, start with the password, register a real authenticator, confirm registration alone cannot finish, perform a separate signed UV assertion and finish. Inspect host-only Secure/HttpOnly/SameSite=Strict/Path=/ cookies, ID changes, CSRF refresh, actual `/session` assurance and normal UI return. Exercise a supported synced/passkey device and a physical key as ordinary MFA without claiming hardware assurance. Test cancellation, unsupported JSON APIs/browser, absent UV, wrong/sibling RP/origin, old cookies, copied A cookie at B and replayed challenge. No new experimental project is needed.
7. Where explicit policy permits, verify private one-time TOTP provisioning, confirmation, repeated/concurrent steps, bounded drift, key loss/tamper behavior and restart persistence. Save recovery codes once; race a code across two sessions, confirm restricted recovery cannot finish until a new factor is proved, and verify replacement/removal/last-factor guards. Test password changes/reset without factor downgrade, account/session/version revocation across nodes and shorter administrative idle/absolute limits. Hardware-required access/recovery must remain denied until independently reviewed evidence exists.
8. In isolated authorized failure/restore exercises, inject PostgreSQL writer, Redis load/save/limiter and outbox/key failures, unknown commits, concurrent policy changes/cancel/logout and long lock waits. No cached allow, partial-factor authority, restored consumed challenge/token or false delivery/success claim is acceptable. Measure generic response timing, account/source budgets and hashing/verification load; short-lived throttles must not permanently disable accounts. Run the owner's existing Gitleaks/Trivy/SonarCloud/GitHub Actions and broader DevSecOps verification. Keep ingress fenced during restore, invalidate Redis namespaces and reconcile durable revocation from independent evidence before reopening.

## Remaining limitations and release blockers

- New code/migration/dependency/test/browser behavior is unverified. The TOTP artifact requires owner resolution. No production readiness, hardware assurance or browser compatibility success is claimed.
- Hardware attestation/trusted metadata/backup policy, the separate privileged admin RP and independent privileged recovery remain unimplemented and fail-closed. Ordinary UV/none attestation and TOTP cannot substitute.
- External email delivery is not implemented/configured/verified. Only the port/encrypted outbox and secure token workflows exist. Real first-factor mailbox approval, verification and reset remain blocked until delivery and trusted membership/invitation provisioning are operational.
- The new browser screen is a functional integration surface with transient credentials, not a completed production UX/browser qualification. It deliberately requires supported native WebAuthn JSON APIs. Existing local ERP roles/business data remain prototype presentation state; backend RBAC/object/field authorization and ERP integration remain separate stages.
- Recent account MFA does not implement transaction-digest-bound business step-up, independent dual approval, central/tenant commit fences or independent worker authorization. Preserve those controls before exposing sensitive business operations.
- Outbox delivery/retention/re-encryption automation, independent audit preservation, meaningful-interaction idle tracking, production TLS/proxy/source attribution, Redis ACL/cache isolation, operational restore/revocation reconciliation and performance/abuse/timing evidence remain owner/integration release work. Source guards are not hostile-process or shared-credential isolation.
- Valid stolen session cookies may replay within current authority/lifetime constraints. HttpOnly/TLS/rotation/revocation/MFA reduce but do not eliminate that approved residual risk; no browser proof-of-possession equivalence is claimed.
