# STRIDE threat model

Phase 04 session architecture revision, 2026-11-27. This remains a design threat model, not an executed penetration test or production assessment.

## Architecture applicability and evidence

[ADR-0004](../adr/0004-authentication-and-session-architecture.md) accepts native Spring Security server-side sessions backed by Spring Session Redis. PostgreSQL writer authority checks account, company, membership, entitlement, policy/version and revocation. [Trust boundaries](attack-surface-and-trust-boundaries.md), [requirements](security-requirements.md), [acceptance criteria](security-acceptance-criteria.md) and the [handoff](phase04-authentication-foundation.md) distinguish unverified source from future controls. All unrelated threat/control IDs remain. Browser-token-specific scenarios below are retired, not credited as mitigated.

Adversaries include anonymous clients, compromised browsers/OS/extensions, authenticated insiders, hostile companies, malicious dependencies, compromised operators/providers and compromised backend code. React, local role/business storage, headers, bodies and client tenant/payment claims are untrusted. Authentication does not establish granular business authorization, honest transaction display or hostile-process isolation.

Stolen valid company session cookies may replay from another device. HttpOnly, TLS, rotation, expiry and revocation reduce risk; exact Origin/CSRF do not create proof of possession. Same-origin malicious code can issue authorized requests. Step-up and independent approval remain required for sensitive effects.

## Qualitative risk method

Ratings describe inherent scenario risk before the proposed controls are demonstrated. They are prioritization judgments, not measured probabilities or current vulnerability severities. Existing mitigations are credited only in the stated evidence scope. A future attack scenario assumes the component becomes exposed and a stated precondition occurs; it does not assert an exploitable current endpoint.

Likelihood: High = routine attacker reach/capability with commonly available techniques; Medium = additional compromise, race, privileged position or implementation defect required; Low = uncommon prerequisites/multiple independent failures. Impact: Critical = cross-tenant/production-wide compromise, privileged takeover or loss of durable security authority; High = serious single-company/account data loss, fraud or major service outage; Moderate = bounded recoverable business disruption without broad authority loss.

| Likelihood / impact | Moderate | High | Critical |
| --- | --- | --- | --- |
| Low | Low | Medium | High |
| Medium | Medium | High | High |
| High | High | High | Critical |

Severity is the risk band obtained from that matrix. Primary STRIDE classification is exclusive for counting; a scenario may have effects in other categories. Specific preconditions and impact text explain each assigned rating. No residual numeric risk reduction is invented: each record instead states remaining exposure after treatment. Critical/High scenarios gate their named feature/production release until preventive, detection, containment and recovery evidence exists or a narrowly scoped approved exception is allowed by the underlying requirement; mandatory production MFA/independent authority cannot be silently waived.

Evidence classes: **Confirmed existing limitation** means a bounded property established by code/document inspection, not a demonstrated exploit. **Hypothetical vulnerability** means a conditional flaw not found by testing. **Future attack scenario** means a threat against a capability not yet implemented/deployed. Records mentioning a future validation failure are hypotheses, not discovered defects. Preventive controls are current only where their linked requirement has narrow source-supported status; all other treatment is planned. Detection, containment and recovery controls below are proposed unless their requirement evidence says otherwise. Live tests require separate authorized test environments.

## Historical inherent-risk inventory (not implementation evidence)

| Primary STRIDE category | Threats |
| --- | ---: |
| Spoofing | 9 |
| Tampering | 13 |
| Repudiation | 5 |
| Information disclosure | 9 |
| Denial of service | 8 |
| Elevation of privilege | 12 |
| **Total** | **56** |

| Risk severity | Threats |
| --- | ---: |
| Critical | 4 |
| High | 52 |
| Medium | 0 |
| Low | 0 |

No risk is Low/Medium in this initial critical-asset catalogue under the chosen inherent-risk judgments; that is not evidence that every possible system threat has been enumerated. Independent review should challenge likelihood, asset scope and omissions.

## Threat catalogue

### THR-S-001 — Password account impersonation

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** User identity.
- **Attack surface:** Login/password verification and enumeration responses.
- **Trust boundary:** TB-01.
- **Scenario:** Credential stuffing, guessing or enumeration identifies and takes over an account.
- **Preconditions:** Attacker can reach public identity entry and has candidate credentials.
- **Business and technical impact:** Unauthorized customer/employee data access and fraudulent actions.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTH-001 (Password hashing); SEC-AUTH-002 (Password policy and screening); SEC-AUTH-003 (Automated attack and enumeration resistance); SEC-MFA-001 (WebAuthn policy enforcement). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Correlate failed logins, distributed attempts and suspicious successful logins.
- **Containment controls:** Throttle by account/source, require MFA and revoke affected sessions.
- **Recovery controls:** Reset compromised password/factors under recovery policy and investigate access.
- **Associated requirements:** SEC-AUTH-001, SEC-AUTH-002, SEC-AUTH-003, SEC-MFA-001.
- **Verification scenarios:** AC-04, AC-07; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; verify again Phase 11. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Stolen valid password plus compromised permitted factor remains usable until containment.

### THR-S-002 — False registration or mailbox ownership

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Account ownership.
- **Attack surface:** Registration, invitation and email verification.
- **Trust boundary:** TB-01.
- **Scenario:** An attacker registers another address or substitutes a verification token to claim company access.
- **Preconditions:** Registration or verification workflow exposed.
- **Business and technical impact:** Account confusion, invitation theft and unauthorized company membership.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTH-004 (Email ownership); SEC-AUTH-005 (Security notifications); SEC-MFA-003 (Safe authenticator registration); SEC-CLIENT-002 (Independent server decisions). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit verification, invitation and enrollment transitions without tokens.
- **Containment controls:** Keep unverified/enrollment identities restricted; invalidate disputed invitations.
- **Recovery controls:** Reverify ownership and memberships; revoke fraudulent sessions/factors.
- **Associated requirements:** SEC-AUTH-004, SEC-AUTH-005, SEC-MFA-003, SEC-CLIENT-002.
- **Verification scenarios:** AC-04, AC-05, AC-A-05; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 04–05. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Mailbox takeover and deceptive invitation recipients remain risks.

### THR-S-003 — WebAuthn origin or challenge impersonation

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Authenticator-bound identity.
- **Attack surface:** WebAuthn assertions and challenges.
- **Trust boundary:** TB-01, TB-12.
- **Scenario:** An assertion for a different RP/origin/account or a replayed challenge is accepted.
- **Preconditions:** Future verifier misvalidates ceremony bindings.
- **Business and technical impact:** MFA bypass and account takeover.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-MFA-001 (WebAuthn policy enforcement); SEC-MFA-002 (Challenge and origin validation); SEC-MFA-005 (Privileged authenticator assurance); SEC-MFA-006 (Biometric and PIN privacy). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Record rejected origin/challenge/signature/assurance classes and counter anomalies.
- **Containment controls:** Atomically consume challenges, deny uncertain assurance and revoke suspect credentials.
- **Recovery controls:** Re-enroll trusted factors through approved recovery and review RP configuration.
- **Associated requirements:** SEC-MFA-001, SEC-MFA-002, SEC-MFA-005, SEC-MFA-006.
- **Verification scenarios:** AC-06, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; frontend Phase 10. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** UV does not establish a specific biometric method, trustworthy display or hardware-bound key.

### THR-S-004 — Stolen opaque session impersonation

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Authenticated browser session.
- **Attack surface:** Cookie theft, fixation and device/session controls.
- **Trust boundary:** TB-01.
- **Scenario:** A stolen current cookie or preselected identifier is replayed as its user.
- **Preconditions:** Malware, network endpoint compromise or a fixation flaw exposes a bearer credential.
- **Business and technical impact:** Unauthorized use within the victim’s authority.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-SESSION-002 (Session identity and fixation); SEC-SESSION-003 (Idle and absolute expiry); SEC-SESSION-006 (Session management and containment); SEC-SESSION-007 (Cookie isolation); SEC-SESSION-008 (Failure and theft response); SEC-BROWSER-006 (Sensitive browser storage). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.
- **Containment controls:** Expire/revoke session, require fresh step-up and deny session fixation.
- **Recovery controls:** Rotate credentials, notify owner and review affected operations.
- **Associated requirements:** SEC-SESSION-002, SEC-SESSION-003, SEC-SESSION-006, SEC-SESSION-007, SEC-SESSION-008, SEC-BROWSER-006.
- **Verification scenarios:** AC-08, AC-09, AC-14; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; browser Phase 10. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.

