# Phase 04 Stage 05 — authorization foundations

## Implemented scope

- Platform and company authorization are separate services, session-context types and database functions. Platform authorization reads current PostgreSQL account status/version, active platform role, permission mapping and a host-bound platform session. Access is denied unless that session carries a database record with `HARDWARE_ATTESTED` assurance, verified attestation metadata and an unexpired 15-minute idle / 8-hour absolute lifetime.
- Company role assignment is available as an application foundation only. It requires current `company.roles.manage` authority, an operation-bound WebAuthn step-up for the requester and a distinct authorized approver. PostgreSQL revalidates the approved intent and current authority, rejects assigning to either participant, rejects self-assignment and cross-company targets, locks the company/membership, consumes approval and changes the role in one transaction. The membership security-version trigger invalidates old sessions. No HTTP role-management endpoint or platform-role grant endpoint is exposed.
- API requests use Redis fixed windows: 300 requests per server-observed source address and 120 per authenticated company/account scope per 60 seconds. Anonymous requests are source-limited. Requests over quota return 429; a missing source or unavailable Redis returns 503. Forwarded-IP headers are not used. Existing stricter authentication/protection limits remain separate.
- The Trivy cache restore/save actions now use the same stable `${{ runner.temp }}/trivy-cache` path as the shell environment. The version-pinned Trivy v0.76.2 documentation confirms the workflow’s filesystem DB download, Java DB download, ignore-file, package inventory and JSON options. The packaged-JAR step still requires a complete embedded Maven inventory and propagates scanner failure. GitHub Actions was not run.

## Migration and operator actions

- Added platform migration V8. V2–V7 remain unchanged. Apply V8 with the restricted platform migrator using normal Flyway validation/migration; do not use Flyway repair or broaden runtime grants.
- V8 adds platform-administrator session evidence and a narrowly scoped authorization function, and an atomic company-role assignment function. Runtime cannot insert/update/delete platform role rows or directly write platform session evidence; the authorized functions are the only new runtime entry points.
- No application flow currently creates platform-admin sessions or verifies attestation evidence. Therefore no real platform administrator can authenticate through this foundation until a reviewed hardware-attestation verifier and platform-host session provisioning flow are delivered. Do not populate the session table from a browser claim, `UV=true`, user-agent, or unverified attestation.
- The owner/operator must review V8 grants in each target environment and confirm the platform migrator is restricted, the runtime role can execute only intended functions, `admin.hich.com` is dedicated, TLS/origin/RP ownership is correct, and production PostgreSQL/Redis failure behavior is validated. Preserve independent migration histories; do not reset data.

## Browser and authenticator verification checklist

Automated MockMvc, PostgreSQL, Redis and native WebAuthn test results are recorded separately from these manual checks. This checklist has not itself been executed.

1. **Login:** On a configured company HTTPS origin, obtain a CSRF token and log in with valid credentials and the required factor. Confirm success only after server-side authentication and that a wrong origin, wrong host, missing CSRF token, invalid factor and replayed challenge fail.
2. **Logout:** Log out through the CSRF-protected POST endpoint. Confirm the session cookie is cleared, the PostgreSQL session is revoked, and replaying the old cookie receives 401.
3. **WebAuthn registration:** Use a supported authenticator and verify the browser’s RP ID/origin, user verification prompt and successful native registration. Repeat with a wrong origin, denied UV, altered challenge and replay; each must fail. Use a physical key or platform authenticator only for a separately documented real-device test.
4. **WebAuthn authentication:** Authenticate with the registered credential; then try a different company origin, invalid signature, stale/replayed challenge and revoked credential. Confirm no authenticated session is issued on failure.
5. **Step-up:** Exercise a sensitive operation with a fresh WebAuthn ceremony. Change one canonical operation detail, use another company/session, wait past expiry and replay a consumed authorization; all must be denied.
6. **Session cookies:** Inspect browser storage and responses. Confirm `__Host-HICHSESSION` is Secure, HttpOnly, SameSite=Strict, path `/`, has no Domain attribute and is not exposed to JavaScript. Confirm API responses use `Cache-Control: no-store`.
7. **CSRF/CORS:** Confirm state-changing requests without the session CSRF token fail; valid same-origin requests succeed. An unapproved origin must not receive credentialed CORS access.
8. **Cross-company denial:** Copy a company A cookie to company B’s origin and try a forged company/tenant identifier. Confirm no session, data, function or schema switches to B, and the denial is audited.
9. **Session revocation:** Revoke the session from a second authenticated browser/device. Confirm the first session fails on its next request and cannot be restored by refreshing its Redis session.
10. **Platform administration:** Confirm company-origin sessions and company roles cannot access `admin.hich.com`. Until the verified attestation and platform-session provisioning flow exists, confirm all platform-admin access remains denied. Hardware-backed access has not been verified by automation or this checklist.

## Remaining boundaries

- No React browser-flow integration, role-management UI/API, platform-admin login/attestation verifier, platform role assignment, tenant business resource/object/field enforcement, worker quotas, upload/concurrency quotas, or atomic authorization of future ERP mutations was added.
- Production trusted-proxy configuration, TLS, browser edge behavior, independent immutable audit storage, production alerting/retention, migration rollout, hardware authenticator compatibility and independent security review remain release gates. Do not claim production readiness from automated tests.
- Target work: Phase 05 for platform-admin session provisioning and role-management UX; Phase 07 for tenant routing, workers and commit fences; Phases 08–10 for resource-level enforcement and frontend flows; Phase 11 for adversarial/browser/hardware verification; Phase 12 for deployment and audit-preservation operations.

## Verification

Targeted Stage 05 verification passed: 55 tests across migration, account-protection and quota-filter suites, with zero failures, errors or skips. The final offline full build passed with:

```text
MAVEN_OPTS='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC' \
  mvn -o -B -ntp -DforkCount=0 clean verify
Tests run: 342, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The shared host repeatedly failed classpath scanning with `Cannot allocate memory` when Surefire used its separate test JVM. Running Surefire in-process avoided the resource failure without changing test selection or assertions. An independent owner can repeat the passing command above, or run `mvn -o -B -ntp clean verify` on a host with sufficient memory. GitHub Actions, browser testing, production operator checks and physical hardware verification are not implied by Maven success and remain unverified.
