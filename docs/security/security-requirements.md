# Security requirements specification

Phase 04 authentication-foundation revision, 2026-11-27. ADR-0004 session architecture is owner-approved. Source changes and test definitions are unexecuted; no production or compliance claim.

## Scope and governing decisions

This specification preserves Java 21/Spring Boot modular monolith, React/Vite, one production PostgreSQL database with tenant schemas and separate platform/billing schemas, Redis, HikariCP and Flyway. Cloudflare/Nginx are planned production components. ADR-0001 through ADR-0003 and AGENTS.md govern dependency direction. No microservices, Kubernetes, extra production databases or infrastructure changes are introduced.

The authentication foundation now includes platform account/session persistence and native Spring Security/Spring Session integration. Stage 03 adds unverified source for MFA and account protection; hardware assurance, external delivery, tenant persistence activation, granular authorization, billing and ERP business workflows remain release work.

MUST/MUST NOT are mandatory conditions of the applicable delivery scope; SHOULD allows a documented justified exception; MAY permits a choice only within all stronger applicable policies. P0 blocks acceptance of the applicable feature or production release; P1 is required by its named phase and needs an explicit time-bounded risk exception if delayed. A future phase does not exempt an exposed feature from its applicable controls.

Identifiers are permanent: do not renumber or reuse retired IDs. Principle requirements are defined only in [security-principles.md](security-principles.md); all other requirements are defined here. References in [security-acceptance-criteria.md](security-acceptance-criteria.md) are verification scenarios, not additional requirements. Each record includes its own acceptance criteria; supplemental integrated scenarios do not replace them. Task 01 did not produce STRIDE analysis or the traceability matrix. Stage A Tasks 02–05 now supply the proposed [threat model](threat-model.md), [attack-surface analysis](attack-surface-and-trust-boundaries.md), [ADR-0004](../adr/0004-authentication-and-session-architecture.md) and [SRTM](security-requirements-traceability-matrix.md). ADR-0004 now accepts native company-bound server-side sessions; browser-token-specific records are retired below without reusing their IDs. Other security protections remain mandatory. Partial implementation statuses identify source only, not executed evidence.

## Status and phase vocabulary

| Status | Meaning |
| --- | --- |
| Implemented | Source and named test evidence support the narrowly stated foundation; not proof of runtime success or production deployment. |
| Partially implemented | Only explicitly named subcontrols exist; remaining scope is not delivered. |
| Planned | Required future implementation; no sufficient current evidence. |
| Deferred | Deliberately assigned to a later phase with current limitations recorded. |
| Retired | Browser JWT/refresh/DPoP requirements superseded by approved native sessions; ID retained for traceability. |
| Pending architecture decision | Mechanism/policy requires an approved later ADR; no choice is made here. |

Phase labels below preserve Task 01 responsibility/workstream vocabulary. The later Stage A instructions supply the established numbered roadmap; the [SRTM roadmap crosswalk](security-requirements-traceability-matrix.md) and each matrix entry map these labels to Phases 01–12. Payment gateway security belongs to Phase 06; tenant activation and stronger database-enforced privilege design belong to Phase 07 or an earlier applicable assurance/release gate. No future-phase assignment permits exposing an unprotected feature.

| Phase/workstream | Responsible owner and exit responsibility |
| --- | --- |
| P3-MAINTAIN | Backend/data maintainers preserve existing tenant/cache/database foundations. |
| P4-DESIGN | Security architect approves identity, assurance, session/token and consistency ADRs in later Phase 04 work. |
| P4-IMPLEMENT | Backend identity/security owner delivers authentication, MFA, authorization and session controls after ADR approval. |
| FRONTEND | Frontend owner delivers React/browser controls before affected UI release. |
| TENANT-ACTIVATION | Backend/data owner activates separate tenant persistence, authorized resolution, jobs and restricted provisioning. |
| TENANT-HARDENING | Data/security architect designs stronger database-enforced company privileges before claiming that guarantee. |
| BILLING | Billing/payment owner implements verified provider events, entitlements and provisioning gates. |
| ERP, ERP-SALES/BILLING, ERP-INVENTORY/PURCHASING | Respective module owners deliver business controls before module exposure. |
| INFRA | Production infrastructure owner delivers edge, network, runtime, secret and backup controls before production. |
| SUPPLY | Repository/release owner delivers supply-chain and CI gates before release. |
| OPERATIONS | Security operations owner delivers audit, incident handling, access review and tested recovery readiness. |
| SECURITY-VERIFY | Independent security/QA owner verifies applicable requirements and reviews evidence before release. |

## Trust boundaries and evidence baseline

| Boundary | Authority and enforcing component |
| --- | --- |
| Customer OS/browser/extensions/storage → API | Entire device and frontend are untrusted. Backend identity and application authorization enforce authority. |
| Internet → Cloudflare → Nginx → Spring | Future TLS/proxy/network policies establish transport and trusted header provenance, not user authorization. |
| Identity/membership/billing → tenant scope | Session guard validates current server records before binding; a UUID or existing context is not proof. |
| Application → Hibernate/HikariCP/PostgreSQL | Provider validates route/runtime and resets supported session state. Shared credentials retain broader SQL reach. |
| Application → Redis | TenantCache enforces scoped naming; future security-state contracts/ACLs must separate durable authority from disposable cache. |
| API → jobs and modules | Explicit application contracts and independent worker authorization mediate execution; no automatic context inheritance. |
| Provider → billing | Future signature, freshness, replay and authoritative state validation establish payment evidence. |
| Source/dependencies → CI → artifact → production | Repository/release owners validate reviewed source, tool integrity, provenance and deployment authority. |
| Operator/audit/backups → recovery | Independent approval, preservation, restricted keys and revocation reconciliation constrain administrative power. |

Evidence is source inspection only; existing automated tests were read, not executed. Paths below are repository evidence, not claims about live PostgreSQL grants, Redis ACLs, hosted GitHub settings or production deployment:

- [Tenant context](../architecture/tenant-context.md), [schema identifiers](../architecture/tenant-schema-identifiers.md), [Hibernate routing](../architecture/hibernate-tenant-routing.md), [pool isolation](../architecture/connection-pool-isolation.md) and corresponding shared/tenant Java/test sources describe supported lifecycle, validated routing and cleanup.
- [Database configuration](../architecture/database-configuration-scripts.md), [migrations](../architecture/database-migrations.md), [central bootstrap](../architecture/central-schema-bootstrap.md), infrastructure/postgres scripts and DatabaseMigrationIntegrationTest show restricted dev/test identities, separate histories and guarded bootstrap. Applied V1 migrations remain untouched.
- [Redis isolation](../architecture/redis-tenant-isolation.md), shared/cache sources, RedisTenantCacheConfig and cache tests show scoped string keys, atomic expiring writes and single-key deletion; they implement no security-state store or per-tenant Redis ACL.
- [Cross-tenant regression coverage](../architecture/cross-tenant-security-testing.md), CrossTenantSecurityIntegrationTest and TenantBoundaryArchitectureTest cover combined context/routing/cache behavior and inner-layer bypass rules. ArchUnit checks code dependencies, not runtime authorization.
- [application.properties](../../Backend/src/main/resources/application.properties) injects credentials, validates Hibernate schema, disables open-in-view/SQL display and limits error/health details. These settings alone do not demonstrate secret redaction or production hardening.
- [backend-ci.yml](../../.github/workflows/backend-ci.yml) includes restricted-role setup, explicit central baseline and Maven verification. The older central-bootstrap document calls the baseline step a proposal; the current workflow already contains it. Source inspection resolves this historical discrepancy without modifying older documents.
- [security-ci.yml](../../.github/workflows/security-ci.yml) defines Gitleaks and Trivy, read-only contents permissions and tag-pinned Actions. Scanner archive checksums, SHA-pinned Actions, SonarCloud, DAST, SBOM/signing/provenance and hosted branch protections are not established. Frontend/package-lock.json exists. No green CI result is inferred.

## Requirement catalogue

### SEC-CLIENT-001 — Customer-controlled environments

- **Requirement:** Customer operating systems, browsers, extensions, React code, JavaScript runtimes, browser storage, headers, parameters and bodies MUST be treated as untrusted; device administrator privileges MUST NOT confer backend or infrastructure authority.
- **Rationale:** Customer code and devices are outside operator control.
- **Priority:** P0.
- **Component / trust boundary:** Customer device → backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A normal authorized user can access their own permitted resource. Negative — Modified frontend roles and local OS administrator privileges grant zero extra backend permissions.
- **Limitations / residual risk:** R1/R2: a compromised device can act within the authenticated user’s existing authority.

### SEC-CLIENT-002 — Independent server decisions