### THR-S-005 — Token issuer or client impersonation (conditional)

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-S-006 — Provider identity spoofing

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Verified payment evidence.
- **Attack surface:** Webhook and payment return.
- **Trust boundary:** TB-07.
- **Scenario:** Attacker impersonates a gateway or presents frontend payment confirmation as proof.
- **Preconditions:** Future billing/provider ingress is reachable.
- **Business and technical impact:** Unpaid entitlement, fraudulent provisioning and revenue loss.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-CLIENT-003 (Commercial state is server-owned); SEC-BUSINESS-002 (Authentic payment and replay safety); SEC-BUSINESS-003 (Provisioning entitlement); SEC-AUTHZ-004 (Entitlement gating). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Alert on signature/freshness failures and settlement/entitlement mismatch.
- **Containment controls:** Reject unverified events; hold entitlement/provisioning transitions.
- **Recovery controls:** Reconcile provider records, revoke unpaid access while preserving customer data.
- **Associated requirements:** SEC-CLIENT-003, SEC-BUSINESS-002, SEC-BUSINESS-003, SEC-AUTHZ-004.
- **Verification scenarios:** AC-03; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 06; provisioning Phase 07. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A compromised provider key can authenticate false messages until rotation/reconciliation.

### THR-S-007 — Administrative identity spoofing

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Production and privileged accounts.
- **Attack surface:** SSH, console, support and privileged grants.
- **Trust boundary:** TB-08.
- **Scenario:** Attacker impersonates an operator/support approver to elevate access.
- **Preconditions:** Administrative account or support evidence is targeted.
- **Business and technical impact:** Production-wide compromise or privileged account takeover.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-INFRA-005 (Administration and deployment); SEC-MFA-005 (Privileged authenticator assurance); SEC-RECOVERY-001 (Account recovery assurance); SEC-PRINCIPLE-008 (Separation of Duties). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit named operator access, hardware assurance and approval provenance.
- **Containment controls:** Restrict administrative entry and require independent verified approval.
- **Recovery controls:** Revoke operator credentials and rebuild trust using incident runbooks.
- **Associated requirements:** SEC-INFRA-005, SEC-MFA-005, SEC-RECOVERY-001, SEC-PRINCIPLE-008.
- **Verification scenarios:** AC-12, AC-21, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 policies; Phase 12 operations. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Compromised privileged humans and collusion remain broad trust risks.

### THR-S-008 — Forged authority at request/worker entry