- **Requirement:** The backend MUST independently validate identity, authentication state, roles, permissions, company membership, business rules and applicable security policies on protected operations.
- **Rationale:** UI restrictions are bypassable.
- **Priority:** P0.
- **Component / trust boundary:** Browser/API → application.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A direct API request with valid server permissions succeeds. Negative — Forged tenant IDs, roles and permission arrays do not change server authority.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-CLIENT-003 — Commercial state is server-owned

- **Requirement:** Prices, payment status, subscription state and entitlements MUST come from authoritative server-side records and verified provider evidence, never client assertions.
- **Rationale:** Commercial claims can be altered.
- **Priority:** P0.
- **Component / trust boundary:** Client/provider → billing.
- **Responsible phase:** BILLING (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A server-verified paid entitlement permits its intended operation. Negative — A modified paid flag or price cannot activate a subscription or lower a charge.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTH-001 — Password hashing

- **Requirement:** Passwords MUST be hashed with Argon2id, unique cryptographically random salts and versioned parameters calibrated on deployment hardware; parameters MUST be reviewed and hashes upgraded on successful verification when obsolete.
- **Rationale:** Slow salted hashes reduce offline guessing.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Equal passwords produce different salted hashes and approved latency/memory benchmarks are recorded. Negative — Plaintext, reversible passwords and unapproved weak parameter sets fail review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTH-002 — Password policy and screening

- **Requirement:** Password policy MUST permit long passphrases, paste and password managers, screen compromised/common passwords on creation and reset, and rate-limit verification; it SHOULD avoid arbitrary composition and periodic change absent compromise. Proposed minimum is 15 characters with at least 64 supported, subject to policy review.
- **Rationale:** Usability and screening reduce predictable credentials.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An allowed long passphrase works without truncation. Negative — A blocked compromised password and an over-limit abusive request are rejected.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTH-003 — Automated attack and enumeration resistance

- **Requirement:** Login, registration, reset and verification MUST use bounded account and source-aware throttling against brute force and credential stuffing, generic responses and materially equivalent failure timing; lockout recovery MUST resist attacker-induced denial of service.
- **Rationale:** Online guessing and identity discovery enable takeover.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Valid users can recover from a throttled state using an approved path. Negative — Distributed wrong-password attempts trigger throttling and existing/nonexistent accounts expose no distinguishing status/body.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTH-004 — Email ownership

- **Requirement:** Email ownership MUST be verified using random, single-use, expiring tokens bound to account, purpose and intended address before verified-email privileges; email changes MUST require reauthentication and notifications to old and new addresses.
- **Rationale:** Mailbox claims are not ownership.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — The intended unexpired token verifies exactly one intended address. Negative — Reused, expired, wrong-purpose or wrong-account tokens fail without changing identity.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTH-005 — Security notifications

- **Requirement:** Authentication factor changes, recovery, password/email changes and suspicious sign-ins MUST generate privacy-preserving security notifications without credentials, with an independent incident-report path.
- **Rationale:** Users can detect unauthorized changes.
- **Priority:** P1.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Each successful factor change produces one durable notification event. Negative — A notification contains no password, OTP, recovery code, session ID or token.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-MFA-001 — WebAuthn policy enforcement

- **Requirement:** WebAuthn MUST be the primary MFA method, with user verification required and server-validated; privileged identities MUST use MFA, company-required MFA MUST apply to every company operation, and ordinary-user MFA MAY be optional only where all applicable policies permit. A qualifying UV WebAuthn ceremony MAY satisfy multiple factors only under an approved assurance policy.
- **Rationale:** Policy cannot rely on frontend prompts.
- **Priority:** P0.
- **Component / trust boundary:** Authenticator/browser → identity.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A qualifying UV ceremony satisfies the applicable policy. Negative — Password-only, UV=false, alternate endpoints and tenant switching cannot bypass mandatory MFA.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-MFA-002 — Challenge and origin validation

- **Requirement:** WebAuthn registration and authentication MUST use cryptographically random, expiring, atomically single-use challenges bound to account/session and ceremony; RP ID, exact allowed origin, ceremony type, signature, credential ownership and required flags MUST be verified.
- **Rationale:** Replay and origin confusion can impersonate users.
- **Priority:** P0.
- **Component / trust boundary:** Authenticator/browser → identity.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A valid intended ceremony succeeds once. Negative — Wrong origin/RP/type/account, expired/replayed challenge and invalid signature each fail.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-MFA-003 — Safe authenticator registration

- **Requirement:** Adding authenticators MUST require a recent approved authentication or narrowly scoped initial-enrollment flow, account-bound challenges and policy validation; enrollment-only sessions MUST NOT access normal protected resources.
- **Rationale:** An attacker must not enroll their own factor.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authenticated owner adds a second approved authenticator. Negative — Another user, CSRF request or password-only session lacking required assurance cannot add a factor.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-MFA-004 — Multiple factors and removal

- **Requirement:** Accounts MUST support multiple approved authenticators; removal MUST require step-up, revoke the removed credential immediately, notify the owner and preserve the minimum required assurance or enter restricted recovery.
- **Rationale:** Loss tolerance must not weaken policy.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A user with two approved factors removes one after step-up. Negative — Removing the last required factor or using a removed factor for a new ceremony fails.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-MFA-005 — Privileged authenticator assurance

- **Requirement:** An approved policy MUST define privileged assurance, distinguish synced and hardware-bound credentials, and require hardware-bound credentials where justified and enforceable using verified evidence; unprovable assurance MUST be denied for restricted roles rather than inferred from marketing or UV alone.
- **Rationale:** UV does not prove non-exportable key storage.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A credential meeting the documented evidence policy accesses its permitted privileged function. Negative — An unsupported or synced credential cannot satisfy a hardware-bound-only policy.
- **Limitations / residual risk:** Attestation availability, privacy and authenticator compatibility constrain enforceability.

### SEC-MFA-006 — Biometric and PIN privacy

- **Requirement:** The ERP backend MUST NOT collect or store biometric templates, biometric samples or device PINs; it MUST store only necessary credential public material and verification metadata.
- **Rationale:** User verification belongs on the authenticator.
- **Priority:** P0.
- **Component / trust boundary:** Authenticator → backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Registration persists a public key and approved metadata only. Negative — Requests and logs containing biometric/PIN fields fail data-flow review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-MFA-007 — Conditional TOTP

- **Requirement:** TOTP MAY be an alternative only where the applicable assurance policy permits; secrets MUST be encrypted with separately managed keys, enrollment verified before activation, attempts throttled and accepted time steps consumed atomically to prevent replay across concurrent nodes.
- **Rationale:** Shared-secret OTPs are phishable and replayable.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — One valid unused time step authenticates once under an allowed policy. Negative — Concurrent reuse, revoked secrets and TOTP under a WebAuthn-only policy fail.
- **Limitations / residual risk:** TOTP is not phishing resistant and cannot satisfy hardware-bound WebAuthn assurance.

### SEC-SESSION-001 — Architecture selection

- **Requirement:** Use native Spring Security server-side authentication sessions backed by Spring Session Redis, with PostgreSQL-authoritative account, membership, company, entitlement and revocation checks; ADR-0004 is accepted. Do not introduce browser JWT/DPoP, public-client refresh tokens, popup grants or redundant authorization servers.
- **Rationale:** The monolith does not require distributed authentication.
- **Priority:** P0.
- **Component / trust boundary:** Browser ↔ modular monolith.
- **Responsible phase:** P4-DESIGN (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Foundation source and owner verification](phase04-authentication-foundation.md); new tests are not executed. Remaining aspects are release gates.
- **Verification method:** ADR review.
- **Measurable acceptance criteria:** Positive — A reviewed ADR selects mechanisms, storage, failure semantics and conditional requirement applicability. Negative — Implementation starts without an ADR or adds unnecessary service boundaries: review fails.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SESSION-002 — Session identity and fixation

- **Requirement:** Server-side sessions MUST use opaque CSPRNG identifiers with at least 128 bits of entropy, rotate identifiers after authentication and privilege/assurance changes, and retain server-side identity, assurance and expiry state.
- **Rationale:** Predictability and fixation enable impersonation.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Login rotates the identifier while retaining only intended application state. Negative — A pre-login fixed identifier cannot access the authenticated session.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SESSION-003 — Idle and absolute expiry

- **Requirement:** Sessions MUST reject at the idle or absolute deadline: ordinary 30 minutes idle/12 hours absolute, privileged 15 minutes/8 hours, step-up freshness 5 minutes. Activity MUST NOT extend absolute age. Privileged authentication remains blocked until required MFA exists.
- **Rationale:** Long-lived authority enlarges theft exposure.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A session inside both configured deadlines is accepted. Negative — At either exact deadline access is rejected; activity cannot extend absolute lifetime.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SESSION-004 — Durable revocation and no resurrection

- **Requirement:** Logout, individual session revocation and global account revocation MUST invalidate authority durably before success is acknowledged; expired/revoked authority MUST NOT resurrect after Redis eviction, restart, stale PostgreSQL reads, failover, backup restoration or concurrent requests.
- **Rationale:** Deleting a cache entry alone is not durable revocation.
- **Priority:** P0.
- **Component / trust boundary:** Backend ↔ PostgreSQL/Redis.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An active session works before a committed revocation. Negative — The same credential remains denied after cache loss, failover and restoration.
- **Limitations / residual risk:** Already committed operations cannot be undone by logout; in-flight handling needs an explicit bound in the ADR.

### SEC-SESSION-005 — PostgreSQL and Redis security consistency

- **Requirement:** PostgreSQL writer state MUST decide current account, company, entitlement, membership, versions and durable revocation on every authenticated request. Redis MUST store HttpSession without independently authorizing stale/revoked state. Commit grant creation before context save and revocation before logout success; deny unknown authority and reconcile restores without assuming cross-store atomicity.
- **Rationale:** Caches and database transactions are not atomic together.
- **Priority:** P0.
- **Component / trust boundary:** Backend ↔ security state.
- **Responsible phase:** P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Failure injection shows committed revocation wins over every stale cache copy. Negative — A Redis miss, partial write or PostgreSQL outage cannot create or extend authority.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SESSION-006 — Session management and containment

- **Requirement:** Users MUST be able to list and individually revoke their own active sessions/devices and revoke all sessions; suspicious reuse and assurance changes MUST trigger auditable risk-based step-up or revocation using defined signals rather than IP address alone.
- **Rationale:** Visibility and containment reduce hijacking harm.
- **Priority:** P1.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An owner lists their sessions and revokes one without revoking an unrelated account. Negative — A different account cannot list/revoke them; suspicious use cannot bypass required step-up.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SESSION-007 — Cookie isolation

- **Requirement:** Production session cookies MUST be Secure, HttpOnly, host-only without Domain, Path=/, SameSite=Strict and use the __Host- prefix. Each company hostname MUST have an independent session. SameSite MUST NOT replace native CSRF and exact Origin validation.
- **Rationale:** Cookie theft and sibling subdomains can affect sessions.
- **Priority:** P0.
- **Component / trust boundary:** Browser ↔ origin.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Foundation source and owner verification](phase04-authentication-foundation.md); new tests are not executed. Remaining aspects are release gates.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Production browser inspection confirms host scope and approved flags. Negative — A sibling subdomain cannot set an accepted domain-scoped session cookie.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SESSION-008 — Failure and theft response

- **Requirement:** Required identity, revocation, tenant and policy infrastructure failures MUST deny protected access; stolen session cookies MUST be contained through bounded lifetime, step-up, anomaly response and revocation, with authentication credentials redacted from telemetry and responses.
- **Rationale:** HttpOnly cannot stop replay of a stolen cookie.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Valid state permits an authorized request and an incident can revoke its session. Negative — Unavailable required state and a revoked stolen cookie are denied.
- **Limitations / residual risk:** A valid stolen bearer cookie may work until expiry, revocation or an assurance check; malicious same-origin code can still issue requests.

### SEC-TOKEN-001 — Conditional JWT validation

- **Requirement:** Retired for the approved browser session architecture; no browser access JWT, refresh token or DPoP implementation is required or permitted by this phase. The identifier is retained and must not be reused. Any future external-token integration requires a new ADR.
- **Implementation status:** Retired.
- **Evidence:** [ADR-0004](../adr/0004-authentication-and-session-architecture.md).
- **Verification method:** Confirm no browser-token endpoints/dependencies or credential browser storage are introduced.

### SEC-TOKEN-002 — Key lifecycle and short lifetimes

- **Requirement:** Retired for the approved browser session architecture; no browser access JWT, refresh token or DPoP implementation is required or permitted by this phase. The identifier is retained and must not be reused. Any future external-token integration requires a new ADR.
- **Implementation status:** Retired.
- **Evidence:** [ADR-0004](../adr/0004-authentication-and-session-architecture.md).
- **Verification method:** Confirm no browser-token endpoints/dependencies or credential browser storage are introduced.

### SEC-TOKEN-003 — Opaque refresh protection

- **Requirement:** Retired for the approved browser session architecture; no browser access JWT, refresh token or DPoP implementation is required or permitted by this phase. The identifier is retained and must not be reused. Any future external-token integration requires a new ADR.
- **Implementation status:** Retired.
- **Evidence:** [ADR-0004](../adr/0004-authentication-and-session-architecture.md).
- **Verification method:** Confirm no browser-token endpoints/dependencies or credential browser storage are introduced.

### SEC-TOKEN-004 — Atomic rotation and concurrency

- **Requirement:** Retired for the approved browser session architecture; no browser access JWT, refresh token or DPoP implementation is required or permitted by this phase. The identifier is retained and must not be reused. Any future external-token integration requires a new ADR.
- **Implementation status:** Retired.
- **Evidence:** [ADR-0004](../adr/0004-authentication-and-session-architecture.md).
- **Verification method:** Confirm no browser-token endpoints/dependencies or credential browser storage are introduced.

### SEC-TOKEN-005 — Conditional standardized DPoP

- **Requirement:** Retired for the approved browser session architecture; no browser access JWT, refresh token or DPoP implementation is required or permitted by this phase. The identifier is retained and must not be reused. Any future external-token integration requires a new ADR.
- **Implementation status:** Retired.
- **Evidence:** [ADR-0004](../adr/0004-authentication-and-session-architecture.md).
- **Verification method:** Confirm no browser-token endpoints/dependencies or credential browser storage are introduced.

### SEC-BROWSER-001 — XSS-safe rendering