- **STRIDE classification:** Spoofing (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Company membership and delegated work.
- **Attack surface:** Role headers, localStorage, tenant selection and job payload.
- **Trust boundary:** TB-01, TB-06.
- **Scenario:** Modified React/local roles or a crafted job claim is interpreted as backend company authority.
- **Preconditions:** Client controls request/device or can submit/replay a job.
- **Business and technical impact:** Cross-company access, privilege escalation and unauthorized business effects.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** Critical (inherent).
- **Preventive controls:** SEC-CLIENT-001 (Customer-controlled environments); SEC-CLIENT-002 (Independent server decisions); SEC-AUTHZ-003 (Tenant membership and switching); SEC-TENANT-006 (Async tenant authority). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit rejected company selections and worker identity/policy mismatches.
- **Containment controls:** Validate current writer membership before scope binding; quarantine unauthorized jobs.
- **Recovery controls:** Revoke affected delegated authority and reconcile job effects.
- **Associated requirements:** SEC-CLIENT-001, SEC-CLIENT-002, SEC-AUTHZ-003, SEC-TENANT-006.
- **Verification scenarios:** AC-01, AC-02, AC-A-07; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; workers Phases 07–09. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Any trusted internal code can bind a context; application-process compromise is a separate risk.

### THR-T-001 — In-origin content and executable dependency tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** React DOM and browser execution.
- **Attack surface:** Stored/reflected/DOM input and npm/CDN scripts.
- **Trust boundary:** TB-01, TB-09.
- **Scenario:** Untrusted content reaches a script sink or a malicious allowed dependency modifies transactions.
- **Preconditions:** Future unsafe rendering or dependency compromise.
- **Business and technical impact:** User data theft and unauthorized actions under current session.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-BROWSER-001 (XSS-safe rendering); SEC-BROWSER-002 (Browser execution policy); SEC-BROWSER-003 (Third-party JavaScript); SEC-SUPPLY-003 (Dependency verification). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Collect redacted CSP/Trusted Types violations and integrity/dependency alerts.
- **Containment controls:** Block unapproved scripts, disable affected release/content and revoke sessions as needed.
- **Recovery controls:** Deploy reviewed assets, sanitize stored payloads and investigate operations.
- **Associated requirements:** SEC-BROWSER-001, SEC-BROWSER-002, SEC-BROWSER-003, SEC-SUPPLY-003.
- **Verification scenarios:** AC-17, AC-22; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 04/10; supply gates before release. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Malicious same-origin JavaScript can use current user authority despite HttpOnly cookies.

### THR-T-002 — Cross-origin and sibling request tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Browser-authorized mutations.
- **Attack surface:** CSRF, CORS, framing, redirects and sibling DNS.
- **Trust boundary:** TB-12.
- **Scenario:** A compromised sibling induces authenticated actions or supplies an unsafe return destination.
- **Preconditions:** User has an app session; sibling is same-site or navigation is user-mediated.
- **Business and technical impact:** Unintended changes, credential phishing and misleading UI.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-BROWSER-004 (Browser isolation headers); SEC-BROWSER-005 (CSRF, CORS and origin); SEC-SESSION-007 (Cookie isolation). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit denied Origin/CSRF/Host, unexpected framing and DNS ownership changes.
- **Containment controls:** Host-only cookies, exact Origin, synchronizer tokens and allowlisted redirects; disable compromised registered origins/clients and dangling DNS records.
- **Recovery controls:** Repair DNS/redirect policy, revoke exposed sessions and notify users.
- **Associated requirements:** SEC-BROWSER-004, SEC-BROWSER-005, SEC-SESSION-007.
- **Verification scenarios:** AC-17, AC-A-01; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; Phases 10/12 deployment. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** SameSite alone does not defend against same-site sibling origins or deceptive user consent.

### THR-T-003 — Proxy parser or forwarding-state tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Origin request integrity.
- **Attack surface:** Host, forwarded headers and HTTP framing.
- **Trust boundary:** TB-02, TB-03.
- **Scenario:** Conflicting parsers allow smuggled requests, spoofed client identity or wrong host routing.
- **Preconditions:** Future proxy/backend framing or trust mismatch.
- **Business and technical impact:** Authorization/rate-limit bypass, poisoned routing and data exposure.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-INFRA-001 (HTTPS and edge protection); SEC-INFRA-002 (Proxy protocol integrity); SEC-API-001 (Strict API contracts); SEC-API-004 (Resource abuse limits). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Alert on malformed framing, foreign hosts and anomalous edge/origin request counts.
- **Containment controls:** Reject ambiguity and restrict origin/proxy peers and header provenance.
- **Recovery controls:** Patch parser/proxy configuration and review affected logs/sessions.
- **Associated requirements:** SEC-INFRA-001, SEC-INFRA-002, SEC-API-001, SEC-API-004.
- **Verification scenarios:** AC-18, AC-21; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 12; verify Phase 11. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Different protocol implementations may have undiscovered parser discrepancies.

### THR-T-004 — SQL and schema route tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** PostgreSQL query structure and tenant selection.
- **Attack surface:** Dynamic identifiers, values and raw SQL.
- **Trust boundary:** TB-04.
- **Scenario:** Attacker manipulates SQL fragments or a schema override to reach another company.
- **Preconditions:** Unvalidated query construction or routing bypass is introduced.
- **Business and technical impact:** Company data modification/disclosure or database damage.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-API-002 (Query and injection safety); SEC-TENANT-002 (Strict schema identifiers); SEC-TENANT-003 (Fail-closed routing foundation). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit invalid identifier attempts and unexpected effective schema/path.
- **Containment controls:** Parameterized values, internal schema derivation and fail-closed provider route.
- **Recovery controls:** Restore affected data and repair unsafe query paths with isolation regression evidence.
- **Associated requirements:** SEC-API-002, SEC-TENANT-002, SEC-TENANT-003.
- **Verification scenarios:** AC-16, AC-18, AC-20; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 03 maintenance; Phases 04/07–09. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Parameterized queries do not constrain hostile fully qualified SQL from an authorized shared process.

### THR-T-005 — Pool or asynchronous context contamination

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Confirmed existing limitation: raw lease escape can bypass supported cleanup; exploitation is hypothetical.
- **Affected asset:** Tenant execution and reused SQL sessions.
- **Attack surface:** Hikari leases, thread reuse and unsupported direct close.
- **Trust boundary:** TB-04, TB-06.
- **Scenario:** Stale context, open transactions or leaked SQL state select/affect the next company.
- **Preconditions:** Caller violates scope/lease lifecycle or future integration bypasses cleanup.
- **Business and technical impact:** Cross-tenant effects and resource corruption.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-TENANT-001 (Scoped context lifecycle); SEC-TENANT-003 (Fail-closed routing foundation); SEC-TENANT-004 (Connection session cleanup); SEC-TENANT-006 (Async tenant authority); SEC-VERIFY-002 (Isolation and architecture regression). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Preserve physical-reuse tests, runtime guard failures and uncertain-ownership alerts.
- **Containment controls:** Close owned scopes, verify/reset idle sessions; evict owned unsafe connections or stop uncertain pool.
- **Recovery controls:** Recover pool and replay only verified idempotent work; investigate leaked contexts.
- **Associated requirements:** SEC-TENANT-001, SEC-TENANT-003, SEC-TENANT-004, SEC-TENANT-006, SEC-VERIFY-002.
- **Verification scenarios:** AC-16, AC-A-07; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 03 maintenance; Phase 07 activation. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Direct raw JDBC/proxy close can bypass supported provider cleanup; trusted internal ownership is required.

### THR-T-006 — Revocation race and stale state overwrite

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Session status and security versions.
- **Attack surface:** Concurrent requests, ORM saves, Redis and writer state.
- **Trust boundary:** TB-04, TB-05.
- **Scenario:** A late session save, cache hit or concurrent mutation restores revoked authority.
- **Preconditions:** Future implementation uses stale positive state, upsert or uncoordinated transactions.
- **Business and technical impact:** Continued account abuse after acknowledged logout/revocation.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-SESSION-004 (Durable revocation and no resurrection); SEC-SESSION-005 (PostgreSQL and Redis security consistency); SEC-AUTHZ-005 (Permission revocation bound); SEC-API-005 (Atomic and idempotent operations). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Trace version/status transitions and verify concurrent revocation ordering.
- **Containment controls:** Terminal state, primary checks and commit fences; no cached allow.
- **Recovery controls:** Revoke affected epoch/sessions and reconcile in-flight effects.
- **Associated requirements:** SEC-SESSION-004, SEC-SESSION-005, SEC-AUTHZ-005, SEC-API-005.
- **Verification scenarios:** AC-08, AC-15, AC-A-02; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; Phase 07 transaction proof. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Already delivered data and provider effects committed before revocation cannot be recalled.

### THR-T-007 — Credential/factor recovery state tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Enrollment, TOTP and recovery assurance.
- **Attack surface:** Factor add/remove, recovery tokens and accepted OTP steps.
- **Trust boundary:** TB-01, TB-08.
- **Scenario:** Attacker adds/removes factors, reuses a TOTP step or consumes another user’s recovery state.
- **Preconditions:** Future flow lacks binding, freshness, atomic consumption or proper recovery policy.
- **Business and technical impact:** Persistent takeover and mandatory MFA downgrade.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-MFA-003 (Safe authenticator registration); SEC-MFA-004 (Multiple factors and removal); SEC-MFA-007 (Conditional TOTP); SEC-RECOVERY-001 (Account recovery assurance). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Notify on factor/recovery transitions; audit rejected OTP/challenge reuse.
- **Containment controls:** Step-up, account/purpose binding, atomic use and last-factor protection.
- **Recovery controls:** Invalidate factors/recovery codes/sessions and re-establish approved assurance.
- **Associated requirements:** SEC-MFA-003, SEC-MFA-004, SEC-MFA-007, SEC-RECOVERY-001.
- **Verification scenarios:** AC-05, AC-07, AC-12; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A phished TOTP or fraudulent recovery evidence can still succeed within an allowed weaker policy.

### THR-T-008 — Refresh successor branching (conditional)

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-T-009 — Financial and inventory command tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** ERP ledgers, prices, stock and payments.
- **Attack surface:** Client totals/state, duplicate commands, race conditions.
- **Trust boundary:** TB-06, TB-07.
- **Scenario:** An attacker changes price/refund/stock state or races requests to duplicate an effect.
- **Preconditions:** Future modules trust client business state or omit transactional invariants.
- **Business and technical impact:** Financial loss, oversell and unreliable accounting.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-CLIENT-003 (Commercial state is server-owned); SEC-BUSINESS-001 (Server financial calculations); SEC-BUSINESS-002 (Authentic payment and replay safety); SEC-BUSINESS-004 (Inventory integrity); SEC-DATA-003 (Constraints and safe data formats); SEC-API-005 (Atomic and idempotent operations). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Reconcile payment/ledger/stock invariants and duplicate idempotency keys.
- **Containment controls:** Server calculations, locked/constraint-backed invariants and payload-bound idempotency.
- **Recovery controls:** Reconcile provider/ledger data and apply audited compensating operations.
- **Associated requirements:** SEC-CLIENT-003, SEC-BUSINESS-001, SEC-BUSINESS-002, SEC-BUSINESS-004, SEC-DATA-003, SEC-API-005.
- **Verification scenarios:** AC-03, AC-19; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 06 billing; Phases 08–09 ERP. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Authorized insiders can misuse permitted discounts or collude; business policy still matters.

### THR-T-010 — Stored file or released artifact tampering

- **STRIDE classification:** Tampering (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Uploads, application files and production artifacts.
- **Attack surface:** File paths, executable payloads, artifact replacement.
- **Trust boundary:** TB-06, TB-09.
- **Scenario:** Uploaded content escapes storage policy or a substituted release executes attacker code.
- **Preconditions:** Future upload/release path accepts unverified bytes or writable executable locations.
- **Business and technical impact:** Tenant data loss and potentially production compromise.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-API-006 (Secure uploads); SEC-INFRA-004 (Container and host hardening); SEC-INFRA-005 (Administration and deployment); SEC-INFRA-006 (Runtime detection); SEC-SUPPLY-004 (Artifact provenance and deployment). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Scan/quarantine files, monitor integrity and verify artifact provenance.
- **Containment controls:** Non-executable private storage, restricted runtime mounts and verified immutable deployment.
- **Recovery controls:** Restore clean artifact/data and rotate credentials if execution occurred.
- **Associated requirements:** SEC-API-006, SEC-INFRA-004, SEC-INFRA-005, SEC-INFRA-006, SEC-SUPPLY-004.
- **Verification scenarios:** AC-18, AC-21, AC-22; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 08–10 uploads; Phase 12 release/runtime. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Signing proves provenance, not absence of malicious authorized code.

### THR-R-001 — User security-action repudiation

- **STRIDE classification:** Repudiation (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Authentication and policy history.
- **Attack surface:** Login, logout, factor and membership changes.
- **Trust boundary:** TB-01, TB-04.
- **Scenario:** An attacker denies performing a security change because events are absent or misattributed.
- **Preconditions:** Future security handler fails to emit trusted correlated evidence.
- **Business and technical impact:** Slow incident response and disputed access/privilege changes.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUDIT-001 (Structured security events); SEC-AUTH-005 (Security notifications); SEC-SESSION-006 (Session management and containment). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Check event completeness and missing sequences against committed transitions.
- **Containment controls:** Use trusted actor/company context, validated correlation IDs and restricted audit access.
- **Recovery controls:** Preserve independent evidence, notify affected users and reconstruct timeline.
- **Associated requirements:** SEC-AUDIT-001, SEC-AUTH-005, SEC-SESSION-006.
- **Verification scenarios:** AC-14, AC-23; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 04–05; Phase 12 preservation. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Audit evidence is not proof that a compromised user device reflected the user’s intent.

### THR-R-002 — Financial approval repudiation

- **STRIDE classification:** Repudiation (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Critical transaction and approval evidence.
- **Attack surface:** Step-up, approver identity and material fields.
- **Trust boundary:** TB-06, TB-08.
- **Scenario:** Requester or approver disputes an action after a generic approval is reused or altered.
- **Preconditions:** Future critical workflow lacks single-use digest-bound independent approval.
- **Business and technical impact:** Fraud, financial dispute and unreliable accountability.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTHZ-006 (Transaction-bound step-up); SEC-AUTHZ-007 (Independent critical approval); SEC-BUSINESS-001 (Server financial calculations); SEC-AUDIT-001 (Structured security events). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Correlate requested digest, approval, current grants and final ledger event.
- **Containment controls:** Single-use transaction binding, distinct approvers and execution revalidation.
- **Recovery controls:** Reconcile financial state and investigate through independent preserved records.
- **Associated requirements:** SEC-AUTHZ-006, SEC-AUTHZ-007, SEC-BUSINESS-001, SEC-AUDIT-001.
- **Verification scenarios:** AC-13, AC-19; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 06/08–09. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** WebAuthn does not guarantee truthful display; colluding approvers remain possible.

### THR-R-003 — Privileged deployment repudiation

- **STRIDE classification:** Repudiation (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Production change provenance.
- **Attack surface:** GitHub, build approvals, SSH and release logs.
- **Trust boundary:** TB-08, TB-09.
- **Scenario:** A privileged change lacks attributable approval or its actor denies deployment.
- **Preconditions:** Shared identities, bypassed gates or missing artifact/deployment links.
- **Business and technical impact:** Undetected malicious release and poor incident accountability.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-SUPPLY-001 (Branch and workflow authority); SEC-SUPPLY-004 (Artifact provenance and deployment); SEC-INFRA-005 (Administration and deployment); SEC-PRINCIPLE-008 (Separation of Duties). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Correlate named approval, source digest, signed artifact and runtime identity.
- **Containment controls:** Separate authorship/release authority and restrict administrator entry.
- **Recovery controls:** Preserve release evidence, roll forward to reviewed artifact and rotate affected authority.
- **Associated requirements:** SEC-SUPPLY-001, SEC-SUPPLY-004, SEC-INFRA-005, SEC-PRINCIPLE-008.
- **Verification scenarios:** AC-21, AC-22, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 01–02 maintenance; Phase 12 release. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Solo development cannot provide independent production approval without additional reviewers.

### THR-R-004 — Audit deletion or log injection

- **STRIDE classification:** Repudiation (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Independent security evidence.
- **Attack surface:** Log fields, storage and administrative deletion.
- **Trust boundary:** TB-11.
- **Scenario:** Attacker injects forged events or removes local logs to hide a compromise.
- **Preconditions:** Untrusted fields are unescaped or process/admin can alter all copies.
- **Business and technical impact:** Lost investigation evidence and concealment of fraud.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUDIT-001 (Structured security events); SEC-AUDIT-002 (No credential exposure); SEC-AUDIT-003 (Audit preservation and alerting). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Detect gaps, tampering, invalid correlation structure and export failures.
- **Containment controls:** Structured encoding, minimal event content, restricted independent preservation.
- **Recovery controls:** Recover preserved copies, verify integrity and rebuild incident timeline.
- **Associated requirements:** SEC-AUDIT-001, SEC-AUDIT-002, SEC-AUDIT-003.
- **Verification scenarios:** AC-14, AC-23; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 event design; Phase 12 operations. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A compromised producer can lie before export; all-trust-root compromise defeats simple tamper evidence.

### THR-R-005 — Recovery and incident authority repudiation

- **STRIDE classification:** Repudiation (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Recovery decisions and containment records.
- **Attack surface:** Support overrides, break-glass and restore approval.
- **Trust boundary:** TB-08, TB-11.
- **Scenario:** A recovery/restore bypass is performed without accountable independent evidence.
- **Preconditions:** Operations lack approved staffing/runbooks or retain only mutable local records.
- **Business and technical impact:** Restored attacker access and inability to explain critical changes.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-RECOVERY-001 (Account recovery assurance); SEC-RECOVERY-002 (Incident containment and recovery); SEC-RECOVERY-003 (Backups, PITR and restores); SEC-VERIFY-003 (Adversarial verification and release evidence). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Review evidence completeness and incident approval timelines.
- **Containment controls:** Keep accounts/service restricted until independent approvals and verified reconciliation.
- **Recovery controls:** Revoke affected sessions/epoch and repeat clean recovery with preserved evidence.
- **Associated requirements:** SEC-RECOVERY-001, SEC-RECOVERY-002, SEC-RECOVERY-003, SEC-VERIFY-003.
- **Verification scenarios:** AC-12, AC-23, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 recovery; Phases 11–12 operations. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** If independent records are unavailable, availability must be sacrificed rather than assuming authority.

### THR-I-001 — Browser/OS and shared-terminal disclosure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Confirmed existing limitation: frontend localStorage is customer-controlled; backend exploitation is not established.
- **Affected asset:** User-visible confidential company data.
- **Attack surface:** LocalStorage, DOM, extensions, OS memory and unattended terminal.
- **Trust boundary:** TB-01.
- **Scenario:** Local malware or another employee reads data already visible to the authenticated user.
- **Preconditions:** Device compromise or unlocked shared session; local prototype data may persist.
- **Business and technical impact:** Customer/staff information disclosure and misuse of existing authority.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-CLIENT-001 (Customer-controlled environments); SEC-BROWSER-006 (Sensitive browser storage); SEC-SESSION-003 (Idle and absolute expiry); SEC-SESSION-006 (Session management and containment). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** User reports, session anomaly signals and privacy-aware terminal access reviews.
- **Containment controls:** No credential persistence, explicit handover, workstation lock and bounded session life.
- **Recovery controls:** Revoke session, clean endpoint and assess disclosed data.
- **Associated requirements:** SEC-CLIENT-001, SEC-BROWSER-006, SEC-SESSION-003, SEC-SESSION-006.
- **Verification scenarios:** AC-01, AC-09, AC-17; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 policy; Phase 10 browser. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Fully compromised device can read/use authorized data; backend cannot guarantee client confidentiality.

### THR-I-002 — Cross-object or field disclosure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Tenant resources, exports and metadata.
- **Attack surface:** Object IDs, bulk endpoints, response entities and mass assignment.
- **Trust boundary:** TB-01, TB-04.
- **Scenario:** A valid user requests another object/company or hidden field without correct checks.
- **Preconditions:** Future API relies on login or client filters alone.
- **Business and technical impact:** Confidential company data loss and regulatory/business harm.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** Critical (inherent).
- **Preventive controls:** SEC-AUTHZ-001 (Server RBAC and fine permissions); SEC-AUTHZ-002 (Ownership and mass assignment); SEC-AUTHZ-003 (Tenant membership and switching); SEC-API-001 (Strict API contracts). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit object/field denials and unusual bulk access by trusted context.
- **Containment controls:** Company/function/object/field mediation, response DTOs and bounded collections.
- **Recovery controls:** Revoke abuse, investigate exports and notify under approved incident policy.
- **Associated requirements:** SEC-AUTHZ-001, SEC-AUTHZ-002, SEC-AUTHZ-003, SEC-API-001.
- **Verification scenarios:** AC-01, AC-02, AC-18; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 policy; Phases 05–10 module gates. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Authorized bulk readers can still disclose data deliberately or through a compromised device.

### THR-I-003 — Shared runtime or Redis credential reach

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Confirmed existing limitation: shared credential grants span permitted schemas/keys; no exploit demonstrated.
- **Affected asset:** All schemas/keys granted to shared identities.
- **Attack surface:** Qualified SQL, raw Redis clients and internal context binding.
- **Trust boundary:** TB-04, TB-05.
- **Scenario:** Compromised backend code bypasses scoped ports and accesses other granted companies.
- **Preconditions:** Attacker controls runtime code or shared database/Redis credentials.
- **Business and technical impact:** Multi-tenant confidentiality/integrity breach.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-TENANT-007 (Database-enforced company privileges); SEC-DATA-001 (Restricted database roles); SEC-DATA-004 (Redis ACL and security-state isolation); SEC-PRINCIPLE-003 (Least Privilege); SEC-PRINCIPLE-011 (Blast Radius Reduction). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Monitor unexpected schema/key/command access and credential use.
- **Containment controls:** Least privilege, stronger database design, restricted Redis ACL/contracts and secret scope.
- **Recovery controls:** Contain application, rotate secrets and restore/reconcile affected tenant data.
- **Associated requirements:** SEC-TENANT-007, SEC-DATA-001, SEC-DATA-004, SEC-PRINCIPLE-003, SEC-PRINCIPLE-011.
- **Verification scenarios:** AC-20, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 07 hardening design; Phase 12 deployment. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Existing search_path/key prefixes are routing isolation, not per-tenant database/Redis authorization.

### THR-I-004 — Credentials or sensitive error exposure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Passwords, session credentials, MFA secrets and customer data.
- **Attack surface:** Logs, traces, errors, analytics, URLs and notifications.
- **Trust boundary:** TB-01, TB-10, TB-11.
- **Scenario:** Secrets are recorded or returned through success/error handling.
- **Preconditions:** Future instrumentation or handler copies sensitive input.
- **Business and technical impact:** Account takeover and large-scale credential/customer exposure.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUDIT-002 (No credential exposure); SEC-CRYPTO-002 (Key and secret management); SEC-MFA-006 (Biometric and PIN privacy); SEC-AUTH-005 (Security notifications); SEC-BROWSER-006 (Sensitive browser storage). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Canary-secret scans and restricted review of redaction pipelines.
- **Containment controls:** Redact at capture, minimize fields and restrict telemetry access/retention.
- **Recovery controls:** Rotate exposed authority, remove unsafe collection and preserve safe incident evidence.
- **Associated requirements:** SEC-AUDIT-002, SEC-CRYPTO-002, SEC-MFA-006, SEC-AUTH-005, SEC-BROWSER-006.
- **Verification scenarios:** AC-14; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; Phases 10/12 telemetry. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Error-suppression configuration alone does not verify all credential handling.

### THR-I-005 — Sensitive store, TOTP or backup disclosure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Encrypted data, OTP seeds, password/recovery hashes and keys.
- **Attack surface:** Database dumps, backup media and encryption-key access.
- **Trust boundary:** TB-04, TB-11.
- **Scenario:** Stolen storage or colocated keys reveal sensitive secrets and tenant data.
- **Preconditions:** Attacker obtains dump, backup or key authority.
- **Business and technical impact:** Offline guessing, factor cloning and customer data disclosure.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTH-001 (Password hashing); SEC-MFA-007 (Conditional TOTP); SEC-CRYPTO-001 (Sensitive data protection); SEC-CRYPTO-002 (Key and secret management); SEC-CRYPTO-003 (Cryptographic hygiene); SEC-RECOVERY-003 (Backups, PITR and restores). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit key access, backup exports and unexpected decryption.
- **Containment controls:** Calibrated hashes, authenticated encryption, key separation and independent encrypted backup.
- **Recovery controls:** Rotate affected keys/factors, recover clean data and assess disclosure.
- **Associated requirements:** SEC-AUTH-001, SEC-MFA-007, SEC-CRYPTO-001, SEC-CRYPTO-002, SEC-CRYPTO-003, SEC-RECOVERY-003.
- **Verification scenarios:** AC-07, AC-14, AC-23, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 secrets; Phase 12 backups. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** At-rest encryption cannot stop an authorized compromised application from using accessible decryption keys.

### THR-I-006 — SSRF and unintended egress disclosure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Internal metadata, services and provider credentials.
- **Attack surface:** URL fetchers, redirects and DNS.
- **Trust boundary:** TB-06, TB-10.
- **Scenario:** A user-selected URL reaches metadata/internal endpoints or exfiltrates secrets.
- **Preconditions:** Future import/provider/file functionality makes outbound requests.
- **Business and technical impact:** Credential theft, internal reconnaissance and lateral access.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-API-003 (SSRF and egress); SEC-INFRA-003 (Private services and network limits); SEC-CRYPTO-002 (Key and secret management). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Alert on blocked private destinations, DNS changes and unusual outbound flows.
- **Containment controls:** Purpose allowlists, DNS/redirect revalidation, bounded fetch and network egress restrictions.
- **Recovery controls:** Rotate disclosed credentials and isolate vulnerable fetch workflow.
- **Associated requirements:** SEC-API-003, SEC-INFRA-003, SEC-CRYPTO-002.
- **Verification scenarios:** AC-18, AC-21; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 policy; module introduction; Phase 12 egress. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** DNS/network changes and approved compromised destinations remain residual risks.

### THR-I-007 — Transport and origin exposure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Credentials and confidential traffic.
- **Attack surface:** Public HTTP, direct origin and private data service ports.
- **Trust boundary:** TB-02, TB-03, TB-04, TB-05.
- **Scenario:** Traffic or a public data listener exposes confidential material outside approved paths.
- **Preconditions:** Future TLS/firewall/origin trust is misconfigured or absent.
- **Business and technical impact:** Credential interception, direct attack surface and company data loss.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-INFRA-001 (HTTPS and edge protection); SEC-INFRA-002 (Proxy protocol integrity); SEC-INFRA-003 (Private services and network limits); SEC-SESSION-007 (Cookie isolation). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Monitor certificate, firewall, direct-origin and unapproved data connection events.
- **Containment controls:** Approved TLS, HSTS readiness, private services and trusted proxy paths.
- **Recovery controls:** Restrict exposure, rotate potentially intercepted credentials and validate affected records.
- **Associated requirements:** SEC-INFRA-001, SEC-INFRA-002, SEC-INFRA-003, SEC-SESSION-007.
- **Verification scenarios:** AC-21; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 12. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Network privacy does not authorize a user or isolate a compromised process.

### THR-I-008 — Unreviewed artifact/dependency disclosure

- **STRIDE classification:** Information disclosure (primary).
- **Evidence classification:** Confirmed existing limitation: tag-pinned Actions and unverified scanner archive downloads; malicious compromise is hypothetical.
- **Affected asset:** Source, build secrets and production data.
- **Attack surface:** Untrusted PRs, Actions, package installers and downloaded scanners.
- **Trust boundary:** TB-09.
- **Scenario:** Malicious build/dependency code exfiltrates secrets or enters a trusted release.
- **Preconditions:** A dependency/runner/action/registry is compromised or PR secrets are exposed.
- **Business and technical impact:** Supply-chain compromise and production-wide secret/data theft.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-SUPPLY-001 (Branch and workflow authority); SEC-SUPPLY-002 (Security analysis gates); SEC-SUPPLY-003 (Dependency verification); SEC-SUPPLY-004 (Artifact provenance and deployment); SEC-SUPPLY-005 (Vulnerability and patch lifecycle). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Secret/SAST/SCA/tool-integrity findings, provenance mismatch and runner egress alerts.
- **Containment controls:** Least-privilege CI, immutable pins, byte verification, isolated untrusted code and authorized artifact verification.
- **Recovery controls:** Rebuild from reviewed inputs, revoke affected secrets and patch dependencies.
- **Associated requirements:** SEC-SUPPLY-001, SEC-SUPPLY-002, SEC-SUPPLY-003, SEC-SUPPLY-004, SEC-SUPPLY-005.
- **Verification scenarios:** AC-22; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 01 maintenance; Phase 11 assurance; Phase 12 release. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A signed artifact or clean scan can still contain malicious trusted code.

### THR-D-001 — Authentication resource exhaustion

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Identity availability and account access.
- **Attack surface:** Argon2id, WebAuthn/TOTP verification, registration and recovery.
- **Trust boundary:** TB-01.
- **Scenario:** Attackers exhaust CPU/memory or lock out victims via expensive/abusive auth requests.
- **Preconditions:** Public identity functions accept attacker traffic.
- **Business and technical impact:** ERP sign-in outage and loss of staff productivity.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTH-001 (Password hashing); SEC-AUTH-003 (Automated attack and enumeration resistance); SEC-API-004 (Resource abuse limits); SEC-SESSION-008 (Failure and theft response). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Monitor attempt rates, hash queue/latency and per-account denial.
- **Containment controls:** Calibrated bounded hashing, source/account quotas and fail-closed required limiters.
- **Recovery controls:** Restore capacity and safe limiter policy without bypassing authentication.
- **Associated requirements:** SEC-AUTH-001, SEC-AUTH-003, SEC-API-004, SEC-SESSION-008.
- **Verification scenarios:** AC-07, AC-15, AC-18; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; Phase 11 load testing. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Distributed attacks and strict fail-closed limits can deny legitimate users.

### THR-D-002 — Shared API, database or cache exhaustion

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Multi-tenant availability.
- **Attack surface:** Oversized requests, bulk reads, connections, cache values and jobs.
- **Trust boundary:** TB-03, TB-04, TB-05, TB-06.
- **Scenario:** One client/tenant monopolizes shared CPU, memory, pools or work queues.
- **Preconditions:** Future endpoints/cache consumers omit entry-size/concurrency budgets.
- **Business and technical impact:** Multi-company outage or severe latency.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-API-001 (Strict API contracts); SEC-API-004 (Resource abuse limits); SEC-TENANT-005 (Tenant Redis namespaces); SEC-INFRA-004 (Container and host hardening). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Measure queue/pool saturation, per-tenant budgets and cache pressure.
- **Containment controls:** Bound body/page/value sizes, TTL, concurrency and background quotas; isolate budgets.
- **Recovery controls:** Drain/cancel bounded work and recover pools without cross-tenant fallback.
- **Associated requirements:** SEC-API-001, SEC-API-004, SEC-TENANT-005, SEC-INFRA-004.
- **Verification scenarios:** AC-16, AC-18, AC-21; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 limits; Phases 07–11 feature/load gates. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** One process and shared stores retain common capacity/failure domains.

### THR-D-003 — Network flood and direct-origin bypass

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Internet and origin service availability.
- **Attack surface:** Cloudflare, Nginx and exposed ports.
- **Trust boundary:** TB-02, TB-03.
- **Scenario:** A flood or edge bypass saturates origin resources.
- **Preconditions:** Production ingress is reachable; origin protection is incomplete.
- **Business and technical impact:** All customers lose availability.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-INFRA-001 (HTTPS and edge protection); SEC-INFRA-002 (Proxy protocol integrity); SEC-INFRA-003 (Private services and network limits); SEC-API-004 (Resource abuse limits). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Edge/origin traffic discrepancy, rate and saturation alerts.
- **Containment controls:** WAF/DDoS controls, direct-origin firewall and proxy resource limits.
- **Recovery controls:** Coordinate provider mitigation and restore verified paths.
- **Associated requirements:** SEC-INFRA-001, SEC-INFRA-002, SEC-INFRA-003, SEC-API-004.
- **Verification scenarios:** AC-21; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 12; Phase 11 authorized testing. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Provider/network outages and large attacks remain possible; WAF is one layer only.

### THR-D-004 — Required security-state failure

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Protected-operation availability and correct denial.
- **Attack surface:** Writer PostgreSQL, Redis rate limits and secret/control plane.
- **Trust boundary:** TB-04, TB-05.
- **Scenario:** Outage, partition, eviction or ambiguous commit prevents safe security decisions.
- **Preconditions:** Required dependency fails or returns uncertain state.
- **Business and technical impact:** Fail-closed outage; unsafe fallback would instead grant unauthorized access.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-PRINCIPLE-005 (Fail Closed); SEC-SESSION-005 (PostgreSQL and Redis security consistency); SEC-SESSION-008 (Failure and theft response); SEC-DATA-004 (Redis ACL and security-state isolation). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Detect timeouts, unknown commits and security-state mismatch without logging secrets.
- **Containment controls:** Deny affected decisions; no cached allow or cross-store atomicity assumption.
- **Recovery controls:** Reconcile committed database state/outbox and resume only after authority is verified.
- **Associated requirements:** SEC-PRINCIPLE-005, SEC-SESSION-005, SEC-SESSION-008, SEC-DATA-004.
- **Verification scenarios:** AC-08, AC-15, AC-A-02; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 design; Phase 12 readiness. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Security-preserving denial reduces availability; independent outage capacity must be planned.

### THR-D-005 — Revocation lock or long-job starvation

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Security control responsiveness.
- **Attack surface:** Long reads/mutations, approval locks, export streams and workers.
- **Trust boundary:** TB-04, TB-06.
- **Scenario:** A long operation holds authority indefinitely or prevents timely revocation.
- **Preconditions:** Future central/tenant transaction coordination lacks bounded work/lock ordering.
- **Business and technical impact:** Continued access or inability to acknowledge containment.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-SESSION-004 (Durable revocation and no resurrection); SEC-AUTHZ-005 (Permission revocation bound); SEC-TENANT-006 (Async tenant authority); SEC-API-004 (Resource abuse limits); SEC-API-005 (Atomic and idempotent operations). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Monitor fence lock waits, authorization age and stream/job chunk deadlines.
- **Containment controls:** Short ordered transactions, rollback timeouts and chunk reauthorization.
- **Recovery controls:** Cancel stalled work, preserve idempotent outcomes and retry revocation without false success.
- **Associated requirements:** SEC-SESSION-004, SEC-AUTHZ-005, SEC-TENANT-006, SEC-API-004, SEC-API-005.
- **Verification scenarios:** AC-08, AC-19, AC-A-02, AC-A-07; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 semantics; Phase 07 fence proof; Phase 11 races. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Already authorized reads and externally committed side effects cannot be recalled.

### THR-D-006 — Backup or restore unavailability

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Disaster recovery capability.
- **Attack surface:** Backup storage, keys, WAL/PITR and restore procedures.
- **Trust boundary:** TB-11.
- **Scenario:** Ransomware, corruption or missing keys makes a backup unusable or recovery unsafe.
- **Preconditions:** Independent backups/restore tests or security reconciliation are insufficient.
- **Business and technical impact:** Extended outage, data loss and potential stale-authority resurrection.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-RECOVERY-002 (Incident containment and recovery); SEC-RECOVERY-003 (Backups, PITR and restores); SEC-AUDIT-003 (Audit preservation and alerting); SEC-SESSION-004 (Durable revocation and no resurrection). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Verify backup age/integrity, restoration timing and preserved journal coverage.
- **Containment controls:** Independent encrypted copies, access separation and closed restored ingress.
- **Recovery controls:** PITR restore, fresh external epoch and reconciled authority before reopening.
- **Associated requirements:** SEC-RECOVERY-002, SEC-RECOVERY-003, SEC-AUDIT-003, SEC-SESSION-004.
- **Verification scenarios:** AC-08, AC-23, AC-A-03; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 12; Phase 11 recovery exercise. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** RPO permits data loss; missing reconciliation evidence may require prolonged account/tenant restrictions.

### THR-D-007 — Monitoring or patch response failure

- **STRIDE classification:** Denial of service (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Detection and containment readiness.
- **Attack surface:** Audit pipeline, alerts and vulnerability lifecycle.
- **Trust boundary:** TB-09, TB-11.
- **Scenario:** Missing alerts or overdue vulnerabilities let an attack persist until service fails.
- **Preconditions:** Operations lack owners, preserved telemetry or patch SLA enforcement.
- **Business and technical impact:** Longer compromise, outage and growing financial loss.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-INFRA-006 (Runtime detection); SEC-AUDIT-003 (Audit preservation and alerting); SEC-SUPPLY-005 (Vulnerability and patch lifecycle); SEC-RECOVERY-002 (Incident containment and recovery); SEC-VERIFY-003 (Adversarial verification and release evidence). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Alert on telemetry gaps, expired exceptions and overdue findings.
- **Containment controls:** Assign owners, restrict exposed capability and apply containment deadlines.
- **Recovery controls:** Patch verified artifacts, recover clean service and exercise revised runbooks.
- **Associated requirements:** SEC-INFRA-006, SEC-AUDIT-003, SEC-SUPPLY-005, SEC-RECOVERY-002, SEC-VERIFY-003.
- **Verification scenarios:** AC-21, AC-22, AC-23, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phases 11–12; gates at every release. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** False negatives and zero-days persist despite monitoring and scanning.

### THR-E-001 — Client role elevation and unauthorized tenant switching

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Company/platform privileges.
- **Attack surface:** React flags, API parameters, subdomain aliases and membership state.
- **Trust boundary:** TB-01, TB-12.
- **Scenario:** Local administrator or modified UI claims an owner role or another company.
- **Preconditions:** Backend incorrectly treats client state/context as authorization.
- **Business and technical impact:** Cross-tenant abuse and platform privilege escalation.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** Critical (inherent).
- **Preventive controls:** SEC-CLIENT-001 (Customer-controlled environments); SEC-CLIENT-002 (Independent server decisions); SEC-AUTHZ-001 (Server RBAC and fine permissions); SEC-AUTHZ-003 (Tenant membership and switching); SEC-TENANT-001 (Scoped context lifecycle). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit role/company selection mismatches and forbidden function attempts.
- **Containment controls:** Deny by default, writer membership and company permissions before scope bind.
- **Recovery controls:** Revoke affected grants/sessions and investigate improper operations.
- **Associated requirements:** SEC-CLIENT-001, SEC-CLIENT-002, SEC-AUTHZ-001, SEC-AUTHZ-003, SEC-TENANT-001.
- **Verification scenarios:** AC-01, AC-02; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; integration Phase 10. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Authorized insiders and compromised existing authority can still misuse permitted resources.

### THR-E-002 — Object/field and mass-assignment escalation

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Protected resources and ownership.
- **Attack surface:** Nested/bulk object IDs and writable DTO fields.
- **Trust boundary:** TB-01, TB-06.
- **Scenario:** A user updates another object, owner, workflow state or permission field.
- **Preconditions:** Future module misses function/object/field checks.
- **Business and technical impact:** Privilege escalation and unauthorized confidential/financial changes.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTHZ-001 (Server RBAC and fine permissions); SEC-AUTHZ-002 (Ownership and mass assignment); SEC-API-001 (Strict API contracts). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Correlate denied ownership/field writes and unusual bulk patterns.
- **Containment controls:** Explicit permission/ownership checks and strict writable DTO allowlists.
- **Recovery controls:** Restore authorized state, revoke abuse and verify all alternate endpoints.
- **Associated requirements:** SEC-AUTHZ-001, SEC-AUTHZ-002, SEC-API-001.
- **Verification scenarios:** AC-18; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 policy; Phases 05–10 module gates. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A correctly authorized malicious user can still perform harmful permitted operations.

### THR-E-003 — Privileged authenticator downgrade

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Privileged assurance and factor lifecycle.
- **Attack surface:** Enrollment/removal, synced credentials and recovery alternatives.
- **Trust boundary:** TB-01, TB-08.
- **Scenario:** A synced/unknown-assurance credential or TOTP/email recovery substitutes for mandatory hardware assurance.
- **Preconditions:** Future policy equates UV/brand with hardware properties or permits weak fallback.
- **Business and technical impact:** Persistent privileged takeover.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-MFA-001 (WebAuthn policy enforcement); SEC-MFA-003 (Safe authenticator registration); SEC-MFA-004 (Multiple factors and removal); SEC-MFA-005 (Privileged authenticator assurance); SEC-MFA-007 (Conditional TOTP); SEC-RECOVERY-001 (Account recovery assurance). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Review assurance evidence and log attempted downgrade without biometric details.
- **Containment controls:** Enforce verifiable policy, no silent recovery bypass, preserve last required factor.
- **Recovery controls:** Restrict affected roles and re-establish hardware-bound assurance independently.
- **Associated requirements:** SEC-MFA-001, SEC-MFA-003, SEC-MFA-004, SEC-MFA-005, SEC-MFA-007, SEC-RECOVERY-001.
- **Verification scenarios:** AC-05, AC-06, AC-12; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04; Phase 12 operator readiness. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Device evidence/privacy/compatibility may prevent support; that must deny restricted role rather than weaken it.

### THR-E-004 — Shared database role escape

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Confirmed existing limitation: routing differs from database authorization; no hostile access demonstrated.
- **Affected asset:** Database privilege and schema boundaries.
- **Attack surface:** Runtime DDL, qualified SQL, Flyway and provisioner credentials.
- **Trust boundary:** TB-04.
- **Scenario:** Runtime/process compromise reaches other granted schemas or assumes stronger database authority.
- **Preconditions:** Shared SQL credential or migration secret is compromised.
- **Business and technical impact:** Multi-tenant compromise and migration/history destruction if privilege boundary fails.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-DATA-001 (Restricted database roles); SEC-DATA-002 (Migration integrity); SEC-TENANT-007 (Database-enforced company privileges); SEC-CRYPTO-002 (Key and secret management). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Catalog/grant reviews, denied DDL/role tests and unexpected credential use.
- **Containment controls:** Separate runtime/migrator, deny DDL/history writes and design stronger tenant privileges.
- **Recovery controls:** Rotate credentials, restore verified schemas/history and review reach.
- **Associated requirements:** SEC-DATA-001, SEC-DATA-002, SEC-TENANT-007, SEC-CRYPTO-002.
- **Verification scenarios:** AC-20; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 03 preservation; Phase 07 design gate; Phase 12 grants. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Current shared runtime grants do not enforce per-company authorization; startup process also receives migration credentials.

### THR-E-005 — Redis cache to security-state escalation

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Confirmed existing limitation: prefixes and shared Redis identity are not tenant authorization; security-state store is future.
- **Affected asset:** Session/revocation authority and tenant caches.
- **Attack surface:** Shared Redis credentials, raw clients and eviction/clear commands.
- **Trust boundary:** TB-05.
- **Scenario:** Cache code overwrites/removes security keys or a cache miss is interpreted as new authority.
- **Preconditions:** Future security store shares broad credentials/contracts or cached allow is trusted.
- **Business and technical impact:** Session resurrection, cross-tenant exposure and MFA/rate-limit bypass.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-DATA-004 (Redis ACL and security-state isolation); SEC-TENANT-005 (Tenant Redis namespaces); SEC-SESSION-005 (PostgreSQL and Redis security consistency); SEC-SESSION-008 (Failure and theft response). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit unauthorized key/command access and security-state eviction pressure.
- **Containment controls:** Separate scoped identities/contracts, restrict commands and check PostgreSQL authority.
- **Recovery controls:** Rotate Redis credentials and rebuild only disposable state; revoke affected sessions.
- **Associated requirements:** SEC-DATA-004, SEC-TENANT-005, SEC-SESSION-005, SEC-SESSION-008.
- **Verification scenarios:** AC-08, AC-15, AC-20; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 contract design; Phase 12 ACLs. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Existing shared credentials/key prefixes cannot constrain a hostile raw infrastructure client.

### THR-E-006 — Unpaid provisioning or entitlement elevation

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Paid capabilities and tenant resources.
- **Attack surface:** Subscription claims, privileged provisioning and repeated callbacks.
- **Trust boundary:** TB-07.
- **Scenario:** Forged subscription state or repeated provision command creates paid resources/privileges.
- **Preconditions:** Future billing/provisioner trusts client state or fails idempotent eligibility checks.
- **Business and technical impact:** Revenue loss, resource abuse and unauthorized company resources.
- **Likelihood:** High under the stated preconditions and qualitative method.
- **Impact severity:** High.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-CLIENT-003 (Commercial state is server-owned); SEC-AUTHZ-004 (Entitlement gating); SEC-BUSINESS-002 (Authentic payment and replay safety); SEC-BUSINESS-003 (Provisioning entitlement). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Reconcile paid entitlements against provider events and provisioning inventory.
- **Containment controls:** Verified payment, current entitlement, restricted provisioner and replay-safe state machine.
- **Recovery controls:** Restrict unpaid operations, reconcile resources and preserve company data.
- **Associated requirements:** SEC-CLIENT-003, SEC-AUTHZ-004, SEC-BUSINESS-002, SEC-BUSINESS-003.
- **Verification scenarios:** AC-03; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 06 payments; Phase 07 provisioning. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Provider compromise and incorrect server pricing policy remain business risks.

### THR-E-007 — Sensitive approval reuse or insider collusion

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** High-value operations and delegated grants.
- **Attack surface:** Step-up proof, transaction digest and dual approval.
- **Trust boundary:** TB-06, TB-08.
- **Scenario:** Generic step-up or self-approval authorizes a changed/duplicate sensitive operation.
- **Preconditions:** Future workflow lacks single-use binding, distinct approvers or current grant checks.
- **Business and technical impact:** Fraud, privilege elevation and unauthorized recovery/deployment.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-AUTHZ-006 (Transaction-bound step-up); SEC-AUTHZ-007 (Independent critical approval); SEC-PRINCIPLE-008 (Separation of Duties); SEC-PRINCIPLE-009 (Complete Mediation). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Audit approver independence, digest changes and repeated approval use.
- **Containment controls:** Transaction-bound WebAuthn, distinct current grants and atomic approval consumption.
- **Recovery controls:** Freeze affected workflows, reconcile effects and review privileged assignments.
- **Associated requirements:** SEC-AUTHZ-006, SEC-AUTHZ-007, SEC-PRINCIPLE-008, SEC-PRINCIPLE-009.
- **Verification scenarios:** AC-13, AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 framework; Phases 05–09 workflows; Phase 12 operations. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Compromised displays and colluding authorized humans can defeat intent/independence assumptions.

### THR-E-008 — Backend, dependency or host execution compromise

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Monolith/runtime and production host.
- **Attack surface:** Unsafe uploads/deserialization, dependencies, host/container privileges.
- **Trust boundary:** TB-06, TB-09, TB-08.
- **Scenario:** Attacker executes code inside the shared process or escalates to host administration.
- **Preconditions:** A hypothetical exploitable runtime/dependency defect or malicious trusted code exists.
- **Business and technical impact:** Production-wide data/credential compromise and loss of trusted enforcement.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-API-006 (Secure uploads); SEC-DATA-003 (Constraints and safe data formats); SEC-CRYPTO-001 (Sensitive data protection); SEC-INFRA-004 (Container and host hardening); SEC-INFRA-005 (Administration and deployment); SEC-SUPPLY-002 (Security analysis gates); SEC-SUPPLY-005 (Vulnerability and patch lifecycle). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Runtime process/file/egress monitoring and security-analysis findings.
- **Containment controls:** Least-privilege non-root runtime, safe serialization, upload isolation and restricted secret reach.
- **Recovery controls:** Isolate compromised host, rotate secrets and deploy clean verified artifact with restored data.
- **Associated requirements:** SEC-API-006, SEC-DATA-003, SEC-CRYPTO-001, SEC-INFRA-004, SEC-INFRA-005, SEC-SUPPLY-002, SEC-SUPPLY-005.
- **Verification scenarios:** AC-18, AC-21, AC-22; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Feature gates Phases 04–10; Phases 11–12 assurance/runtime. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** A compromised backend can bypass application controls and use available keys; shared modules are not sandboxes.

### THR-E-009 — Stale restore regrants authority

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** Revoked accounts, factors, permissions and sessions.
- **Attack surface:** PITR snapshots, failover and recovery epoch.
- **Trust boundary:** TB-11.
- **Scenario:** Old sessions resume or a fresh login reuses roles/factors revoked after the restore point.
- **Preconditions:** Acknowledged security writes are missing from restored/failover state.
- **Business and technical impact:** Previously contained attacker regains access.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-SESSION-004 (Durable revocation and no resurrection); SEC-SESSION-005 (PostgreSQL and Redis security consistency); SEC-AUTHZ-005 (Permission revocation bound); SEC-RECOVERY-002 (Incident containment and recovery); SEC-RECOVERY-003 (Backups, PITR and restores); SEC-AUDIT-003 (Audit preservation and alerting). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Compare recovery epoch, writer fencing and independently preserved security-change journal.
- **Containment controls:** Close ingress, new external epoch, fence writers and reconcile all post-restore revocations/grants.
- **Recovery controls:** Re-establish affected authority independently before login/tenant reopening.
- **Associated requirements:** SEC-SESSION-004, SEC-SESSION-005, SEC-AUTHZ-005, SEC-RECOVERY-002, SEC-RECOVERY-003, SEC-AUDIT-003.
- **Verification scenarios:** AC-08, AC-23, AC-A-03; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** Phase 04 durability design; Phase 12 restore gate. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Epoch reset alone blocks old credentials but cannot reconstruct lost revocations; missing evidence forces restriction.

### THR-E-010 — Unreviewed security boundary or bypassed release gate

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario.
- **Affected asset:** System-wide security objectives.
- **Attack surface:** Architecture changes, solo approvals and incomplete verification.
- **Trust boundary:** TB-01 through TB-12.
- **Scenario:** A feature bypasses controls or declares readiness without threat/evidence review.
- **Preconditions:** Missing ownership, speculative security assumptions or unapproved architecture adoption.
- **Business and technical impact:** Systemic security gaps and unjustified production assurance.
- **Likelihood:** Medium under the stated preconditions and qualitative method.
- **Impact severity:** Critical.
- **Risk severity:** High (inherent).
- **Preventive controls:** SEC-PRINCIPLE-001 (Defense in Depth); SEC-PRINCIPLE-002 (Zero Trust); SEC-PRINCIPLE-003 (Least Privilege); SEC-PRINCIPLE-004 (Deny by Default); SEC-PRINCIPLE-005 (Fail Closed); SEC-PRINCIPLE-006 (Secure by Design); SEC-PRINCIPLE-007 (Secure by Default); SEC-PRINCIPLE-008 (Separation of Duties); SEC-PRINCIPLE-009 (Complete Mediation); SEC-PRINCIPLE-010 (Minimization of Attack Surface); SEC-PRINCIPLE-011 (Blast Radius Reduction); SEC-PRINCIPLE-012 (Explicit Trust Boundaries); SEC-PRINCIPLE-013 (Prevention, Detection, Containment and Recovery); SEC-VERIFY-001 (Verification target and standards); SEC-VERIFY-002 (Isolation and architecture regression); SEC-VERIFY-003 (Adversarial verification and release evidence); SEC-CRYPTO-003 (Cryptographic hygiene); SEC-SESSION-001 (Architecture selection). Apply their normative statements and the accepted ADR where relevant; implementation status remains as documented.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval.
- **Recovery controls:** Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Associated requirements:** SEC-PRINCIPLE-001, SEC-PRINCIPLE-002, SEC-PRINCIPLE-003, SEC-PRINCIPLE-004, SEC-PRINCIPLE-005, SEC-PRINCIPLE-006, SEC-PRINCIPLE-007, SEC-PRINCIPLE-008, SEC-PRINCIPLE-009, SEC-PRINCIPLE-010, SEC-PRINCIPLE-011, SEC-PRINCIPLE-012, SEC-PRINCIPLE-013, SEC-VERIFY-001, SEC-VERIFY-002, SEC-VERIFY-003, SEC-CRYPTO-003, SEC-SESSION-001.
- **Verification scenarios:** AC-24; include both canonical positive/negative criteria of each associated requirement.
- **Responsible phase:** All delivery phases; formal Phase 11 and Phase 12 gates. Owners and feature/release gates are resolved in the SRTM.
- **Remaining risk:** Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.

## Superseded proposal scenarios and company-boundary threat

### THR-S-009 — Cookie-only bootstrap of an attacker key

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-T-011 — Proof replay after cache loss or first-use race

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-T-012 — Key-bound refresh family race and rebind

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-I-009 — Non-exportable key misuse in a compromised signer

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-E-011 — Company/hostname or admin authority confusion

- **STRIDE classification:** Elevation of privilege (primary).
- **Evidence classification:** Future attack scenario; proposed revision, no exploit demonstrated.
- **Affected asset:** Cross-company and privileged authority.
- **Attack surface:** Company session issuance, Host, Origin and restored Redis sessions.
- **Trust boundary:** TB-03, TB-04, TB-08, TB-12.
- **Scenario:** A company-A token/proof or manipulated client/host is accepted at company B or the platform administrator interface.
- **Preconditions:** Future verifier trusts subdomain/claims without exact client/company/audience/key mapping and current membership.
- **Business and technical impact:** Cross-tenant or platform privileged access.
- **Likelihood:** High under the existing qualitative method and stated preconditions.
- **Impact severity:** Critical.
- **Risk severity:** Critical (inherent).
- **Preventive controls:** Exact configured Host/HTTPS origin and database company mapping, host-bound principal, current writer membership/entitlement/version checks; no parent-domain RP/cookie. Privileged MFA fails closed.
- **Detection controls:** Alert on audience/host/client/company mismatch and forbidden admin issuance.
- **Containment controls:** Reject mismatch, disable compromised registered origin/client and revoke its scoped families.
- **Recovery controls:** Repair DNS/registry, re-establish authorized grants and investigate cross-company access.
- **Associated requirements:** SEC-CLIENT-002, SEC-AUTHZ-001, SEC-AUTHZ-003, SEC-TOKEN-001, SEC-TOKEN-005, SEC-INFRA-002, SEC-SESSION-007.
- **Verification scenarios:** AC-02, AC-R-01, AC-R-08, AC-R-10; canonical positive/negative criteria also remain required.
- **Responsible phase:** Phase 04 protocol/design qualification and later implementation only after approval; Phase 10 browser integration; Phase 11 adversarial/load verification; Phase 12 actual deployment/durability. Identity/protocol, frontend, data and independent security owners share the stated gates.
- **Remaining risk:** Same backend/OS compromise or an authorized multi-company account can have wider reach; subdomains alone are not authorization.

### THR-D-008 — Proof, nonce and replay-ledger exhaustion

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-T-013 — Authorization-window callback or transient state injection

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.

### THR-E-012 — Unsupported framework silently removes sender constraint

Status: Retired browser-token-specific scenario under approved ADR-0004. Identifier retained, not reused. No JWT/refresh/DPoP/popup implementation or related proof guarantee applies to browser sessions. Current cookie replay, CSRF, company binding and required-state threats remain active under THR-S-004, THR-T-002, THR-D-004 and THR-E-001.


## Treatment and release review

All active threats remain release gates for affected features. Retired protocol scenarios do not imply that cookie replay is prevented. Current source denies mandatory MFA and privileged password-only access; actual MFA, recovery, transaction step-up, dual approval, RBAC, audit, notifications, session-management and restoration evidence remain incomplete. In-flight mutation authority must be fenced in the business transaction before ERP mutation endpoints are exposed.

Redis session credentials require restricted network/TLS/ACL access; prefixes and logical database numbers are not security boundaries. PostgreSQL must be the current writer; no stale-replica or cached allow is permitted. Restoration requires ingress fencing, a fresh Redis namespace and reconciliation of durable revocations from independent evidence. Compromised runtime/operator credentials, malicious dependencies, privileged collusion and zero-days remain consequential. No test, scan, WebAuthn, WAF, session cookie, schema route or encryption provides complete prevention.

## Stage 03 control mapping and residual risk — 2026-11-28

THR-S-001/002/003/004, THR-T-002/006/007, THR-I-004/005, THR-D-001/004 and THR-E-001/003/007 remain active. Source now implements native exact-origin/RP ceremonies with UV and a distinct signed assertion after registration; committed challenge consumption; expiring password/browser/company-bound pending state; first-enrollment ownership approval; encrypted TOTP with an atomic replay fence; hashed single-use recovery; version-fenced password/factor changes; and transactional notification events. See AC-M-01–10 and [Stage 03 evidence](phase04-stage03-mfa-account-protection.md).

These are unverified controls, not measured risk reductions. Synced/software authenticators and none attestation do not prove hardware; every hardware-required role/recovery path still denies. Email delivery is queued but not externally implemented. Initial enrollment approval depends on actual mailbox ownership/delivery or a trusted provisioning process; no client-supplied approval is trusted. Recovery codes are possession secrets and may be stolen; they yield only restricted reenrollment with a fresh password. MFA or enrollment state cannot act as independent approval for business transactions. A valid stolen session cookie and compromised same-origin code remain powerful inside current authority; no equivalent browser proof-of-possession is claimed. Shared runtime/process/Redis authority, restore reconciliation, independent audit, policy assignment and remaining business/infrastructure protections retain their earlier limitations and release gates.

Stage 03 additionally persists enrollment history/MFA opt-in, rejects first-enrollment email approval for previously enrolled identities, and revokes a completed grant when cancellation wins after completion. Global password replacement cannot use ordinary company MFA to bypass an account’s hardware-privileged scope. Token, challenge and transaction deadlines are not runtime-writable; delivery status is not runtime-writable. These guards and new regression definitions are unexecuted, and do not remove the operational release gates above.