- **Requirement:** React rendering MUST use safe text binding and context-appropriate output encoding; stored, reflected and DOM input MUST NOT reach unsafe HTML/script/URL sinks. Necessary rich HTML MUST use a reviewed maintained sanitizer with strict allowed content.
- **Rationale:** XSS uses the legitimate browser’s authority.
- **Priority:** P0.
- **Component / trust boundary:** Untrusted content → React/DOM.
- **Responsible phase:** FRONTEND (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Untrusted text is displayed as text and permitted sanitized markup works. Negative — Stored, reflected and DOM payloads execute zero scripts or unsafe URLs.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BROWSER-002 — Browser execution policy

- **Requirement:** Production MUST enforce a strict nonce/hash CSP without unsafe script exceptions; Trusted Types SHOULD protect sinks where supported. Policy rollout MUST monitor violations without exposing secrets.
- **Rationale:** Limit execution when injection occurs.
- **Priority:** P0.
- **Component / trust boundary:** Origin → browser.
- **Responsible phase:** FRONTEND + INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Intended scripts work under enforced policy in supported browsers. Negative — Unapproved inline/remote scripts are blocked and produce redacted violations.
- **Limitations / residual risk:** CSP/Trusted Types depend on correct policy and browser support; allowed scripts can still be malicious.

### SEC-BROWSER-003 — Third-party JavaScript

- **Requirement:** Third-party scripts MUST be inventoried, minimized and explicitly approved; immutable external resources MUST use Subresource Integrity where applicable, and npm dependency changes MUST receive supply-chain review.
- **Rationale:** Approved scripts have same-origin execution power.
- **Priority:** P0.
- **Component / trust boundary:** Dependencies/CDN → browser.
- **Responsible phase:** FRONTEND + SUPPLY (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An approved pinned integrity-checked asset loads. Negative — A modified integrity-checked asset or unapproved third-party script is blocked.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BROWSER-004 — Browser isolation headers

- **Requirement:** Production MUST prevent clickjacking using CSP frame-ancestors and compatible legacy controls, restrict powerful features with Permissions Policy, and SHOULD apply compatible cross-origin resource/opener/embedder policies after testing login, payments and downloads.
- **Rationale:** Embedding and browser features widen attack paths.
- **Priority:** P1.
- **Component / trust boundary:** Browser ↔ other origins.
- **Responsible phase:** FRONTEND + INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Required payment/login flows pass compatibility tests with restrictive headers. Negative — An unauthorized framing origin and disallowed feature use fail.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BROWSER-005 — CSRF, CORS and origin

- **Requirement:** Cookie-authenticated state changes MUST use robust CSRF tokens and server-side Origin validation, prohibit state changes on safe methods, and use exact approved CORS origins/methods/headers without credentialed wildcard or arbitrary reflection.
- **Rationale:** CORS does not authenticate callers or prevent all CSRF.
- **Priority:** P0.
- **Component / trust boundary:** Other origins → backend.
- **Responsible phase:** P4-IMPLEMENT + FRONTEND (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Foundation source and owner verification](phase04-authentication-foundation.md); new tests are not executed. Remaining aspects are release gates.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized same-origin mutation with valid CSRF evidence succeeds. Negative — Cross-origin mutation, missing/invalid CSRF token, null/unapproved origin and credentialed wildcard fail.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BROWSER-006 — Sensitive browser storage

- **Requirement:** Authentication credentials MUST NOT be stored in localStorage; sessionStorage, IndexedDB, service-worker caches and URLs MUST NOT hold bearer credentials or unnecessary sensitive data. BFF and HttpOnly cookies MUST be evaluated with their same-origin request limitations.
- **Rationale:** Browser storage and URLs expose durable secrets.
- **Priority:** P0.
- **Component / trust boundary:** Browser storage/runtime.
- **Responsible phase:** P4-DESIGN → FRONTEND (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Approved authentication works with the selected cookie/BFF design. Negative — Inspection of browser storage, history and frontend logs finds zero authentication secrets.
- **Limitations / residual risk:** CSP and HttpOnly cannot fully prevent malicious code executing in a legitimate authenticated browser from exercising that user’s authority.

### SEC-AUTHZ-001 — Server RBAC and fine permissions

- **Requirement:** Backend application boundaries MUST enforce company-specific RBAC and fine-grained permissions at function, object and field levels; authentication alone MUST NOT authorize any resource.
- **Rationale:** A valid identity does not imply all permissions.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized role reads permitted fields of its own object. Negative — Direct API calls to a forbidden function/object/field fail without data leakage.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTHZ-002 — Ownership and mass assignment

- **Requirement:** Every resource operation MUST verify ownership and authorized company scope, including nested IDs, exports and bulk operations; request DTOs MUST allowlist writable fields and reject privilege, ownership or workflow-state mass assignment.
- **Rationale:** IDOR/BOLA and hidden fields bypass UI controls.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An owner updates only permitted fields. Negative — Changing an object ID, owner ID or role field grants no access and persists no forbidden changes.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTHZ-003 — Tenant membership and switching

- **Requirement:** Authenticated tenant resolution MUST verify current company membership and policy before binding context; tenant switching MUST start a fresh authorized scope, invalidate incompatible state and never trust a client tenant identifier alone.
- **Rationale:** A valid UUID is not tenant authority.
- **Priority:** P0.
- **Component / trust boundary:** Identity/membership → tenant context.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Foundation source and owner verification](phase04-authentication-foundation.md); new tests are not executed. Remaining aspects are release gates.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A member of A and B can deliberately switch after both checks. Negative — A member of A cannot select B through header, URL, body, cookie or token claim.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTHZ-004 — Entitlement gating

- **Requirement:** Server-side entitlement checks MUST gate subscribed operations and provisioning after verified payment; expiry MUST restrict applicable access while preserving customer data under retention policy.
- **Rationale:** Frontend billing state is forgeable.
- **Priority:** P0.
- **Component / trust boundary:** Billing → business modules.
- **Responsible phase:** BILLING (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Foundation source and owner verification](phase04-authentication-foundation.md); new tests are not executed. Remaining aspects are release gates.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Verified payment plus an active entitlement permits the paid operation. Negative — A client paid flag or expired subscription cannot provision or invoke a paid operation; expiry does not delete company data.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTHZ-005 — Permission revocation bound

- **Requirement:** Role, membership and assurance revocation MUST take effect immediately at authoritative checks or within an explicitly approved maximum propagation bound; proposed bound is 60 seconds for ordinary permissions and immediate authoritative checks for privileged mutations. Unknown state MUST deny access.
- **Rationale:** Cached claims outlive permissions.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Foundation source and owner verification](phase04-authentication-foundation.md); new tests are not executed. Remaining aspects are release gates.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Current grants permit an operation within the approved policy. Negative — A revoked user is denied by the approved deadline across nodes and caches.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUTHZ-006 — Transaction-bound step-up

- **Requirement:** Sensitive mutations MUST require fresh Step-Up WebAuthn and a single-use, expiring approval bound to actor, company, action, target, amount/payee or other material fields and operation digest; changing those fields MUST invalidate approval.
- **Rationale:** A generic login cannot authorize a changed payment.
- **Priority:** P0.
- **Component / trust boundary:** Authenticator → sensitive transaction.
- **Responsible phase:** P4-IMPLEMENT + ERP (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An unchanged approved transaction commits once. Negative — Changed amount, target, company, actor, expiry or reused approval prevents mutation.
- **Limitations / residual risk:** WebAuthn proves an authenticator ceremony, not trusted display of business details on a compromised browser.

### SEC-AUTHZ-007 — Independent critical approval

- **Requirement:** Selected critical operations MUST require two distinct authorized people under a documented threshold/operation policy, prevent self-approval and conflicting roles, and revalidate both grants and operation state at execution.
- **Rationale:** Reduce unilateral fraud and insider harm.
- **Priority:** P0.
- **Component / trust boundary:** Requester ↔ approver ↔ application.
- **Responsible phase:** ERP + OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A requester and independent authorized approver complete one unchanged transaction. Negative — Same-person approval, revoked approver or changed transaction cannot commit.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-API-001 — Strict API contracts

- **Requirement:** Versioned /api/v1 endpoints MUST accept strict bounded DTO/schema inputs, reject invalid types/ranges and unknown writable fields, return response DTOs with consistent non-sensitive errors, restrict HTTP methods and paginate large collections.
- **Rationale:** Ambiguity and unbounded output expose internals.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Valid DTOs return documented statuses and bounded pages. Negative — Unknown fields, invalid values, unsupported methods and excessive page sizes fail without persistence entities or stack traces.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-API-002 — Query and injection safety

- **Requirement:** Database values MUST use parameterized queries; dynamic identifiers MUST come only from reviewed validated internal mappings; untrusted SQL fragments MUST NOT be accepted.
- **Rationale:** Input must not become executable SQL.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** TenantConnectionProvider uses parameterized pg_catalog.set_config; broad API/query coverage is future.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Quoted text is stored as data. Negative — SQL injection payloads neither change query structure nor access another schema.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-API-003 — SSRF and egress

- **Requirement:** Server-initiated requests MUST validate destinations against purpose-specific allowlists, prevent private/loopback/link-local/metadata access, revalidate DNS/redirect targets and use bounded timeouts and response sizes; network egress SHOULD enforce the same policy.
- **Rationale:** URLs can reach privileged internal services.
- **Priority:** P0.
- **Component / trust boundary:** Application → outbound network.
- **Responsible phase:** P4-IMPLEMENT + INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An approved external endpoint responds within limits. Negative — Private-IP, redirect and DNS-rebinding destinations are denied.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-API-004 — Resource abuse limits

- **Requirement:** Endpoints MUST enforce request/body/upload sizes, timeouts, bounded pagination, tenant/user resource quotas and rate/concurrency limits with trusted proxy handling; limits MUST protect CPU, memory, connections and background work.
- **Rationale:** A valid user can exhaust shared resources.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A request at documented limits succeeds. Negative — Oversized payloads and above-quota parallel traffic fail within configured budgets without starving another tenant.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-API-005 — Atomic and idempotent operations

- **Requirement:** Mutations requiring retry safety MUST bind idempotency keys to actor/company/action and payload digest, persist outcomes atomically with business state and enforce transaction/concurrency invariants using constraints or locking.
- **Rationale:** Retries and races can duplicate financial effects.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT + ERP/BILLING (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A retry returns the original authorized outcome once. Negative — Same key with a changed payload is rejected and concurrent duplicate commands create one effect.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-API-006 — Secure uploads

- **Requirement:** Uploads MUST require authorization and tenant ownership, bounded count/size, content-type/signature validation, generated storage names, non-executable private storage and scanning/quarantine as risk requires; downloads MUST reauthorize and safely set content headers.
- **Rationale:** Files can execute code or leak company data.
- **Priority:** P0.
- **Component / trust boundary:** Client file → storage → download.
- **Responsible phase:** ERP (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An allowed scanned file is downloadable by its authorized owner. Negative — Executable/polyglot, traversal, oversized and foreign-tenant downloads fail.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BUSINESS-001 — Server financial calculations

- **Requirement:** Sales, invoice and pricing modules MUST calculate prices, taxes, totals and discounts from authorized server records using explicit rounding/currency rules; unauthorized discounts/refunds and client-controlled workflow state MUST be rejected.
- **Rationale:** Client totals are not financial evidence.
- **Priority:** P0.
- **Component / trust boundary:** Client → financial domain.
- **Responsible phase:** ERP-SALES/BILLING (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized sale matches approved catalog and rounding rules. Negative — Modified totals, negative quantity abuse and unauthorized refund/discount persist no effect.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BUSINESS-002 — Authentic payment and replay safety

- **Requirement:** Billing MUST verify payment webhook signatures over original bytes, provider/account/environment and freshness, process unique event IDs idempotently and reconcile authoritative payment state before entitlement changes; frontend confirmation MUST NOT provision resources.
- **Rationale:** Replayed or fabricated events can grant unpaid service.
- **Priority:** P0.
- **Component / trust boundary:** Payment provider → billing.
- **Responsible phase:** BILLING (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A verified entitled payment event changes state exactly once. Negative — Invalid signature, stale/replayed event and client confirmation grant no additional entitlement.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BUSINESS-003 — Provisioning entitlement

- **Requirement:** Provisioning MUST require verified payment and current server entitlement, use validated tenant identities and restricted privileges, and be idempotent and recoverable without granting authority on partial failure.
- **Rationale:** Resource creation is a privileged paid transition.
- **Priority:** P0.
- **Component / trust boundary:** Billing → provisioner → PostgreSQL.
- **Responsible phase:** BILLING → TENANT-ACTIVATION (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — One verified entitlement provisions one intended company safely on retry. Negative — An unpaid or forged entitlement and a partial failure cannot create usable unauthorized resources.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-BUSINESS-004 — Inventory integrity

- **Requirement:** Inventory and purchasing MUST enforce tenant-owned references, permitted stock transitions and documented negative-stock rules transactionally, preventing oversell and duplicate movements under concurrency.
- **Rationale:** Concurrent valid requests can corrupt stock.
- **Priority:** P0.
- **Component / trust boundary:** API/jobs → inventory domain.
- **Responsible phase:** ERP-INVENTORY/PURCHASING (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Allowed receipts/issues preserve the stock ledger invariant. Negative — Concurrent competing issues cannot violate configured stock rules or cross tenant references.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-TENANT-001 — Scoped context lifecycle

- **Requirement:** Tenant execution MUST use canonical internal UUIDs in owned, non-inheritable thread-local scopes, reject in-scope company replacement, close in LIFO order on the owning thread and clear after every outcome.
- **Rationale:** Thread reuse must not retain another company.
- **Priority:** P0.
- **Component / trust boundary:** Execution scope → tenant operations.
- **Responsible phase:** P3-MAINTAIN (owner defined above).
- **Implementation status:** Implemented.
- **Evidence:** [TenantContextHolder](../../Backend/src/main/java/com/saaserp/erp/shared/tenant/application/TenantContextHolder.java); TenantContextHolderTest; TenantIdTest.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A then B on the same worker resolve to their own identities after cleanup. Negative — Missing context, foreign-thread close and replacement fail without changing the active scope.
- **Limitations / residual risk:** Any trusted internal caller can bind an identity on an empty thread; this is lifecycle integrity, not membership authorization.

### SEC-TENANT-002 — Strict schema identifiers

- **Requirement:** Schema names MUST derive only from validated TenantId through TenantSchemaIdentifier, respecting its fixed lowercase tenant naming contract and PostgreSQL byte limits; arbitrary schema input MUST NOT enter SQL.
- **Rationale:** Prevent identifier injection and alias ambiguity.
- **Priority:** P0.
- **Component / trust boundary:** Tenant identity → schema routing.
- **Responsible phase:** P3-MAINTAIN (owner defined above).
- **Implementation status:** Implemented.
- **Evidence:** [TenantSchemaIdentifier](../../Backend/src/main/java/com/saaserp/erp/shared/tenant/domain/TenantSchemaIdentifier.java); TenantSchemaIdentifierTest; TenantConnectionProviderTest.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Canonical UUID produces the expected deterministic 39-character schema name. Negative — Raw schema names, reserved names, malformed or noncanonical UUIDs fail before borrowing.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-TENANT-003 — Fail-closed routing foundation

- **Requirement:** Hibernate tenant routing MUST require active context/identifier agreement, restricted runtime identity and existing accessible schema, verify the exact effective path and MUST NOT fall back to public, platform, billing or another tenant; central persistence MUST remain separate.
- **Rationale:** A failed route must not silently select another namespace.
- **Priority:** P0.
- **Component / trust boundary:** Hibernate → PostgreSQL.
- **Responsible phase:** P3-MAINTAIN → TENANT-ACTIVATION (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** TenantIdentifierResolver, TenantConnectionProvider; TenantRoutingDatabaseIntegrationTest; [routing activation limits](../architecture/hibernate-tenant-routing.md).
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A permitted fixture tenant has exactly its intended effective schema path. Negative — Missing, inaccessible or mismatched tenants fail before customer queries.
- **Limitations / residual risk:** Routing/provider code exists and has tests, but no production tenant persistence unit, tenant entities or authenticated resolver is activated.

### SEC-TENANT-004 — Connection session cleanup

- **Requirement:** TenantConnectionProvider MUST verify idle transaction state and runtime identity, DISCARD ALL and verify catalog-only baseline/JDBC state before reuse, reject unsupported ownership violations and evict owned unsafe leases or stop the pool when ownership is uncertain; cleanup MUST NOT commit caller transactions.
- **Rationale:** Search-path reset alone leaves session resources and stale state.
- **Priority:** P0.
- **Component / trust boundary:** Tenant JDBC lease → HikariCP.
- **Responsible phase:** P3-MAINTAIN (owner defined above).
- **Implementation status:** Implemented.
- **Evidence:** [PostgresSessionState](../../Backend/src/main/java/com/saaserp/erp/shared/tenant/infrastructure/persistence/PostgresSessionState.java); TenantConnectionProviderTest; TenantRoutingDatabaseIntegrationTest.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Same physical connection releases with pg_catalog, pg_temp baseline and no prior tenant state. Negative — Open/failed transactions, failed reset, duplicate or stale releases never return an unverified owned connection as reusable.
- **Limitations / residual risk:** Raw JDBC escape/direct proxy close can bypass provider cleanup; trusted internal callers must obey lease ownership. Driver/network failures can require pool recovery.

### SEC-TENANT-005 — Tenant Redis namespaces

- **Requirement:** Tenant cache operations MUST derive hich:tenant:<uuid>:<namespace>:<key> from current context on every call, validate components, expose no arbitrary tenant/raw-key or bulk clear API, require atomic bounded TTL writes and use safe string serialization.
- **Rationale:** Key collisions and broad deletion can cross companies.
- **Priority:** P0.
- **Component / trust boundary:** TenantCache port → Redis.
- **Responsible phase:** P3-MAINTAIN (owner defined above).
- **Implementation status:** Implemented.
- **Evidence:** TenantCacheKey, RedisTenantCache, RedisTenantCacheConfig; TenantCacheKeyTest; RedisTenantCacheTest; TenantRedisCacheIntegrationTest.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A and B store/read distinct values for the same namespace/key with TTL 1ms–24h in whole milliseconds. Negative — Missing context, delimiter/glob injection, invalid TTL and A deleting B’s value are rejected or impossible through the port.
- **Limitations / residual risk:** Prefixes and shared Redis credentials are routing conventions, not server-enforced tenant authorization; eviction may precede TTL.

### SEC-TENANT-006 — Async tenant authority

- **Requirement:** Every asynchronous job MUST independently establish authenticated/delegated company authority, revalidate applicable membership/entitlement on execution, bind and close its own context and never inherit request thread-local state implicitly.
- **Rationale:** Jobs may run after permissions change.
- **Priority:** P0.
- **Component / trust boundary:** Request/queue → worker.
- **Responsible phase:** TENANT-ACTIVATION + ERP (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** TenantContextHolder uses ordinary ThreadLocal and tests non-inheritance; no production job authorization boundary exists.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized job binds and closes its own tenant scope. Negative — Missing/stale authority and reused worker state cannot authorize another company.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-TENANT-007 — Database-enforced company privileges

- **Requirement:** A future ADR MUST design stronger database-enforced tenant privileges within the existing single database, including controlled role/credential reach and safe pooling/provisioning; shared runtime routing MUST NOT be described as database-enforced per-tenant authorization.
- **Rationale:** Qualified SQL can bypass search_path when shared credentials permit it.
- **Priority:** P0.
- **Component / trust boundary:** Compromised backend/runtime → PostgreSQL.
- **Responsible phase:** TENANT-HARDENING (owner defined above).
- **Implementation status:** Deferred.
- **Evidence:** No implementation evidence identified.
- **Verification method:** ADR review, then real restricted-role adversarial SQL tests.
- **Measurable acceptance criteria:** Positive — Approved design demonstrates denied cross-tenant fully qualified SQL under its selected model. Negative — A design claiming search_path alone blocks a compromised shared-runtime process fails review.
- **Limitations / residual risk:** Current shared runtime credentials can reach multiple granted tenant schemas; no containment guarantee against backend-process compromise.

### SEC-DATA-001 — Restricted database roles

- **Requirement:** Database runtime and migration credentials MUST remain separate, deny superuser/role/database creation, replication and RLS bypass, deny runtime DDL/history modification and restrict environment connections; production grants MUST be explicitly provisioned and verified.
- **Rationale:** Compromised runtime must not become a database administrator.
- **Priority:** P0.
- **Component / trust boundary:** Runtime/migrator → PostgreSQL.
- **Responsible phase:** P3-MAINTAIN + INFRA (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Role setup](../../infrastructure/postgres/00_create_roles.sql), configure_application_database.sql; DatabaseMigrationIntegrationTest. These are dev/test scripts, not verified production state.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Restricted runtime can execute only explicitly granted business DML. Negative — Runtime cannot CREATE schema/table, modify Flyway history, assume a migrator or cross-connect dev/test.
- **Limitations / residual risk:** Runtime permissions still span every granted tenant; separate migrator secrets remain process-accessible under current startup configuration.

### SEC-DATA-002 — Migration integrity

- **Requirement:** Schema changes MUST use immutable versioned Flyway migrations separated into allowlisted platform, billing and future tenant streams, with no recursive unrestricted tenant execution or automatic Hibernate updates; explicit guarded bootstrap MUST NOT become a startup privilege fallback.
- **Rationale:** Migration privileges are powerful and histories are evidence.
- **Priority:** P0.
- **Component / trust boundary:** Migration resources → PostgreSQL.
- **Responsible phase:** P3-MAINTAIN (owner defined above).
- **Implementation status:** Implemented.
- **Evidence:** FlywayConfig, CentralSchemaBootstrap, application.properties; CentralSchemaBootstrapTest; DatabaseMigrationIntegrationTest.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Each central stream validates its own unchanged history with ddl-auto=validate. Negative — Changed applied checksum, unsafe bootstrap state or unrestricted tenant location fails validation/review.
- **Limitations / residual risk:** Production tenant migrations/provisioning are not yet implemented; central baseline has independent non-atomic history operations.

### SEC-DATA-003 — Constraints and safe data formats

- **Requirement:** Persistence MUST enforce business invariants with appropriate constraints/transactions and use reviewed bounded serializers without unsafe native/polymorphic deserialization; foreign references MUST remain tenant-authorized.
- **Rationale:** Application checks alone race and serialized input can execute code.
- **Priority:** P0.
- **Component / trust boundary:** Application ↔ database/cache.
- **Responsible phase:** ERP + TENANT-ACTIVATION (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** RedisTenantCacheConfig explicitly uses string serializers; ERP entities and business constraints are not implemented.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Valid domain data round-trips and concurrent writes preserve constraints. Negative — Corrupt relations, oversized serialized input and unsafe type metadata fail without execution.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-DATA-004 — Redis ACL and security-state isolation

- **Requirement:** Production Redis MUST use least-privilege service/environment ACL identities, encrypted private transport and restricted administration; session/revocation state MUST have a separate application contract and protected key/command scope from disposable tenant caches, with a reviewed non-eviction/durable recovery policy. Logical database numbers MUST NOT be treated as security boundaries.
- **Rationale:** Cache eviction or FLUSH-like operations must not restore authority.
- **Priority:** P0.
- **Component / trust boundary:** Business cache ↔ security state/Redis.
- **Responsible phase:** P4-DESIGN + INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Cache credentials cannot access security keys or administrative commands under the approved policy. Negative — Tenant-cache writes, eviction pressure and unauthorized clearing cannot erase authoritative revocation or grant access.
- **Limitations / residual risk:** Existing shared credentials and raw infrastructure clients have broader reach; a shared ACL matching all tenants still cannot isolate one tenant from compromised code.

### SEC-CRYPTO-001 — Sensitive data protection

- **Requirement:** Sensitive fields, TOTP secrets and backup data MUST be encrypted at rest with approved authenticated encryption and managed keys separate from ciphertext; envelope encryption SHOULD be used where justified by classification, rotation or tenant key scope.
- **Rationale:** Storage theft should not directly reveal secrets.
- **Priority:** P0.
- **Component / trust boundary:** Sensitive data → storage/key management.
- **Responsible phase:** P4-IMPLEMENT + INFRA (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Authorized access decrypts with the intended key and authenticated context. Negative — Ciphertext modification, wrong key/context and unauthorized key access fail.
- **Limitations / residual risk:** Encryption at rest does not prevent an authorized compromised application from decrypting accessible data.

### SEC-CRYPTO-002 — Key and secret management

- **Requirement:** Passwords, API keys, signing/encryption keys and service credentials MUST use approved secret injection/storage without source/log exposure, documented owners, least-privilege access, rotation and emergency replacement; production keys MUST NOT be shared with development/test.
- **Rationale:** Secrets connect otherwise separate trust zones.
- **Priority:** P0.
- **Component / trust boundary:** Operator/secret storage → process.
- **Responsible phase:** P4-IMPLEMENT + INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A documented rotation replaces the intended secret without exposing it. Negative — A development secret cannot authenticate to production and repository/log scans find zero live secrets.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-CRYPTO-003 — Cryptographic hygiene

- **Requirement:** Cryptographic operations MUST use maintained standard libraries, approved algorithms and CSPRNGs with explicit key sizes/context binding; custom cryptography MUST NOT be introduced, and algorithm deprecation MUST have an owner and migration path.
- **Rationale:** Correct protocols can fail through weak primitives.
- **Priority:** P0.
- **Component / trust boundary:** Backend.
- **Responsible phase:** P4-IMPLEMENT + SUPPLY (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Code review, crypto inventory and targeted negative tests.
- **Measurable acceptance criteria:** Positive — A cryptographic inventory identifies algorithm, key purpose, owner and rotation. Negative — Non-cryptographic randomness for challenges/tokens and unreviewed primitives fail review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-INFRA-001 — HTTPS and edge protection

- **Requirement:** Production MUST enforce HTTPS with approved TLS and HSTS after domain readiness, use Cloudflare WAF/DDoS controls with monitored rules, encrypt/authenticate edge-to-Nginx origin connections and restrict direct-origin access.
- **Rationale:** The edge is one layer, not the application trust decision.
- **Priority:** P0.
- **Component / trust boundary:** Internet → Cloudflare → Nginx.
- **Responsible phase:** INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Supported HTTPS through the approved edge succeeds with validated origin identity. Negative — Plaintext sensitive traffic and unauthorized direct-origin requests fail.
- **Limitations / residual risk:** WAF rules cannot prevent every application attack; edge outages and bypass mistakes remain possible.

### SEC-INFRA-002 — Proxy protocol integrity

- **Requirement:** Nginx and upstream parsers MUST reject ambiguous framing, conflicting Content-Length/Transfer-Encoding and invalid Host headers, constrain body/header/time limits and trust forwarded identity/protocol/client-IP headers only from configured proxies which overwrite untrusted values.
- **Rationale:** Parser and proxy disagreement enables smuggling and spoofing.
- **Priority:** P0.
- **Component / trust boundary:** Cloudflare/Nginx → Spring.
- **Responsible phase:** INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An approved host and unambiguous request reaches the intended route once. Negative — Smuggling payloads, foreign Host and spoofed forwarding headers create no extra request or rate-limit bypass.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-INFRA-003 — Private services and network limits

- **Requirement:** PostgreSQL and Redis MUST be private, firewall rules MUST permit only needed flows, environments MUST be segmented, and outbound connections MUST be restricted to approved dependencies with reviewed exceptions.
- **Rationale:** Private placement reduces exposed attack paths.
- **Priority:** P0.
- **Component / trust boundary:** Network → data services.
- **Responsible phase:** INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Only approved application/administrative paths reach data services. Negative — Internet and unapproved workloads cannot connect to PostgreSQL/Redis or metadata services.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-INFRA-004 — Container and host hardening

- **Requirement:** Production containers MUST run non-root with minimum Linux capabilities and read-only filesystems where applicable, bounded resources, supported seccomp/AppArmor policies and narrowly scoped writable paths; exceptions MUST be reviewed against applicable CIS guidance.
- **Rationale:** Runtime exploitation should have limited host reach.
- **Priority:** P0.
- **Component / trust boundary:** Container → host.
- **Responsible phase:** INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Application works with approved capabilities and writable mounts only. Negative — Privileged execution, host mounts and writes outside approved paths fail.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-INFRA-005 — Administration and deployment

- **Requirement:** Production administration MUST use named least-privilege SSH/console identities, hardware-backed administrator authentication, restricted entry paths and audited time-bounded elevation; deployments MUST use approved immutable artifacts and protect secret access.
- **Rationale:** Administrators can bypass application controls.
- **Priority:** P0.
- **Component / trust boundary:** Administrator/CI → production.
- **Responsible phase:** INFRA + OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized administrator receives scoped audited temporary access. Negative — Shared password-only administration and unauthorized artifact replacement fail.
- **Limitations / residual risk:** A fully compromised production administrator may alter controls, credentials, data and logs.

### SEC-INFRA-006 — Runtime detection

- **Requirement:** Operations MUST deploy actionable runtime and file integrity monitoring for unexpected processes, configuration/artifact changes, network activity and resource abuse, with owned alerts and tested response thresholds.
- **Rationale:** Build-time scans do not observe runtime compromise.
- **Priority:** P1.
- **Component / trust boundary:** Backend.
- **Responsible phase:** INFRA + OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A controlled unauthorized file change raises the assigned alert within its documented SLA. Negative — Alert suppression or absent telemetry fails operational readiness.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SUPPLY-001 — Branch and workflow authority

- **Requirement:** Protected branches MUST require reviewed changes and mandatory security/build checks; workflows MUST use least-privilege scoped permissions, isolate untrusted PR code from secrets and pin external Actions to reviewed immutable commits.
- **Rationale:** CI is a privileged code-execution boundary.
- **Priority:** P0.
- **Component / trust boundary:** Contributor/Actions → trusted build.
- **Responsible phase:** SUPPLY (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** backend-ci.yml and security-ci.yml set contents: read and persist-credentials: false; Actions use version tags, not immutable SHA pins. Hosted protection settings were not inspected.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A reviewed change with all required checks can merge under inspected repository settings. Negative — A direct protected-branch write, skipped check or untrusted PR secret request fails.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SUPPLY-002 — Security analysis gates

- **Requirement:** CI/release checks MUST include redacted secret scanning, SAST, SCA and infrastructure scanning, plus authorized DAST against a representative isolated environment; SonarCloud applicability/configuration MUST be verified rather than inferred from the intended toolchain.
- **Rationale:** Different analyses find different failure classes.
- **Priority:** P0.
- **Component / trust boundary:** Source/build/test environment.
- **Responsible phase:** SUPPLY (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** security-ci.yml defines Gitleaks history and Trivy dependency/config scans; no SonarCloud or DAST configuration found. Workflow presence is not a successful run result.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Seeded safe fixtures demonstrate each selected gate detects its intended issue. Negative — A verified unacceptable finding or failed/missing required check blocks release.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SUPPLY-003 — Dependency verification

- **Requirement:** Dependencies and downloaded tools MUST use reviewed versions, integrity/checksum or signature verification and controlled sources; lockfiles MUST be maintained where supported and dependency changes reviewed, including transitive npm risks.
- **Rationale:** Pinned versions alone do not authenticate downloaded bytes.
- **Priority:** P0.
- **Component / trust boundary:** Registry/tool download → build.
- **Responsible phase:** SUPPLY (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** Frontend/package-lock.json and Backend/pom.xml exist; workflow scanner archives have pinned versions but no visible checksum verification.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A clean install matches reviewed package-lock and approved artifacts. Negative — Changed download checksum, unexpected lockfile drift or unapproved dependency fails.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SUPPLY-004 — Artifact provenance and deployment

- **Requirement:** Release builds MUST produce an SBOM, signed artifacts and verifiable build provenance tied to reviewed source; deployment MUST verify digest/signature/provenance and explicit authorization before running an immutable production artifact.
- **Rationale:** Source review is ineffective if deployment bytes differ.
- **Priority:** P0.
- **Component / trust boundary:** Build → artifact → production.
- **Responsible phase:** SUPPLY + INFRA (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized deployment verifies the intended artifact and provenance. Negative — A modified, unsigned, untrusted-source or unauthorized artifact is refused.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-SUPPLY-005 — Vulnerability and patch lifecycle

- **Requirement:** Security findings MUST have owners, severity/exploitability triage, approved remediation deadlines and expiry for exceptions; production dependencies, images and hosts MUST receive verified patches and regression checks. Proposed deadlines are 24h containment for actively exploited critical issues, 7 days critical and 30 days high remediation, pending operational approval.
- **Rationale:** Scans without remediation leave known exposure.
- **Priority:** P1.
- **Component / trust boundary:** Backend.
- **Responsible phase:** SUPPLY + OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Finding-tracker audit, release-gate exercise and patch regression evidence.
- **Measurable acceptance criteria:** Positive — A finding is tracked through verified fix within its approved SLA. Negative — Expired exceptions and unowned overdue release-blocking findings fail review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUDIT-001 — Structured security events

- **Requirement:** Authentication outcomes, authorization failures, policy/privilege changes, tenant membership changes, privileged operations, suspicious sessions and revocations MUST emit structured security events with trusted actor/company context, outcome, timestamp and validated correlation IDs.
- **Rationale:** Investigation needs attributable transitions.
- **Priority:** P0.
- **Component / trust boundary:** Security/business decisions → audit.
- **Responsible phase:** P4-IMPLEMENT + ERP (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — Each defined event type produces a correlated event with intended context. Negative — Forged newline/correlation input cannot inject or attribute an event to another tenant.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUDIT-002 — No credential exposure

- **Requirement:** Logs, traces, error responses, analytics and notifications MUST redact passwords, cookies, Authorization values, OTPs, TOTP secrets, recovery codes, refresh tokens and authentication challenges that convey authority; sensitive customer data MUST be minimized and access restricted.
- **Rationale:** Audit storage must not become a credential source.
- **Priority:** P0.
- **Component / trust boundary:** All inputs → telemetry/responses.
- **Responsible phase:** P4-IMPLEMENT + OPERATIONS (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** application.properties suppresses detailed server errors, SQL logging and health details; tenant/cache components add no logs. No end-to-end credential-redaction tests or authentication handlers exist.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An authorized investigator sees a useful redacted security event. Negative — Canary secrets sent through success and error flows appear zero times in captured telemetry/responses.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-AUDIT-003 — Audit preservation and alerting

- **Requirement:** Audit storage MUST restrict read/write/delete privileges, enforce approved retention and tamper-evidence, preserve independent copies beyond ordinary application administrator reach where feasible, and alert on security events, gaps and tampering under documented SLAs.
- **Rationale:** A compromised process may erase local evidence.
- **Priority:** P0.
- **Component / trust boundary:** Application → independently preserved audit.
- **Responsible phase:** INFRA + OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A controlled security event reaches independently preserved storage and its assigned alert. Negative — Unauthorized log deletion/modification or a telemetry gap is detected within the approved interval.
- **Limitations / residual risk:** Tamper-evidence cannot guarantee truth if all producers and administrative trust roots are compromised.

### SEC-RECOVERY-001 — Account recovery assurance

- **Requirement:** Recovery MUST use approved identity/ownership evidence, single-use expiring scoped tokens and throttling; recovery codes MUST be high-entropy, hashed and atomically single-use. Recovery MUST NOT silently bypass company/privileged MFA or hardware assurance; lost-factor accounts MUST stay restricted until required assurance is re-established, with notifications and session revocation.
- **Rationale:** Recovery is an alternate authentication route.
- **Priority:** P0.
- **Component / trust boundary:** Claimant/support → identity.
- **Responsible phase:** P4-IMPLEMENT (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** [Stage 03 source, coverage and owner verification](phase04-stage03-mfa-account-protection.md). Baseline 280 passing tests are owner-reported; new source/tests are unexecuted. Hardware assurance, external delivery and other named release gates remain incomplete.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A legitimate owner recovers into the policy-required restricted/re-enrollment flow. Negative — Replayed codes, email-only privileged reset and support-assisted policy bypass fail.
- **Limitations / residual risk:** Mailbox compromise and fraudulent support evidence remain risks; recovery requires an approved escalation policy.

### SEC-RECOVERY-002 — Incident containment and recovery

- **Requirement:** Operations MUST maintain and exercise incident triage, scope assessment, account/session/token containment, credential/key rotation, tenant protection, evidence preservation and clean recovery runbooks with named owners and communication duties.
- **Rationale:** Containment must remain usable during compromise.
- **Priority:** P0.
- **Component / trust boundary:** Detection → incident operations.
- **Responsible phase:** OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — An exercise revokes affected authority, preserves evidence and restores a verified clean service. Negative — Recovery cannot reuse known-compromised keys or silently restore revoked access.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-RECOVERY-003 — Backups, PITR and restores

- **Requirement:** Production MUST maintain independent encrypted backups and point-in-time recovery with protected keys, approved RPO/RTO and retention, and scheduled isolated restore verification; restoration MUST reconcile durable revocations and tenant integrity before serving traffic. Proposed RPO 15 minutes/RTO 4 hours requires operations approval and tested capacity.
- **Rationale:** An untested backup is not demonstrated recovery.
- **Priority:** P0.
- **Component / trust boundary:** PostgreSQL/backups → restored production.
- **Responsible phase:** INFRA + OPERATIONS (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — A timed restore recovers tenant data within approved RPO/RTO and reconciles post-backup revocations. Negative — Corrupt backups, missing keys and restored stale session authority prevent readiness.
- **Limitations / residual risk:** Backup retention leaves a data-loss window; independent storage reduces but cannot eliminate administrator and infrastructure compromise.

### SEC-VERIFY-001 — Verification target and standards

- **Requirement:** Security verification MUST propose OWASP ASVS Level 2 with selected higher-assurance privileged/sensitive controls, consider the referenced OWASP/NIST/FIDO/RFC/CIS guidance, and pin reviewed editions and applicability later; compliance, certification and exact mappings MUST NOT be claimed without verified evidence.
- **Rationale:** A target is not achieved compliance.
- **Priority:** P0.
- **Component / trust boundary:** Requirements → assurance evidence.
- **Responsible phase:** P4-DESIGN → SECURITY-VERIFY (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Independent security review.
- **Measurable acceptance criteria:** Positive — A review records selected editions, scope, higher-assurance choices and gaps. Negative — A claim of certification or an invented standard mapping fails review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-VERIFY-002 — Isolation and architecture regression

- **Requirement:** Existing Phase 03 tenant, database privilege, Redis and ArchUnit tests MUST remain intact and pass in the supported restricted environment before dependent functionality is accepted; test failures MUST NOT be hidden by disabling security, weakening roles or removing tests.
- **Rationale:** Authentication must preserve existing isolation foundations.
- **Priority:** P0.
- **Component / trust boundary:** Code changes → regression assurance.
- **Responsible phase:** P3-MAINTAIN + SECURITY-VERIFY (owner defined above).
- **Implementation status:** Partially implemented.
- **Evidence:** CrossTenantSecurityIntegrationTest, TenantBoundaryArchitectureTest and existing tenant/cache/database tests exist; .github/workflows/backend-ci.yml runs clean verify. No execution performed in Task 01.
- **Verification method:** Automated positive/negative integration tests and security review.
- **Measurable acceptance criteria:** Positive — The preserved suite passes with restricted PostgreSQL/Redis prerequisites. Negative — An intentionally unauthorized tenant route is denied and any failed mandatory regression blocks acceptance.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-VERIFY-003 — Adversarial verification and release evidence

- **Requirement:** Delivery MUST add measurable positive/negative tests for each applicable requirement, misuse and concurrency cases, authorized penetration/DAST testing and security failure injection; waivers MUST name owner, scope, expiry and residual risk, and conditional exclusions MUST cite the approved ADR.
- **Rationale:** Happy paths do not prove resistance to misuse.
- **Priority:** P0.
- **Component / trust boundary:** Implementation → release acceptance.
- **Responsible phase:** SECURITY-VERIFY + each delivery phase (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Evidence review, adversarial testing and release-gate exercise.
- **Measurable acceptance criteria:** Positive — Each applicable requirement has recorded evidence and an independent reviewer. Negative — An uncovered P0 requirement or expired waiver blocks acceptance.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

## Requirement counts

| Category | Count |
| --- | ---: |
| SEC-PRINCIPLE | 13 |
| SEC-CLIENT | 3 |
| SEC-AUTH | 5 |
| SEC-MFA | 7 |
| SEC-SESSION | 8 |
| SEC-TOKEN | 5 |
| SEC-BROWSER | 6 |
| SEC-AUTHZ | 7 |
| SEC-API | 6 |
| SEC-BUSINESS | 4 |
| SEC-TENANT | 7 |
| SEC-DATA | 4 |
| SEC-CRYPTO | 3 |
| SEC-INFRA | 6 |
| SEC-SUPPLY | 5 |
| SEC-AUDIT | 3 |
| SEC-RECOVERY | 3 |
| SEC-VERIFY | 3 |
| **Total** | **98** |

## Residual risk register

| Risk | Remaining exposure and containment direction |
| --- | --- |
| R1 — Compromised browser | Malicious extensions or same-origin JavaScript can read displayed data, initiate authorized actions and potentially use available keys. CSP and HttpOnly have limits; step-up, independent approval and session revocation reduce exposure. |
| R2 — Compromised customer OS | Local administrator/malware can control the browser and misuse authority already available to its user. It does not automatically gain ERP roles, other company memberships or production access. Strong authenticator verification cannot guarantee a trustworthy transaction display. |
| R3 — Compromised backend process | A shared process can invoke internal contracts, bind arbitrary contexts and use process-accessible database/Redis/migration secrets. Clean Architecture and routing checks do not sandbox hostile application code. Least privilege, egress limits, secret scope and detection reduce impact. |
| R4 — Compromised production administrator | Powerful administrators can replace software, steal keys or tamper with data/logs. Hardware authentication, separation of duties, independent audit/backup preservation and recovery limit but do not eliminate this trust. |
| R5 — Shared database credentials | search_path routing is not PostgreSQL authorization. Explicitly qualified SQL can reach every schema granted to a shared runtime role. Future database-enforced tenant privileges require a dedicated design within the single database. |
| R6 — Shared Redis credentials | Key prefixes/logical databases are not tenant ACL boundaries. A compromised raw client can access all keys permitted to its shared identity; even a tenant-root ACL spans multiple companies. Security-state separation and restricted commands remain future work. |
| R7 — Zero-day vulnerabilities | Unknown defects in browsers, JVM, frameworks, drivers, infrastructure or authenticators can bypass reviewed controls. Patching, layered containment and incident response remain necessary. |
| R8 — Malicious authorized insiders | A properly authenticated user can abuse granted access or collude. Field limits, dual authorization, monitoring and review reduce exposure without guaranteeing prevention. |
| R9 — Supply-chain compromise | Malicious packages, Actions, downloaded tools, build workers or signed artifacts can execute trusted code. Integrity/provenance checks authenticate origin, not benign behavior. |
| R10 — Infrastructure failure | Network partitions, data loss, clock error, cache eviction, failed key services and backup gaps can deny service or expose stale state. Fail-closed security may reduce availability; restoration must reconcile revocations. |
| R11 — Control limitations | Tests cover selected cases, scans have false negatives, policies may be misconfigured and bearer credentials remain replayable while valid unless additional constraints apply. No single control guarantees complete security and no compliance/certification claim is made. |

## Pending decisions and explicit deferrals

The owner approved native company-bound Spring Security sessions in ADR-0004. WebAuthn uses each exact company hostname as RP ID and exact HTTPS origin, with separate privileged admin RP and no parent-domain RP or cookies. SEC-TOKEN-001–005 are retired browser architecture records. Ordinary/privileged session deadlines remain as specified. Cookie theft replay is an accepted residual risk without browser proof-of-possession equivalence. See the foundation handoff for unverified source and remaining implementation/release gates.

Tenant hardening owns stronger database-enforced tenant privileges; tenant activation owns authenticated resolution, a separate tenant persistence unit, provisioning and worker boundaries. Billing and future ERP modules own payment/price/invoice/refund/inventory/upload controls before their release. Infrastructure, supply-chain and operations owners deliver production edge/network/runtime, keys, independent logs/backups, signing/provenance, patch and incident capabilities. These are unimplemented requirements, not claims about existing deployments. Hardware-bound enforcement, browser policy compatibility and envelope encryption scope require evidence-based design review. Session deadlines are approved by ADR-0004; password calibration, patch and RPO/RTO operational evidence remain required.

## Verification references

Official sources checked on 2026-11-27. These inform the proposed controls; edition-specific mapping, applicability and achievement remain future verification work:

- [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) — proposed Level 2 target with selected higher-assurance controls.
- [OWASP API Security Top 10 project](https://owasp.org/www-project-api-security/), [WSTG](https://owasp.org/www-project-web-security-testing-guide/) and [Cheat Sheet Series](https://cheatsheetseries.owasp.org/) — API misuse, test planning and implementation guidance.
- [NIST digital identity / authentication guidance](https://pages.nist.gov/800-63-4/sp800-63b.html) and [NIST SP 800-207 Zero Trust Architecture](https://csrc.nist.gov/pubs/sp/800/207/final) — assurance, recovery and trust-boundary review.
- [W3C WebAuthn](https://www.w3.org/TR/webauthn-3/) and [FIDO specifications](https://fidoalliance.org/specifications/) — ceremony and authenticator evidence validation.
- [RFC 8725: JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725), [RFC 9449: DPoP](https://www.rfc-editor.org/rfc/rfc9449) and [RFC 9700: OAuth Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700) — conditional token design review; OAuth is not thereby mandated.
- [CIS Benchmarks](https://www.cisecurity.org/cis-benchmarks) — applicable production hardening review after deployment versions are selected.

## Stage 03 evidence and limitations — 2026-11-28

The approved architecture is unchanged. [Stage 03](phase04-stage03-mfa-account-protection.md) adds source for native WebAuthn, explicitly permitted TOTP, bounded partial sessions, token/code recovery, current account security versions and a transactional notification outbox. First-factor enrollment needs fresh company-bound approval; registration alone is not authentication. All 98 canonical IDs and unrelated controls remain intact. Hardware-required identities remain fail-closed; email delivery is not functional without a separately implemented/configured/verified provider. Generic recent MFA is not a transaction-bound business approval. Owner-reported baseline success does not establish new-code, browser or hardware readiness.
