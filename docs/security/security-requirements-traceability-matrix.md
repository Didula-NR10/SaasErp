# Security requirements traceability matrix

Phase 04 session-foundation traceability revision, 2026-11-27, with Stage 05 implementation disposition and the V9 authorization correction appended 2026-11-28. [ADR-0004](../adr/0004-authentication-and-session-architecture.md) is owner-approved. All 98 canonical IDs are retained, including five retired browser-token records. Source and test definitions are not executed results.

The matrix uses one entry per requirement to keep all required fields readable. Planned task IDs IMP-<requirement suffix> are local backlog candidates, not invented issue links or claims of verified implementation. Owners are accountable roles requiring assignment to named people. Threat treatments are proposed; source evidence does not establish a green test or deployed control. The underlying canonical positive/negative criteria remain mandatory even when an integrated scenario covers several controls.

## Roadmap and workstream crosswalk

The supplied numbered roadmap is authoritative for scheduling. Existing Task 01 workstream text is preserved as historical vocabulary, with the following explicit crosswalk. A later numbered phase never permits an earlier exposed feature to omit its applicable controls. Phase 01–03 completion is the supplied project milestone status, not independent proof of current tests or production controls.

| Roadmap phase | Scope and legacy workstream mapping | Security gate |
| --- | --- | --- |
| Phase 01 — Development environment and CI (completed milestone) | SUPPLY foundations; maintain current Dev Container and workflow evidence. | Preserve checks; remaining SHA pins, download integrity, hosted settings and release controls stay tracked in Phases 11/12. |
| Phase 02 — Software architecture foundation (completed milestone) | Shared ADR/module/ArchUnit rules and principles; maintain dependencies. | No API-to-infrastructure bypass, speculative service boundaries or weakened architecture tests. |
| Phase 03 — Database and multi-tenancy (completed milestone) | P3-MAINTAIN. | Preserve scoped context/routing/reset/cache tests; do not mislabel inactive persistence/authenticated resolution as delivered. |
| Phase 04 — Authentication, authorization and security architecture | P4-DESIGN, P4-IMPLEMENT, identity cryptography/recovery/audit primitives. | Approved native session architecture; identity/MFA/session/security-state tests before exposure. |
| Phase 05 — Platform management | Account, company membership/roles, notification, audit and privileged assignment management. | Current membership/permission enforcement, independent privileged/recovery approvals and audit. |
| Phase 06 — Billing, payment gateway and subscriptions | BILLING, ERP-SALES/BILLING billing portion, entitlement eligibility. | Signature/original-byte verification, freshness/replay/idempotency, authoritative settlement/price and server entitlements before paid activation. |
| Phase 07 — Tenant provisioning | TENANT-ACTIVATION and TENANT-HARDENING; async tenant boundary. | Restricted verified provisioning, separate tenant persistence, proven central-authority/tenant transaction fence and stronger database-enforced privilege design. Earlier gate applies if stronger assurance is claimed earlier. |
| Phase 08 — Shared ERP core | ERP, ERP-INVENTORY/PURCHASING, ERP-SALES/BILLING sales portion, shared files. | Object/field/company checks, ledger/stock/financial invariants, bound approvals and upload safety before module exposure. |
| Phase 09 — Industry-specific ERP modules | ERP vertical adaptations. | Preserve shared invariants and module contracts; validate new workflow/tenant boundaries and delegated jobs. |
| Phase 10 — Frontend integration | FRONTEND. | Safe React/CSP/Origin/CSRF/storage and dedicated company-origin session/CSRF flows; local role/business values remain untrusted. |
| Phase 11 — Security, penetration testing and performance | SECURITY-VERIFY plus SUPPLY verification, independent review and authorized exercises. | ASVS target assessment, adversarial/race/outage/restore/load tests and closed applicable P0 gaps; no conformance claim without evidence. |
| Phase 12 — Production deployment, infrastructure and disaster recovery | INFRA, OPERATIONS; SUPPLY release authorization and independent preservation. | Proven actual grants/ACLs, TLS/proxy/network/runtime, secrets, independent staffing/approval, signed artifacts, backup/restore/epoch reconciliation and operational SLAs. |

P4 labels stay in canonical records to preserve the baseline; every matrix row below supplies numbered phase responsibilities. SUPPLY and OPERATIONS are continuing duties, not a reason to delay protection of an already exposed feature. Legacy compound labels such as ERP/BILLING use both named domain phases; all-delivery labels mean each affected Phase 04–12 release plus Phase 01–03 maintenance.

## Status, conditional scope and release rules

Statuses follow the canonical source records. Partial implementation means a bounded foundation exists but the full canonical control and/or release evidence remains incomplete. SEC-TOKEN-001–005 are retired for browser sessions. Stages 04–05 include tested company authorization, a fail-closed platform authorization gate, operation-bound WebAuthn step-up, dual approval, an atomic company-role assignment foundation, audit and API quotas/boundaries. These do not constitute production authorization of future business mutations or satisfy remaining operational and owner gates. Platform-admin session provisioning and attestation verification remain unavailable, so privileged platform access is denied. Valid stolen cookies may replay; no browser proof-of-possession equivalence is claimed.

Each entry’s release gate means P0 must pass before the applicable feature is exposed; P1 requires completion by its named phase or an approved bounded exception under the baseline policy. No exception silently downgrades mandatory MFA, independent production approval, verified payment or unsupported isolation claims. Existing Phase 03 evidence remains scoped to routed APIs, not fully hostile runtime containment.

## Per-requirement matrix

### SEC-PRINCIPLE-001 — Defense in Depth

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Designs MUST combine independent preventive, detective and recovery controls across client, application, data and operational boundaries.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-001 — deliver/preserve and verify “Defense in Depth” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-001 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-002 — Zero Trust

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Each protected operation MUST establish identity, device/session assurance where applicable, resource authorization and current tenant policy without trusting network location.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-002 — deliver/preserve and verify “Zero Trust” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-002 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-003 — Least Privilege

- **Related threat IDs:** THR-I-003, THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-04, TB-05, TB-01 through TB-12.
- **Preventive controls:** Human and machine identities MUST receive only documented necessary permissions, with periodic review and time-bounded elevated access.
- **Detection controls:** Monitor unexpected schema/key/command access and credential use.; Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Least privilege, stronger database design, restricted Redis ACL/contracts and secret scope. Contain application, rotate secrets and restore/reconcile affected tenant data.; Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-003 — deliver/preserve and verify “Least Privilege” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-20, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-003 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Existing search_path/key prefixes are routing isolation, not per-tenant database/Redis authorization.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-004 — Deny by Default

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Unmatched routes, permissions, memberships and policy cases MUST deny protected access.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-004 — deliver/preserve and verify “Deny by Default” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-004 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-005 — Fail Closed

- **Related threat IDs:** THR-D-004, THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-04, TB-05, TB-01 through TB-12.
- **Preventive controls:** An unavailable or indeterminate required security decision MUST deny the protected operation without tenant, credential or policy fallback.
- **Detection controls:** Detect timeouts, unknown commits and security-state mismatch without logging secrets.; Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny affected decisions; no cached allow or cross-store atomicity assumption. Reconcile committed database state/outbox and resume only after authority is verified.; Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-005 — deliver/preserve and verify “Fail Closed” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-15, AC-24, AC-A-02.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-005 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Security-preserving denial reduces availability; independent outage capacity must be planned.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-006 — Secure by Design

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Security review MUST precede release of new trust boundaries and sensitive workflows, retaining modular monolith and inward dependency rules.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-006 — deliver/preserve and verify “Secure by Design” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-006 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-007 — Secure by Default

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Fresh installations MUST expose only necessary capabilities and MUST require explicit reviewed configuration for risky features.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-007 — deliver/preserve and verify “Secure by Default” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-007 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-008 — Separation of Duties

- **Related threat IDs:** THR-S-007, THR-R-003, THR-E-007, THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-08, TB-08, TB-09, TB-06, TB-08, TB-01 through TB-12.
- **Preventive controls:** Critical role grants, deployments, recovery and selected financial operations MUST separate requester and approver identities.
- **Detection controls:** Audit named operator access, hardware assurance and approval provenance.; Correlate named approval, source digest, signed artifact and runtime identity.
- **Containment / recovery controls:** Restrict administrative entry and require independent verified approval. Revoke operator credentials and rebuild trust using incident runbooks.; Preserve release evidence, roll forward to reviewed artifact and rotate affected authority.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-008 — deliver/preserve and verify “Separation of Duties” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-12, AC-13, AC-21, AC-22, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-008 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Compromised privileged humans and collusion remain broad trust risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-009 — Complete Mediation

- **Related threat IDs:** THR-E-007, THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-06, TB-08, TB-01 through TB-12.
- **Preventive controls:** Authorization MUST run at every protected entry point and state-changing execution boundary, including background jobs and alternate APIs.
- **Detection controls:** Audit approver independence, digest changes and repeated approval use.; Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Transaction-bound WebAuthn, distinct current grants and atomic approval consumption. Freeze affected workflows, reconcile effects and review privileged assignments.; Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-009 — deliver/preserve and verify “Complete Mediation” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-13, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-009 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Compromised displays and colluding authorized humans can defeat intent/independence assumptions.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-010 — Minimization of Attack Surface

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Releases MUST inventory and remove unnecessary endpoints, dependencies, privileges and exposed administrative functions.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-010 — deliver/preserve and verify “Minimization of Attack Surface” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-010 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-011 — Blast Radius Reduction

- **Related threat IDs:** THR-I-003, THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-04, TB-05, TB-01 through TB-12.
- **Preventive controls:** Designs MUST constrain compromise by tenant, environment, account and administrative scope using explicit boundaries.
- **Detection controls:** Monitor unexpected schema/key/command access and credential use.; Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Least privilege, stronger database design, restricted Redis ACL/contracts and secret scope. Contain application, rotate secrets and restore/reconcile affected tenant data.; Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-011 — deliver/preserve and verify “Blast Radius Reduction” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-20, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-011 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Existing search_path/key prefixes are routing isolation, not per-tenant database/Redis authorization.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-012 — Explicit Trust Boundaries

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Designs MUST identify every transition of identity, data and authority and name its enforcing component.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-012 — deliver/preserve and verify “Explicit Trust Boundaries” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-012 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-PRINCIPLE-013 — Prevention, Detection, Containment and Recovery

- **Related threat IDs:** THR-E-010.
- **Affected component / boundary:** All trust boundaries; TB-01 through TB-12.
- **Preventive controls:** Each critical threat class MUST have prevention, detection, containment and tested recovery procedures with operational owners.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Security architect + all component owners.
- **Implementation phase:** Phase 04 design; Phases 01–03 maintenance and 05–12 delivery gates. Legacy workstream: P4-DESIGN + all delivery phases (owner defined above).
- **Planned implementation task:** IMP-PRINCIPLE-013 — deliver/preserve and verify “Prevention, Detection, Containment and Recovery” under the canonical requirement and proposed design after approval.
- **Verification method:** Architecture review and tabletop exercises; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-PRINCIPLE-013 canonical record](security-principles.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-CLIENT-001 — Customer-controlled environments

- **Related threat IDs:** THR-S-008, THR-I-001, THR-E-001, THR-I-009.
- **Affected component / boundary:** Customer device → backend; TB-01, TB-06, TB-01, TB-01, TB-12.
- **Preventive controls:** Customer operating systems, browsers, extensions, React code, JavaScript runtimes, browser storage, headers, parameters and bodies MUST be treated as untrusted; device administrator privileges MUST NOT confer backend or infrastructure authority.
- **Detection controls:** Audit rejected company selections and worker identity/policy mismatches.; User reports, session anomaly signals and privacy-aware terminal access reviews.
- **Containment / recovery controls:** Validate current writer membership before scope binding; quarantine unauthorized jobs. Revoke affected delegated authority and reconcile job effects.; Revoke session, clean endpoint and assess disclosed data.
- **Implementation owner:** Backend identity + frontend owners.
- **Implementation phase:** Phase 04 enforcement; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-CLIENT-001 — deliver/preserve and verify “Customer-controlled environments” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-09, AC-17, AC-A-07, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-CLIENT-001 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** R1/R2: a compromised device can act within the authenticated user’s existing authority. Any trusted internal code can bind a context; application-process compromise is a separate risk.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-CLIENT-002 — Independent server decisions

- **Related threat IDs:** THR-S-002, THR-S-008, THR-E-001, THR-E-011.
- **Affected component / boundary:** Browser/API → application; TB-01, TB-01, TB-06, TB-01, TB-12.
- **Preventive controls:** The backend MUST independently validate identity, authentication state, roles, permissions, company membership, business rules and applicable security policies on protected operations.
- **Detection controls:** Audit verification, invitation and enrollment transitions without tokens.; Audit rejected company selections and worker identity/policy mismatches.
- **Containment / recovery controls:** Keep unverified/enrollment identities restricted; invalidate disputed invitations. Reverify ownership and memberships; revoke fraudulent sessions/factors.; Revoke affected delegated authority and reconcile job effects.
- **Implementation owner:** Backend identity + frontend owners.
- **Implementation phase:** Phase 04 enforcement; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-CLIENT-002 — deliver/preserve and verify “Independent server decisions” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-04, AC-05, AC-A-05, AC-A-07, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-CLIENT-002 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Mailbox takeover and deceptive invitation recipients remain risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-CLIENT-003 — Commercial state is server-owned

- **Related threat IDs:** THR-S-006, THR-T-009, THR-E-006.
- **Affected component / boundary:** Client/provider → billing; TB-07, TB-06, TB-07.
- **Preventive controls:** Prices, payment status, subscription state and entitlements MUST come from authoritative server-side records and verified provider evidence, never client assertions.
- **Detection controls:** Alert on signature/freshness failures and settlement/entitlement mismatch.; Reconcile payment/ledger/stock invariants and duplicate idempotency keys.
- **Containment / recovery controls:** Reject unverified events; hold entitlement/provisioning transitions. Reconcile provider records, revoke unpaid access while preserving customer data.; Reconcile provider/ledger data and apply audited compensating operations.
- **Implementation owner:** Billing owner + ERP price-rule owners.
- **Implementation phase:** Phase 06 payment/subscription authority; Phases 08–09 server business state. Legacy workstream: BILLING (owner defined above).
- **Planned implementation task:** IMP-CLIENT-003 — deliver/preserve and verify “Commercial state is server-owned” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03, AC-19.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-CLIENT-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A compromised provider key can authenticate false messages until rotation/reconciliation.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTH-001 — Password hashing

- **Related threat IDs:** THR-S-001, THR-I-005, THR-D-001.
- **Affected component / boundary:** Backend; TB-01, TB-04, TB-11.
- **Preventive controls:** Passwords MUST be hashed with Argon2id, unique cryptographically random salts and versioned parameters calibrated on deployment hardware; parameters MUST be reviewed and hashes upgraded on successful verification when obsolete.
- **Detection controls:** Correlate failed logins, distributed attempts and suspicious successful logins.; Audit key access, backup exports and unexpected decryption.
- **Containment / recovery controls:** Throttle by account/source, require MFA and revoke affected sessions. Reset compromised password/factors under recovery policy and investigate access.; Rotate affected keys/factors, recover clean data and assess disclosure.
- **Implementation owner:** Identity backend owner.
- **Implementation phase:** Phase 04 identity; Phase 05 account/notification management; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTH-001 — deliver/preserve and verify “Password hashing” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-07, AC-14, AC-15, AC-18, AC-23, AC-24.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md), [platform authorization service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/PlatformAuthorizationService.java), V8 platform-session gate, and AccountProtectionIntegrationTest. The gate requires recorded hardware-attestation evidence and fails closed; no platform session provisioning or attestation verifier exists, so hardware access is not established by this foundation.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Stolen valid password plus compromised permitted factor remains usable until containment.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTH-002 — Password policy and screening

- **Related threat IDs:** THR-S-001.
- **Affected component / boundary:** Backend; TB-01.
- **Preventive controls:** Password policy MUST permit long passphrases, paste and password managers, screen compromised/common passwords on creation and reset, and rate-limit verification; it SHOULD avoid arbitrary composition and periodic change absent compromise. Proposed minimum is 15 characters with at least 64 supported, subject to policy review.
- **Detection controls:** Correlate failed logins, distributed attempts and suspicious successful logins.
- **Containment / recovery controls:** Throttle by account/source, require MFA and revoke affected sessions. Reset compromised password/factors under recovery policy and investigate access.
- **Implementation owner:** Identity backend owner.
- **Implementation phase:** Phase 04 identity; Phase 05 account/notification management; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTH-002 — deliver/preserve and verify “Password policy and screening” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-07.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Stolen valid password plus compromised permitted factor remains usable until containment.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTH-003 — Automated attack and enumeration resistance

- **Related threat IDs:** THR-S-001, THR-D-001, THR-S-009.
- **Affected component / boundary:** Backend; TB-01.
- **Preventive controls:** Login, registration, reset and verification MUST use bounded account and source-aware throttling against brute force and credential stuffing, generic responses and materially equivalent failure timing; lockout recovery MUST resist attacker-induced denial of service.
- **Detection controls:** Correlate failed logins, distributed attempts and suspicious successful logins.; Monitor attempt rates, hash queue/latency and per-account denial.
- **Containment / recovery controls:** Throttle by account/source, require MFA and revoke affected sessions. Reset compromised password/factors under recovery policy and investigate access.; Restore capacity and safe limiter policy without bypassing authentication.
- **Implementation owner:** Identity backend owner.
- **Implementation phase:** Phase 04 identity; Phase 05 account/notification management; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTH-003 — deliver/preserve and verify “Automated attack and enumeration resistance” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-07, AC-15, AC-18, AC-A-05, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Stolen valid password plus compromised permitted factor remains usable until containment.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTH-004 — Email ownership

- **Related threat IDs:** THR-S-002.
- **Affected component / boundary:** Backend; TB-01.
- **Preventive controls:** Email ownership MUST be verified using random, single-use, expiring tokens bound to account, purpose and intended address before verified-email privileges; email changes MUST require reauthentication and notifications to old and new addresses.
- **Detection controls:** Audit verification, invitation and enrollment transitions without tokens.
- **Containment / recovery controls:** Keep unverified/enrollment identities restricted; invalidate disputed invitations. Reverify ownership and memberships; revoke fraudulent sessions/factors.
- **Implementation owner:** Identity backend owner.
- **Implementation phase:** Phase 04 identity; Phase 05 account/notification management; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTH-004 — deliver/preserve and verify “Email ownership” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-05, AC-A-05.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Mailbox takeover and deceptive invitation recipients remain risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTH-005 — Security notifications

- **Related threat IDs:** THR-S-002, THR-R-001, THR-I-004.
- **Affected component / boundary:** Backend; TB-01, TB-01, TB-04, TB-01, TB-10, TB-11.
- **Preventive controls:** Authentication factor changes, recovery, password/email changes and suspicious sign-ins MUST generate privacy-preserving security notifications without credentials, with an independent incident-report path.
- **Detection controls:** Audit verification, invitation and enrollment transitions without tokens.; Check event completeness and missing sequences against committed transitions.
- **Containment / recovery controls:** Keep unverified/enrollment identities restricted; invalidate disputed invitations. Reverify ownership and memberships; revoke fraudulent sessions/factors.; Preserve independent evidence, notify affected users and reconstruct timeline.
- **Implementation owner:** Identity backend owner.
- **Implementation phase:** Phase 04 identity; Phase 05 account/notification management; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTH-005 — deliver/preserve and verify “Security notifications” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-05, AC-14, AC-23, AC-A-05.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Mailbox takeover and deceptive invitation recipients remain risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P1 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-001 — WebAuthn policy enforcement

- **Related threat IDs:** THR-S-001, THR-S-003, THR-E-003, THR-S-009.
- **Affected component / boundary:** Authenticator/browser → identity; TB-01, TB-01, TB-12, TB-01, TB-08.
- **Preventive controls:** WebAuthn MUST be the primary MFA method, with user verification required and server-validated; privileged identities MUST use MFA, company-required MFA MUST apply to every company operation, and ordinary-user MFA MAY be optional only where all applicable policies permit. A qualifying UV WebAuthn ceremony MAY satisfy multiple factors only under an approved assurance policy.
- **Detection controls:** Correlate failed logins, distributed attempts and suspicious successful logins.; Record rejected origin/challenge/signature/assurance classes and counter anomalies.
- **Containment / recovery controls:** Throttle by account/source, require MFA and revoke affected sessions. Reset compromised password/factors under recovery policy and investigate access.; Re-enroll trusted factors through approved recovery and review RP configuration.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-001 — deliver/preserve and verify “WebAuthn policy enforcement” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-05, AC-06, AC-07, AC-12, AC-24, AC-A-05, AC-A-06, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Stolen valid password plus compromised permitted factor remains usable until containment.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-002 — Challenge and origin validation

- **Related threat IDs:** THR-S-003, THR-S-009.
- **Affected component / boundary:** Authenticator/browser → identity; TB-01, TB-12.
- **Preventive controls:** WebAuthn registration and authentication MUST use cryptographically random, expiring, atomically single-use challenges bound to account/session and ceremony; RP ID, exact allowed origin, ceremony type, signature, credential ownership and required flags MUST be verified.
- **Detection controls:** Record rejected origin/challenge/signature/assurance classes and counter anomalies.
- **Containment / recovery controls:** Atomically consume challenges, deny uncertain assurance and revoke suspect credentials. Re-enroll trusted factors through approved recovery and review RP configuration.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-002 — deliver/preserve and verify “Challenge and origin validation” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-06, AC-24, AC-A-01, AC-A-06, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. UV does not establish a specific biometric method, trustworthy display or hardware-bound key.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-003 — Safe authenticator registration

- **Related threat IDs:** THR-S-002, THR-T-007, THR-E-003.
- **Affected component / boundary:** Backend; TB-01, TB-01, TB-08.
- **Preventive controls:** Adding authenticators MUST require a recent approved authentication or narrowly scoped initial-enrollment flow, account-bound challenges and policy validation; enrollment-only sessions MUST NOT access normal protected resources.
- **Detection controls:** Audit verification, invitation and enrollment transitions without tokens.; Notify on factor/recovery transitions; audit rejected OTP/challenge reuse.
- **Containment / recovery controls:** Keep unverified/enrollment identities restricted; invalidate disputed invitations. Reverify ownership and memberships; revoke fraudulent sessions/factors.; Invalidate factors/recovery codes/sessions and re-establish approved assurance.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-003 — deliver/preserve and verify “Safe authenticator registration” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-04, AC-05, AC-06, AC-07, AC-12, AC-A-05.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Mailbox takeover and deceptive invitation recipients remain risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-004 — Multiple factors and removal

- **Related threat IDs:** THR-T-007, THR-E-003.
- **Affected component / boundary:** Backend; TB-01, TB-08.
- **Preventive controls:** Accounts MUST support multiple approved authenticators; removal MUST require step-up, revoke the removed credential immediately, notify the owner and preserve the minimum required assurance or enter restricted recovery.
- **Detection controls:** Notify on factor/recovery transitions; audit rejected OTP/challenge reuse.; Review assurance evidence and log attempted downgrade without biometric details.
- **Containment / recovery controls:** Step-up, account/purpose binding, atomic use and last-factor protection. Invalidate factors/recovery codes/sessions and re-establish approved assurance.; Restrict affected roles and re-establish hardware-bound assurance independently.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-004 — deliver/preserve and verify “Multiple factors and removal” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-05, AC-06, AC-07, AC-12.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A phished TOTP or fraudulent recovery evidence can still succeed within an allowed weaker policy.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-005 — Privileged authenticator assurance

- **Related threat IDs:** THR-S-003, THR-S-007, THR-E-003, THR-I-009.
- **Affected component / boundary:** Backend; TB-01, TB-12, TB-08, TB-01, TB-08.
- **Preventive controls:** An approved policy MUST define privileged assurance, distinguish synced and hardware-bound credentials, and require hardware-bound credentials where justified and enforceable using verified evidence; unprovable assurance MUST be denied for restricted roles rather than inferred from marketing or UV alone.
- **Detection controls:** Record rejected origin/challenge/signature/assurance classes and counter anomalies.; Audit named operator access, hardware assurance and approval provenance.
- **Containment / recovery controls:** Atomically consume challenges, deny uncertain assurance and revoke suspect credentials. Re-enroll trusted factors through approved recovery and review RP configuration.; Revoke operator credentials and rebuild trust using incident runbooks.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-005 — deliver/preserve and verify “Privileged authenticator assurance” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-05, AC-06, AC-12, AC-17, AC-21, AC-24, AC-A-06, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Attestation availability, privacy and authenticator compatibility constrain enforceability. UV does not establish a specific biometric method, trustworthy display or hardware-bound key.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-006 — Biometric and PIN privacy

- **Related threat IDs:** THR-S-003, THR-I-004.
- **Affected component / boundary:** Authenticator → backend; TB-01, TB-12, TB-01, TB-10, TB-11.
- **Preventive controls:** The ERP backend MUST NOT collect or store biometric templates, biometric samples or device PINs; it MUST store only necessary credential public material and verification metadata.
- **Detection controls:** Record rejected origin/challenge/signature/assurance classes and counter anomalies.; Canary-secret scans and restricted review of redaction pipelines.
- **Containment / recovery controls:** Atomically consume challenges, deny uncertain assurance and revoke suspect credentials. Re-enroll trusted factors through approved recovery and review RP configuration.; Rotate exposed authority, remove unsafe collection and preserve safe incident evidence.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-006 — deliver/preserve and verify “Biometric and PIN privacy” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-06, AC-14, AC-24, AC-A-06.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. UV does not establish a specific biometric method, trustworthy display or hardware-bound key.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-MFA-007 — Conditional TOTP

- **Related threat IDs:** THR-T-007, THR-I-005, THR-E-003.
- **Affected component / boundary:** Backend; TB-01, TB-08, TB-04, TB-11.
- **Preventive controls:** TOTP MAY be an alternative only where the applicable assurance policy permits; secrets MUST be encrypted with separately managed keys, enrollment verified before activation, attempts throttled and accepted time steps consumed atomically to prevent replay across concurrent nodes.
- **Detection controls:** Notify on factor/recovery transitions; audit rejected OTP/challenge reuse.; Audit key access, backup exports and unexpected decryption.
- **Containment / recovery controls:** Step-up, account/purpose binding, atomic use and last-factor protection. Invalidate factors/recovery codes/sessions and re-establish approved assurance.; Rotate affected keys/factors, recover clean data and assess disclosure.
- **Implementation owner:** Identity/security architect.
- **Implementation phase:** Phase 04 assurance and implementation; Phase 10 integration; Phase 11 verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-MFA-007 — deliver/preserve and verify “Conditional TOTP” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-05, AC-06, AC-07, AC-12, AC-14, AC-23, AC-24, AC-A-06.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** TOTP is not phishing resistant and cannot satisfy hardware-bound WebAuthn assurance. A phished TOTP or fraudulent recovery evidence can still succeed within an allowed weaker policy.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-SESSION-001 — Architecture selection

- **Related threat IDs:** THR-E-010, THR-T-013, THR-E-012.
- **Affected component / boundary:** Browser ↔ modular monolith; TB-01 through TB-12.
- **Preventive controls:** Use native Spring Security server-side authentication sessions backed by Spring Session Redis, with PostgreSQL-authoritative account, membership, company, entitlement and revocation checks; ADR-0004 is accepted. Do not introduce browser JWT/DPoP, public-client refresh tokens, popup grants or redundant authorization servers.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-DESIGN (owner defined above).
- **Planned implementation task:** IMP-SESSION-001 — deliver/preserve and verify “Architecture selection” under the canonical requirement and proposed design after approval.
- **Verification method:** ADR review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24, AC-A-01, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-002 — Session identity and fixation

- **Related threat IDs:** THR-S-004.
- **Affected component / boundary:** Backend; TB-01.
- **Preventive controls:** Server-side sessions MUST use opaque CSPRNG identifiers with at least 128 bits of entropy, rotate identifiers after authentication and privilege/assurance changes, and retain server-side identity, assurance and expiry state.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.
- **Containment / recovery controls:** Expire/revoke session, require fresh step-up and deny session fixation. Rotate credentials, notify owner and review affected operations.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-002 — deliver/preserve and verify “Session identity and fixation” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-09, AC-14, AC-A-04.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-003 — Idle and absolute expiry

- **Related threat IDs:** THR-S-004, THR-I-001.
- **Affected component / boundary:** Backend; TB-01.
- **Preventive controls:** Sessions MUST reject at the idle or absolute deadline: ordinary 30 minutes idle/12 hours absolute, privileged 15 minutes/8 hours, step-up freshness 5 minutes. Activity MUST NOT extend absolute age. Privileged authentication remains blocked until required MFA exists.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.; User reports, session anomaly signals and privacy-aware terminal access reviews.
- **Containment / recovery controls:** Expire/revoke session, require fresh step-up and deny session fixation. Rotate credentials, notify owner and review affected operations.; Revoke session, clean endpoint and assess disclosed data.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-003 — deliver/preserve and verify “Idle and absolute expiry” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-08, AC-09, AC-14, AC-17, AC-A-04.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-004 — Durable revocation and no resurrection

- **Related threat IDs:** THR-T-006, THR-D-005, THR-D-006, THR-E-009, THR-T-011, THR-T-012.
- **Affected component / boundary:** Backend ↔ PostgreSQL/Redis; TB-04, TB-05, TB-04, TB-06, TB-11.
- **Preventive controls:** Logout, individual session revocation and global account revocation MUST invalidate authority durably before success is acknowledged; expired/revoked authority MUST NOT resurrect after Redis eviction, restart, stale PostgreSQL reads, failover, backup restoration or concurrent requests.
- **Detection controls:** Trace version/status transitions and verify concurrent revocation ordering.; Monitor fence lock waits, authorization age and stream/job chunk deadlines.
- **Containment / recovery controls:** Terminal state, primary checks and commit fences; no cached allow. Revoke affected epoch/sessions and reconcile in-flight effects.; Cancel stalled work, preserve idempotent outcomes and retry revocation without false success.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-004 — deliver/preserve and verify “Durable revocation and no resurrection” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-11, AC-15, AC-19, AC-23, AC-A-02, AC-A-03, AC-A-04, AC-A-07, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Already committed operations cannot be undone by logout; in-flight handling needs an explicit bound in the ADR. Already delivered data and provider effects committed before revocation cannot be recalled.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-005 — PostgreSQL and Redis security consistency

- **Related threat IDs:** THR-T-006, THR-D-004, THR-E-005, THR-E-009, THR-T-011.
- **Affected component / boundary:** Backend ↔ security state; TB-04, TB-05, TB-05, TB-11.
- **Preventive controls:** PostgreSQL writer state MUST decide current account, company, entitlement, membership, versions and durable revocation on every authenticated request. Redis MUST store HttpSession without independently authorizing stale/revoked state. Commit grant creation before context save and revocation before logout success; deny unknown authority and reconcile restores without assuming cross-store atomicity.
- **Detection controls:** Trace version/status transitions and verify concurrent revocation ordering.; Detect timeouts, unknown commits and security-state mismatch without logging secrets.
- **Containment / recovery controls:** Terminal state, primary checks and commit fences; no cached allow. Revoke affected epoch/sessions and reconcile in-flight effects.; Reconcile committed database state/outbox and resume only after authority is verified.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-005 — deliver/preserve and verify “PostgreSQL and Redis security consistency” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-15, AC-20, AC-23, AC-A-02, AC-A-03, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Already delivered data and provider effects committed before revocation cannot be recalled.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-006 — Session management and containment

- **Related threat IDs:** THR-S-004, THR-R-001, THR-I-001.
- **Affected component / boundary:** Backend; TB-01, TB-01, TB-04.
- **Preventive controls:** Users MUST be able to list and individually revoke their own active sessions/devices and revoke all sessions; suspicious reuse and assurance changes MUST trigger auditable risk-based step-up or revocation using defined signals rather than IP address alone.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.; Check event completeness and missing sequences against committed transitions.
- **Containment / recovery controls:** Expire/revoke session, require fresh step-up and deny session fixation. Rotate credentials, notify owner and review affected operations.; Preserve independent evidence, notify affected users and reconstruct timeline.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-006 — deliver/preserve and verify “Session management and containment” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-08, AC-09, AC-14, AC-17, AC-23, AC-A-04, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P1 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-007 — Cookie isolation

- **Related threat IDs:** THR-S-004, THR-T-002, THR-I-007, THR-E-011.
- **Affected component / boundary:** Browser ↔ origin; TB-01, TB-12, TB-02, TB-03, TB-04, TB-05.
- **Preventive controls:** Production session cookies MUST be Secure, HttpOnly, host-only without Domain, Path=/, SameSite=Strict and use the __Host- prefix. Each company hostname MUST have an independent session. SameSite MUST NOT replace native CSRF and exact Origin validation.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.; Audit denied Origin/CSRF/Host, unexpected framing and DNS ownership changes.
- **Containment / recovery controls:** Expire/revoke session, require fresh step-up and deny session fixation. Rotate credentials, notify owner and review affected operations.; Repair DNS/redirect policy, revoke exposed sessions and notify users.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-007 — deliver/preserve and verify “Cookie isolation” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-02, AC-08, AC-09, AC-14, AC-17, AC-21, AC-A-01, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-SESSION-008 — Failure and theft response

- **Related threat IDs:** THR-S-004, THR-D-001, THR-D-004, THR-E-005, THR-S-009.
- **Affected component / boundary:** Backend; TB-01, TB-04, TB-05, TB-05.
- **Preventive controls:** Required identity, revocation, tenant and policy infrastructure failures MUST deny protected access; stolen session cookies MUST be contained through bounded lifetime, step-up, anomaly response and revocation, with authentication credentials redacted from telemetry and responses.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.; Monitor attempt rates, hash queue/latency and per-account denial.
- **Containment / recovery controls:** Expire/revoke session, require fresh step-up and deny session fixation. Rotate credentials, notify owner and review affected operations.; Restore capacity and safe limiter policy without bypassing authentication.
- **Implementation owner:** Identity backend + data/operations owners.
- **Implementation phase:** Phase 04 sessions; Phase 10 browser; Phase 11 races/outages; Phase 12 durability. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-SESSION-008 — deliver/preserve and verify “Failure and theft response” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-07, AC-08, AC-09, AC-14, AC-15, AC-18, AC-20, AC-A-02, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** A valid stolen bearer cookie may work until expiry, revocation or an assurance check; malicious same-origin code can still issue requests. A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-TOKEN-001 — Conditional JWT validation

- **Related threat IDs:** Retired THR-S-005/THR-T-008 and browser-token proposal scenarios.
- **Affected component / boundary:** Superseded browser token architecture.
- **Preventive controls:** Retired by owner-approved ADR-0004; no browser JWT, refresh-token or DPoP flow is implemented.
- **Detection controls:** Review dependencies, endpoints and browser storage for prohibited credentials.
- **Containment / recovery controls:** Current native sessions retain expiry, durable revocation, MFA policy and recovery gates.
- **Implementation owner:** Identity/security owner.
- **Implementation phase:** Retired browser architecture; any future external integration needs a separate ADR.
- **Planned implementation task:** None for browser tokens; retain identifier without reuse.
- **Verification method:** Scope review, AC-S-08/10; never report obsolete protocol tests as passed.
- **Acceptance scenario IDs:** Retired AC-10/11; current scope AC-S-08/10.
- **Current implementation status:** Retired.
- **Evidence reference:** ADR-0004 and Phase 04 foundation handoff.
- **Residual risk:** Valid stolen session cookies may replay; native sessions provide no equivalent browser proof-of-possession protection.
- **Pending decisions:** None for this retired record.
- **Release gate:** Preserve all unrelated security requirements; do not add browser token flows under this phase.

### SEC-TOKEN-002 — Key lifecycle and short lifetimes

- **Related threat IDs:** Retired THR-S-005/THR-T-008 and browser-token proposal scenarios.
- **Affected component / boundary:** Superseded browser token architecture.
- **Preventive controls:** Retired by owner-approved ADR-0004; no browser JWT, refresh-token or DPoP flow is implemented.
- **Detection controls:** Review dependencies, endpoints and browser storage for prohibited credentials.
- **Containment / recovery controls:** Current native sessions retain expiry, durable revocation, MFA policy and recovery gates.
- **Implementation owner:** Identity/security owner.
- **Implementation phase:** Retired browser architecture; any future external integration needs a separate ADR.
- **Planned implementation task:** None for browser tokens; retain identifier without reuse.
- **Verification method:** Scope review, AC-S-08/10; never report obsolete protocol tests as passed.
- **Acceptance scenario IDs:** Retired AC-10/11; current scope AC-S-08/10.
- **Current implementation status:** Retired.
- **Evidence reference:** ADR-0004 and Phase 04 foundation handoff.
- **Residual risk:** Valid stolen session cookies may replay; native sessions provide no equivalent browser proof-of-possession protection.
- **Pending decisions:** None for this retired record.
- **Release gate:** Preserve all unrelated security requirements; do not add browser token flows under this phase.

### SEC-TOKEN-003 — Opaque refresh protection

- **Related threat IDs:** Retired THR-S-005/THR-T-008 and browser-token proposal scenarios.
- **Affected component / boundary:** Superseded browser token architecture.
- **Preventive controls:** Retired by owner-approved ADR-0004; no browser JWT, refresh-token or DPoP flow is implemented.
- **Detection controls:** Review dependencies, endpoints and browser storage for prohibited credentials.
- **Containment / recovery controls:** Current native sessions retain expiry, durable revocation, MFA policy and recovery gates.
- **Implementation owner:** Identity/security owner.
- **Implementation phase:** Retired browser architecture; any future external integration needs a separate ADR.
- **Planned implementation task:** None for browser tokens; retain identifier without reuse.
- **Verification method:** Scope review, AC-S-08/10; never report obsolete protocol tests as passed.
- **Acceptance scenario IDs:** Retired AC-10/11; current scope AC-S-08/10.
- **Current implementation status:** Retired.
- **Evidence reference:** ADR-0004 and Phase 04 foundation handoff.
- **Residual risk:** Valid stolen session cookies may replay; native sessions provide no equivalent browser proof-of-possession protection.
- **Pending decisions:** None for this retired record.
- **Release gate:** Preserve all unrelated security requirements; do not add browser token flows under this phase.

### SEC-TOKEN-004 — Atomic rotation and concurrency

- **Related threat IDs:** Retired THR-S-005/THR-T-008 and browser-token proposal scenarios.
- **Affected component / boundary:** Superseded browser token architecture.
- **Preventive controls:** Retired by owner-approved ADR-0004; no browser JWT, refresh-token or DPoP flow is implemented.
- **Detection controls:** Review dependencies, endpoints and browser storage for prohibited credentials.
- **Containment / recovery controls:** Current native sessions retain expiry, durable revocation, MFA policy and recovery gates.
- **Implementation owner:** Identity/security owner.
- **Implementation phase:** Retired browser architecture; any future external integration needs a separate ADR.
- **Planned implementation task:** None for browser tokens; retain identifier without reuse.
- **Verification method:** Scope review, AC-S-08/10; never report obsolete protocol tests as passed.
- **Acceptance scenario IDs:** Retired AC-10/11; current scope AC-S-08/10.
- **Current implementation status:** Retired.
- **Evidence reference:** ADR-0004 and Phase 04 foundation handoff.
- **Residual risk:** Valid stolen session cookies may replay; native sessions provide no equivalent browser proof-of-possession protection.
- **Pending decisions:** None for this retired record.
- **Release gate:** Preserve all unrelated security requirements; do not add browser token flows under this phase.

### SEC-TOKEN-005 — Conditional standardized DPoP

- **Related threat IDs:** Retired THR-S-005/THR-T-008 and browser-token proposal scenarios.
- **Affected component / boundary:** Superseded browser token architecture.
- **Preventive controls:** Retired by owner-approved ADR-0004; no browser JWT, refresh-token or DPoP flow is implemented.
- **Detection controls:** Review dependencies, endpoints and browser storage for prohibited credentials.
- **Containment / recovery controls:** Current native sessions retain expiry, durable revocation, MFA policy and recovery gates.
- **Implementation owner:** Identity/security owner.
- **Implementation phase:** Retired browser architecture; any future external integration needs a separate ADR.
- **Planned implementation task:** None for browser tokens; retain identifier without reuse.
- **Verification method:** Scope review, AC-S-08/10; never report obsolete protocol tests as passed.
- **Acceptance scenario IDs:** Retired AC-10/11; current scope AC-S-08/10.
- **Current implementation status:** Retired.
- **Evidence reference:** ADR-0004 and Phase 04 foundation handoff.
- **Residual risk:** Valid stolen session cookies may replay; native sessions provide no equivalent browser proof-of-possession protection.
- **Pending decisions:** None for this retired record.
- **Release gate:** Preserve all unrelated security requirements; do not add browser token flows under this phase.

### SEC-BROWSER-001 — XSS-safe rendering

- **Related threat IDs:** THR-T-001, THR-I-009.
- **Affected component / boundary:** Untrusted content → React/DOM; TB-01, TB-09.
- **Preventive controls:** React rendering MUST use safe text binding and context-appropriate output encoding; stored, reflected and DOM input MUST NOT reach unsafe HTML/script/URL sinks. Necessary rich HTML MUST use a reviewed maintained sanitizer with strict allowed content.
- **Detection controls:** Collect redacted CSP/Trusted Types violations and integrity/dependency alerts.
- **Containment / recovery controls:** Block unapproved scripts, disable affected release/content and revoke sessions as needed. Deploy reviewed assets, sanitize stored payloads and investigate operations.
- **Implementation owner:** Frontend + identity + infrastructure owners.
- **Implementation phase:** Phase 04 policy; Phase 10 frontend integration; Phase 11 browser tests; Phase 12 headers/DNS. Legacy workstream: FRONTEND (owner defined above).
- **Planned implementation task:** IMP-BROWSER-001 — deliver/preserve and verify “XSS-safe rendering” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-17, AC-22, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BROWSER-001 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Malicious same-origin code can use current authority despite HttpOnly cookies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-BROWSER-002 — Browser execution policy

- **Related threat IDs:** THR-T-001, THR-I-009.
- **Affected component / boundary:** Origin → browser; TB-01, TB-09.
- **Preventive controls:** Production MUST enforce a strict nonce/hash CSP without unsafe script exceptions; Trusted Types SHOULD protect sinks where supported. Policy rollout MUST monitor violations without exposing secrets.
- **Detection controls:** Collect redacted CSP/Trusted Types violations and integrity/dependency alerts.
- **Containment / recovery controls:** Block unapproved scripts, disable affected release/content and revoke sessions as needed. Deploy reviewed assets, sanitize stored payloads and investigate operations.
- **Implementation owner:** Frontend + identity + infrastructure owners.
- **Implementation phase:** Phase 04 policy; Phase 10 frontend integration; Phase 11 browser tests; Phase 12 headers/DNS. Legacy workstream: FRONTEND + INFRA (owner defined above).
- **Planned implementation task:** IMP-BROWSER-002 — deliver/preserve and verify “Browser execution policy” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-17, AC-22, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [security request boundary filter](../../Backend/src/main/java/com/saaserp/erp/platform/audit/infrastructure/SecurityRequestBoundaryFilter.java) and [HTTP integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java). Restrictive API response CSP is present; a nonce/hash policy for the served React application and browser-level CSP/Trusted Types verification remain outstanding.
- **Residual risk:** CSP/Trusted Types depend on correct policy and browser support; allowed scripts can still be malicious. Malicious same-origin code can use current authority despite HttpOnly cookies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-BROWSER-003 — Third-party JavaScript

- **Related threat IDs:** THR-T-001.
- **Affected component / boundary:** Dependencies/CDN → browser; TB-01, TB-09.
- **Preventive controls:** Third-party scripts MUST be inventoried, minimized and explicitly approved; immutable external resources MUST use Subresource Integrity where applicable, and npm dependency changes MUST receive supply-chain review.
- **Detection controls:** Collect redacted CSP/Trusted Types violations and integrity/dependency alerts.
- **Containment / recovery controls:** Block unapproved scripts, disable affected release/content and revoke sessions as needed. Deploy reviewed assets, sanitize stored payloads and investigate operations.
- **Implementation owner:** Frontend + identity + infrastructure owners.
- **Implementation phase:** Phase 04 policy; Phase 10 frontend integration; Phase 11 browser tests; Phase 12 headers/DNS. Legacy workstream: FRONTEND + SUPPLY (owner defined above).
- **Planned implementation task:** IMP-BROWSER-003 — deliver/preserve and verify “Third-party JavaScript” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-17, AC-22.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BROWSER-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Malicious same-origin code can use current authority despite HttpOnly cookies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-BROWSER-004 — Browser isolation headers

- **Related threat IDs:** THR-T-002.
- **Affected component / boundary:** Browser ↔ other origins; TB-12.
- **Preventive controls:** Production MUST prevent clickjacking using CSP frame-ancestors and compatible legacy controls, restrict powerful features with Permissions Policy, and SHOULD apply compatible cross-origin resource/opener/embedder policies after testing login, payments and downloads.
- **Detection controls:** Audit denied Origin/CSRF/Host, unexpected framing and DNS ownership changes.
- **Containment / recovery controls:** Host-only cookies, exact Origin, synchronizer tokens and allowlisted redirects; disable dangling aliases. Repair DNS/redirect policy, revoke exposed sessions and notify users.
- **Implementation owner:** Frontend + identity + infrastructure owners.
- **Implementation phase:** Phase 04 policy; Phase 10 frontend integration; Phase 11 browser tests; Phase 12 headers/DNS. Legacy workstream: FRONTEND + INFRA (owner defined above).
- **Planned implementation task:** IMP-BROWSER-004 — deliver/preserve and verify “Browser isolation headers” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-17, AC-A-01.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [security request boundary filter](../../Backend/src/main/java/com/saaserp/erp/platform/audit/infrastructure/SecurityRequestBoundaryFilter.java) and [HTTP integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java). API clickjacking and related response headers are tested; production edge policy and full cross-origin isolation remain operator/browser-verification gates.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. SameSite alone does not defend against same-site sibling origins or deceptive user consent.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P1 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-BROWSER-005 — CSRF, CORS and origin

- **Related threat IDs:** THR-T-002, THR-T-013.
- **Affected component / boundary:** Other origins → backend; TB-12.
- **Preventive controls:** Cookie-authenticated state changes MUST use robust CSRF tokens and server-side Origin validation, prohibit state changes on safe methods, and use exact approved CORS origins/methods/headers without credentialed wildcard or arbitrary reflection.
- **Detection controls:** Audit denied Origin/CSRF/Host, unexpected framing and DNS ownership changes.
- **Containment / recovery controls:** Host-only cookies, exact Origin, synchronizer tokens and allowlisted redirects; disable dangling aliases. Repair DNS/redirect policy, revoke exposed sessions and notify users.
- **Implementation owner:** Frontend + identity + infrastructure owners.
- **Implementation phase:** Phase 04 policy; Phase 10 frontend integration; Phase 11 browser tests; Phase 12 headers/DNS. Legacy workstream: P4-IMPLEMENT + FRONTEND (owner defined above).
- **Planned implementation task:** IMP-BROWSER-005 — deliver/preserve and verify “CSRF, CORS and origin” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-17, AC-A-01, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [authentication configuration](../../Backend/src/main/java/com/saaserp/erp/config/AuthenticationConfig.java), [authentication integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java) and [security request boundary filter](../../Backend/src/main/java/com/saaserp/erp/platform/audit/infrastructure/SecurityRequestBoundaryFilter.java). Exact-origin, CSRF, restrictive CORS and security headers have integration coverage; real-browser, edge/proxy and production origin verification remain outstanding.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. SameSite alone does not defend against same-site sibling origins or deceptive user consent.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-BROWSER-006 — Sensitive browser storage

- **Related threat IDs:** THR-S-004, THR-I-001, THR-I-004, THR-I-009, THR-T-013.
- **Affected component / boundary:** Browser storage/runtime; TB-01, TB-01, TB-10, TB-11.
- **Preventive controls:** Authentication credentials MUST NOT be stored in localStorage; sessionStorage, IndexedDB, service-worker caches and URLs MUST NOT hold bearer credentials or unnecessary sensitive data. BFF and HttpOnly cookies MUST be evaluated with their same-origin request limitations.
- **Detection controls:** Detect unusual session activity, credential changes and user-reported sessions.; User reports, session anomaly signals and privacy-aware terminal access reviews.
- **Containment / recovery controls:** Expire/revoke session, require fresh step-up and deny session fixation. Rotate credentials, notify owner and review affected operations.; Revoke session, clean endpoint and assess disclosed data.
- **Implementation owner:** Frontend + identity + infrastructure owners.
- **Implementation phase:** Phase 04 policy; Phase 10 frontend integration; Phase 11 browser tests; Phase 12 headers/DNS. Legacy workstream: P4-DESIGN → FRONTEND (owner defined above).
- **Planned implementation task:** IMP-BROWSER-006 — deliver/preserve and verify “Sensitive browser storage” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-08, AC-09, AC-14, AC-17, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BROWSER-006 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** CSP and HttpOnly cannot fully prevent malicious code executing in a legitimate authenticated browser from exercising that user’s authority. A valid stolen bearer cookie may work before expiry/revocation; HttpOnly does not stop authenticated malicious requests.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-AUTHZ-001 — Server RBAC and fine permissions

- **Related threat IDs:** THR-I-002, THR-E-001, THR-E-002, THR-E-011.
- **Affected component / boundary:** Backend; TB-01, TB-04, TB-01, TB-12, TB-01, TB-06.
- **Preventive controls:** Backend application boundaries MUST enforce company-specific RBAC and fine-grained permissions at function, object and field levels; authentication alone MUST NOT authorize any resource.
- **Detection controls:** Audit object/field denials and unusual bulk access by trusted context.; Audit role/company selection mismatches and forbidden function attempts.
- **Containment / recovery controls:** Company/function/object/field mediation, response DTOs and bounded collections. Revoke abuse, investigate exports and notify under approved incident policy.; Revoke affected grants/sessions and investigate improper operations.
- **Implementation owner:** Identity/platform + module owners.
- **Implementation phase:** Phase 04 authorization; Phase 05 grants; Phase 07 membership/routing integration; Phases 08–10 resource enforcement. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-001 — deliver/preserve and verify “Server RBAC and fine permissions” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-04, AC-18, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Company authorization service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/AuthorizationService.java), [platform authorization service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/PlatformAuthorizationService.java), [company role assignment service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/CompanyRoleAssignmentService.java), V8 platform migration, and [authorization integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java). Company role/permission checks are database-backed and deny by default. Platform authorization requires an independently host-bound session and recorded hardware-attestation evidence; no provisioning/verifier or HTTP administrative route exists. Object/field enforcement on future tenant modules remains deferred.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Authorized bulk readers can still disclose data deliberately or through a compromised device.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTHZ-002 — Ownership and mass assignment

- **Related threat IDs:** THR-I-002, THR-E-002.
- **Affected component / boundary:** Backend; TB-01, TB-04, TB-01, TB-06.
- **Preventive controls:** Every resource operation MUST verify ownership and authorized company scope, including nested IDs, exports and bulk operations; request DTOs MUST allowlist writable fields and reject privilege, ownership or workflow-state mass assignment.
- **Detection controls:** Audit object/field denials and unusual bulk access by trusted context.; Correlate denied ownership/field writes and unusual bulk patterns.
- **Containment / recovery controls:** Company/function/object/field mediation, response DTOs and bounded collections. Revoke abuse, investigate exports and notify under approved incident policy.; Restore authorized state, revoke abuse and verify all alternate endpoints.
- **Implementation owner:** Identity/platform + module owners.
- **Implementation phase:** Phase 04 authorization; Phase 05 grants; Phase 07 membership/routing integration; Phases 08–10 resource enforcement. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-002 — deliver/preserve and verify “Ownership and mass assignment” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-18.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-AUTHZ-002 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Authorized bulk readers can still disclose data deliberately or through a compromised device.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTHZ-003 — Tenant membership and switching

- **Related threat IDs:** THR-S-008, THR-I-002, THR-E-001, THR-E-011.
- **Affected component / boundary:** Identity/membership → tenant context; TB-01, TB-06, TB-01, TB-04, TB-01, TB-12.
- **Preventive controls:** Authenticated tenant resolution MUST verify current company membership and policy before binding context; tenant switching MUST start a fresh authorized scope, invalidate incompatible state and never trust a client tenant identifier alone.
- **Detection controls:** Audit rejected company selections and worker identity/policy mismatches.; Audit object/field denials and unusual bulk access by trusted context.
- **Containment / recovery controls:** Validate current writer membership before scope binding; quarantine unauthorized jobs. Revoke affected delegated authority and reconcile job effects.; Revoke abuse, investigate exports and notify under approved incident policy.
- **Implementation owner:** Identity/platform + module owners.
- **Implementation phase:** Phase 04 authorization; Phase 05 grants; Phase 07 membership/routing integration; Phases 08–10 resource enforcement. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-003 — deliver/preserve and verify “Tenant membership and switching” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-18, AC-A-01, AC-A-04, AC-A-07, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [session authority filter](../../Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SessionAuthorityFilter.java) and [authentication integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java). Current membership, company status, entitlement, hostname and security-version checks precede company session use. Tenant schema/business-resource binding and tenant switching remain future integration work.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Any trusted internal code can bind a context; application-process compromise is a separate risk.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-AUTHZ-004 — Entitlement gating

- **Related threat IDs:** THR-S-006, THR-E-006.
- **Affected component / boundary:** Billing → business modules; TB-07.
- **Preventive controls:** Server-side entitlement checks MUST gate subscribed operations and provisioning after verified payment; expiry MUST restrict applicable access while preserving customer data under retention policy.
- **Detection controls:** Alert on signature/freshness failures and settlement/entitlement mismatch.; Reconcile paid entitlements against provider events and provisioning inventory.
- **Containment / recovery controls:** Reject unverified events; hold entitlement/provisioning transitions. Reconcile provider records, revoke unpaid access while preserving customer data.; Restrict unpaid operations, reconcile resources and preserve company data.
- **Implementation owner:** Billing/entitlement + provisioning owners.
- **Implementation phase:** Phase 06 verified entitlements; Phase 07 provisioning gate; Phases 08–09 paid-operation gates. Legacy workstream: BILLING (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-004 — deliver/preserve and verify “Entitlement gating” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [session authority filter](../../Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/SessionAuthorityFilter.java) and [authentication integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java). Company entitlement is checked for authentication/session authority only; paid-operation and provisioning gates remain future work.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A compromised provider key can authenticate false messages until rotation/reconciliation.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTHZ-005 — Permission revocation bound

- **Related threat IDs:** THR-T-006, THR-D-005, THR-E-009.
- **Affected component / boundary:** Backend; TB-04, TB-05, TB-04, TB-06, TB-11.
- **Preventive controls:** Role, membership and assurance revocation MUST take effect immediately at authoritative checks or within an explicitly approved maximum propagation bound; proposed bound is 60 seconds for ordinary permissions and immediate authoritative checks for privileged mutations. Unknown state MUST deny access.
- **Detection controls:** Trace version/status transitions and verify concurrent revocation ordering.; Monitor fence lock waits, authorization age and stream/job chunk deadlines.
- **Containment / recovery controls:** Terminal state, primary checks and commit fences; no cached allow. Revoke affected epoch/sessions and reconcile in-flight effects.; Cancel stalled work, preserve idempotent outcomes and retry revocation without false success.
- **Implementation owner:** Identity/platform + module owners.
- **Implementation phase:** Phase 04 authorization; Phase 05 grants; Phase 07 membership/routing integration; Phases 08–10 resource enforcement. Legacy workstream: P4-DESIGN → P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-005 — deliver/preserve and verify “Permission revocation bound” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-15, AC-19, AC-23, AC-A-02, AC-A-03, AC-A-07, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [authorization repository](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/infrastructure/JdbcAuthorizationRepository.java) and [authorization integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java). Current decisions read authoritative PostgreSQL state; this does not establish a commit fence for future tenant business transactions.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Already delivered data and provider effects committed before revocation cannot be recalled.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-AUTHZ-006 — Transaction-bound step-up

- **Related threat IDs:** THR-R-002, THR-E-007, THR-I-009.
- **Affected component / boundary:** Authenticator → sensitive transaction; TB-06, TB-08.
- **Preventive controls:** Sensitive mutations MUST require fresh Step-Up WebAuthn and a single-use, expiring approval bound to actor, company, action, target, amount/payee or other material fields and operation digest; changing those fields MUST invalidate approval.
- **Detection controls:** Correlate requested digest, approval, current grants and final ledger event.; Audit approver independence, digest changes and repeated approval use.
- **Containment / recovery controls:** Single-use transaction binding, distinct approvers and execution revalidation. Reconcile financial state and investigate through independent preserved records.; Freeze affected workflows, reconcile effects and review privileged assignments.
- **Implementation owner:** Identity + billing/ERP operation owners.
- **Implementation phase:** Phase 04 step-up/approval primitive; Phase 06 critical billing; Phases 08–09 critical ERP; Phase 11 race tests. Legacy workstream: P4-IMPLEMENT + ERP (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-006 — deliver/preserve and verify “Transaction-bound step-up” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-13, AC-17, AC-19, AC-24, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [step-up service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/StepUpAuthorizationService.java), [step-up repository](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/infrastructure/JdbcStepUpRepository.java), [company role assignment service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/CompanyRoleAssignmentService.java), and [integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java). Native WebAuthn step-up is bound to session, company, host, operation and details digest, expires and is single-use. Company-role assignment independently derives the Java-compatible operation digest in PostgreSQL through [V9 authorization correction](../../Backend/src/main/resources/db/migration/platform/V9__bind_authorization_operations_and_retire_obsolete_functions.sql), consumes the correlated current dual approval and changes membership atomically; future business mutations still do not consume step-up/approval in their business transaction.
- **Residual risk:** WebAuthn proves an authenticator ceremony, not trusted display of business details on a compromised browser. WebAuthn does not guarantee truthful display; colluding approvers remain possible.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUTHZ-007 — Independent critical approval

- **Related threat IDs:** THR-R-002, THR-E-007, THR-I-009.
- **Affected component / boundary:** Requester ↔ approver ↔ application; TB-06, TB-08.
- **Preventive controls:** Selected critical operations MUST require two distinct authorized people under a documented threshold/operation policy, prevent self-approval and conflicting roles, and revalidate both grants and operation state at execution.
- **Detection controls:** Correlate requested digest, approval, current grants and final ledger event.; Audit approver independence, digest changes and repeated approval use.
- **Containment / recovery controls:** Single-use transaction binding, distinct approvers and execution revalidation. Reconcile financial state and investigate through independent preserved records.; Freeze affected workflows, reconcile effects and review privileged assignments.
- **Implementation owner:** Platform/financial + operations owners.
- **Implementation phase:** Phase 05 privileged grants; Phase 06 finance; Phases 08–09 critical ERP; Phase 12 production approval. Legacy workstream: ERP + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-AUTHZ-007 — deliver/preserve and verify “Independent critical approval” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-13, AC-17, AC-19, AC-24, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [dual-approval service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/DualApprovalService.java), [company role assignment service](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/application/CompanyRoleAssignmentService.java), [V9 authorization correction](../../Backend/src/main/resources/db/migration/platform/V9__bind_authorization_operations_and_retire_obsolete_functions.sql), and [PostgreSQL integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java). Company-role assignment revalidates both current grants, requires an independent approver other than the target, independently binds the approved digest to company, target, expected role and requested role, and consumes approval with the membership mutation. Restricted-runtime SQL regressions cover original-digest substitution, NULL bindings, expiry, replay, concurrent single use, revoked approver authority and rollback. Production policy thresholds and integration with business mutations remain absent.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. WebAuthn does not guarantee truthful display; colluding approvers remain possible.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-API-001 — Strict API contracts

- **Related threat IDs:** THR-T-003, THR-I-002, THR-D-002, THR-E-002, THR-D-008, THR-T-013.
- **Affected component / boundary:** Backend; TB-02, TB-03, TB-01, TB-04, TB-03, TB-04, TB-05, TB-06, TB-01, TB-06.
- **Preventive controls:** Versioned /api/v1 endpoints MUST accept strict bounded DTO/schema inputs, reject invalid types/ranges and unknown writable fields, return response DTOs with consistent non-sensitive errors, restrict HTTP methods and paginate large collections.
- **Detection controls:** Alert on malformed framing, foreign hosts and anomalous edge/origin request counts.; Audit object/field denials and unusual bulk access by trusted context.
- **Containment / recovery controls:** Reject ambiguity and restrict origin/proxy peers and header provenance. Patch parser/proxy configuration and review affected logs/sessions.; Revoke abuse, investigate exports and notify under approved incident policy.
- **Implementation owner:** Backend + affected module owners.
- **Implementation phase:** Phase 04 API policy; Phases 05–10 feature gates; Phase 11 adversarial verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-API-001 — deliver/preserve and verify “Strict API contracts” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-16, AC-18, AC-21, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [security request boundary filter](../../Backend/src/main/java/com/saaserp/erp/platform/audit/infrastructure/SecurityRequestBoundaryFilter.java), strict Jackson configuration in [application.properties](../../Backend/src/main/resources/application.properties), and [authentication integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java). Request-size and unknown-property checks are covered; endpoint-specific DTO, pagination and module contracts remain future work.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Different protocol implementations may have undiscovered parser discrepancies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-API-002 — Query and injection safety

- **Related threat IDs:** THR-T-004.
- **Affected component / boundary:** Backend; TB-04.
- **Preventive controls:** Database values MUST use parameterized queries; dynamic identifiers MUST come only from reviewed validated internal mappings; untrusted SQL fragments MUST NOT be accepted.
- **Detection controls:** Audit invalid identifier attempts and unexpected effective schema/path.
- **Containment / recovery controls:** Parameterized values, internal schema derivation and fail-closed provider route. Restore affected data and repair unsafe query paths with isolation regression evidence.
- **Implementation owner:** Backend + affected module owners.
- **Implementation phase:** Phase 04 API policy; Phases 05–10 feature gates; Phase 11 adversarial verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-API-002 — deliver/preserve and verify “Query and injection safety” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-16, AC-18, AC-20.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Parameterized queries do not constrain hostile fully qualified SQL from an authorized shared process.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-API-003 — SSRF and egress

- **Related threat IDs:** THR-I-006.
- **Affected component / boundary:** Application → outbound network; TB-06, TB-10.
- **Preventive controls:** Server-initiated requests MUST validate destinations against purpose-specific allowlists, prevent private/loopback/link-local/metadata access, revalidate DNS/redirect targets and use bounded timeouts and response sizes; network egress SHOULD enforce the same policy.
- **Detection controls:** Alert on blocked private destinations, DNS changes and unusual outbound flows.
- **Containment / recovery controls:** Purpose allowlists, DNS/redirect revalidation, bounded fetch and network egress restrictions. Rotate disclosed credentials and isolate vulnerable fetch workflow.
- **Implementation owner:** Backend outbound-adapter + infrastructure owners.
- **Implementation phase:** Phase 04 SSRF policy; each Phase 05–10 outbound feature; Phase 12 egress restriction. Legacy workstream: P4-IMPLEMENT + INFRA (owner defined above).
- **Planned implementation task:** IMP-API-003 — deliver/preserve and verify “SSRF and egress” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-API-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. DNS/network changes and approved compromised destinations remain residual risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-API-004 — Resource abuse limits

- **Related threat IDs:** THR-T-003, THR-D-001, THR-D-002, THR-D-003, THR-D-005, THR-D-008.
- **Affected component / boundary:** Backend; TB-02, TB-03, TB-01, TB-03, TB-04, TB-05, TB-06, TB-04, TB-06.
- **Preventive controls:** Endpoints MUST enforce request/body/upload sizes, timeouts, bounded pagination, tenant/user resource quotas and rate/concurrency limits with trusted proxy handling; limits MUST protect CPU, memory, connections and background work.
- **Detection controls:** Alert on malformed framing, foreign hosts and anomalous edge/origin request counts.; Monitor attempt rates, hash queue/latency and per-account denial.
- **Containment / recovery controls:** Reject ambiguity and restrict origin/proxy peers and header provenance. Patch parser/proxy configuration and review affected logs/sessions.; Restore capacity and safe limiter policy without bypassing authentication.
- **Implementation owner:** Backend + affected module owners.
- **Implementation phase:** Phase 04 API policy; Phases 05–10 feature gates; Phase 11 adversarial verification. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-API-004 — deliver/preserve and verify “Resource abuse limits” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-07, AC-08, AC-15, AC-16, AC-18, AC-19, AC-21, AC-A-02, AC-A-07, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [security request boundary filter](../../Backend/src/main/java/com/saaserp/erp/platform/audit/infrastructure/SecurityRequestBoundaryFilter.java), [API quota filter](../../Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/ApiRequestQuotaFilter.java), [Redis API quota](../../Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/RedisApiRequestQuota.java), [authentication throttle](../../Backend/src/main/java/com/saaserp/erp/platform/identity/infrastructure/AuthenticationThrottle.java), and [quota/security integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java). Authentication attempt limits, request-body bounds and fail-closed Redis fixed-window limits of 300/source/minute and 120/authenticated company-account/minute are implemented. Upload/concurrency limits, deployment proxy topology and production rate/abuse tuning remain outstanding.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Different protocol implementations may have undiscovered parser discrepancies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-API-005 — Atomic and idempotent operations

- **Related threat IDs:** THR-T-006, THR-T-009, THR-D-005, THR-T-011, THR-T-012.
- **Affected component / boundary:** Backend; TB-04, TB-05, TB-06, TB-07, TB-04, TB-06.
- **Preventive controls:** Mutations requiring retry safety MUST bind idempotency keys to actor/company/action and payload digest, persist outcomes atomically with business state and enforce transaction/concurrency invariants using constraints or locking.
- **Detection controls:** Trace version/status transitions and verify concurrent revocation ordering.; Reconcile payment/ledger/stock invariants and duplicate idempotency keys.
- **Containment / recovery controls:** Terminal state, primary checks and commit fences; no cached allow. Revoke affected epoch/sessions and reconcile in-flight effects.; Reconcile provider/ledger data and apply audited compensating operations.
- **Implementation owner:** Backend + billing/ERP transaction owners.
- **Implementation phase:** Phase 04 operation semantics; Phase 06 billing idempotency; Phase 07 atomic authority fence; Phases 08–09 transactions. Legacy workstream: P4-IMPLEMENT + ERP/BILLING (owner defined above).
- **Planned implementation task:** IMP-API-005 — deliver/preserve and verify “Atomic and idempotent operations” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03, AC-08, AC-11, AC-15, AC-19, AC-A-02, AC-A-07, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [dual-approval repository](../../Backend/src/main/java/com/saaserp/erp/platform/authorization/infrastructure/JdbcDualApprovalRepository.java) and [PostgreSQL integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java). Authorization primitives use locked atomic transitions and idempotency; atomicity with future business state is not implemented or claimed.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Already delivered data and provider effects committed before revocation cannot be recalled.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-API-006 — Secure uploads

- **Related threat IDs:** THR-T-010, THR-E-008.
- **Affected component / boundary:** Client file → storage → download; TB-06, TB-09, TB-06, TB-09, TB-08.
- **Preventive controls:** Uploads MUST require authorization and tenant ownership, bounded count/size, content-type/signature validation, generated storage names, non-executable private storage and scanning/quarantine as risk requires; downloads MUST reauthorize and safely set content headers.
- **Detection controls:** Scan/quarantine files, monitor integrity and verify artifact provenance.; Runtime process/file/egress monitoring and security-analysis findings.
- **Containment / recovery controls:** Non-executable private storage, restricted runtime mounts and verified immutable deployment. Restore clean artifact/data and rotate credentials if execution occurred.; Isolate compromised host, rotate secrets and deploy clean verified artifact with restored data.
- **Implementation owner:** File feature + frontend/infrastructure owners.
- **Implementation phase:** Phase 08 shared file capability; Phase 09 vertical consumers; Phase 10 browser; Phase 12 storage before exposure. Legacy workstream: ERP (owner defined above).
- **Planned implementation task:** IMP-API-006 — deliver/preserve and verify “Secure uploads” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21, AC-22.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-API-006 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Signing proves provenance, not absence of malicious authorized code.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-BUSINESS-001 — Server financial calculations

- **Related threat IDs:** THR-T-009, THR-R-002.
- **Affected component / boundary:** Client → financial domain; TB-06, TB-07, TB-06, TB-08.
- **Preventive controls:** Sales, invoice and pricing modules MUST calculate prices, taxes, totals and discounts from authorized server records using explicit rounding/currency rules; unauthorized discounts/refunds and client-controlled workflow state MUST be rejected.
- **Detection controls:** Reconcile payment/ledger/stock invariants and duplicate idempotency keys.; Correlate requested digest, approval, current grants and final ledger event.
- **Containment / recovery controls:** Server calculations, locked/constraint-backed invariants and payload-bound idempotency. Reconcile provider/ledger data and apply audited compensating operations.; Reconcile financial state and investigate through independent preserved records.
- **Implementation owner:** ERP sales/accounting + billing owners.
- **Implementation phase:** Phase 06 billing calculations; Phase 08 sales/invoices; Phase 09 vertical adaptations. Legacy workstream: ERP-SALES/BILLING (owner defined above).
- **Planned implementation task:** IMP-BUSINESS-001 — deliver/preserve and verify “Server financial calculations” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03, AC-13, AC-19.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BUSINESS-001 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Authorized insiders can misuse permitted discounts or collude; business policy still matters.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-BUSINESS-002 — Authentic payment and replay safety

- **Related threat IDs:** THR-S-006, THR-T-009, THR-E-006.
- **Affected component / boundary:** Payment provider → billing; TB-07, TB-06, TB-07.
- **Preventive controls:** Billing MUST verify payment webhook signatures over original bytes, provider/account/environment and freshness, process unique event IDs idempotently and reconcile authoritative payment state before entitlement changes; frontend confirmation MUST NOT provision resources.
- **Detection controls:** Alert on signature/freshness failures and settlement/entitlement mismatch.; Reconcile payment/ledger/stock invariants and duplicate idempotency keys.
- **Containment / recovery controls:** Reject unverified events; hold entitlement/provisioning transitions. Reconcile provider records, revoke unpaid access while preserving customer data.; Reconcile provider/ledger data and apply audited compensating operations.
- **Implementation owner:** Billing/payment gateway owner.
- **Implementation phase:** Phase 06 signed gateway integration, replay prevention and reconciliation before paid activation. Legacy workstream: BILLING (owner defined above).
- **Planned implementation task:** IMP-BUSINESS-002 — deliver/preserve and verify “Authentic payment and replay safety” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03, AC-19.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BUSINESS-002 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A compromised provider key can authenticate false messages until rotation/reconciliation.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-BUSINESS-003 — Provisioning entitlement

- **Related threat IDs:** THR-S-006, THR-E-006.
- **Affected component / boundary:** Billing → provisioner → PostgreSQL; TB-07.
- **Preventive controls:** Provisioning MUST require verified payment and current server entitlement, use validated tenant identities and restricted privileges, and be idempotent and recoverable without granting authority on partial failure.
- **Detection controls:** Alert on signature/freshness failures and settlement/entitlement mismatch.; Reconcile paid entitlements against provider events and provisioning inventory.
- **Containment / recovery controls:** Reject unverified events; hold entitlement/provisioning transitions. Reconcile provider records, revoke unpaid access while preserving customer data.; Restrict unpaid operations, reconcile resources and preserve company data.
- **Implementation owner:** Billing + provisioning/data owners.
- **Implementation phase:** Phase 06 verified eligibility; Phase 07 restricted idempotent provisioning before tenant activation. Legacy workstream: BILLING → TENANT-ACTIVATION (owner defined above).
- **Planned implementation task:** IMP-BUSINESS-003 — deliver/preserve and verify “Provisioning entitlement” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BUSINESS-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A compromised provider key can authenticate false messages until rotation/reconciliation.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-BUSINESS-004 — Inventory integrity

- **Related threat IDs:** THR-T-009.
- **Affected component / boundary:** API/jobs → inventory domain; TB-06, TB-07.
- **Preventive controls:** Inventory and purchasing MUST enforce tenant-owned references, permitted stock transitions and documented negative-stock rules transactionally, preventing oversell and duplicate movements under concurrency.
- **Detection controls:** Reconcile payment/ledger/stock invariants and duplicate idempotency keys.
- **Containment / recovery controls:** Server calculations, locked/constraint-backed invariants and payload-bound idempotency. Reconcile provider/ledger data and apply audited compensating operations.
- **Implementation owner:** ERP inventory/purchasing owners.
- **Implementation phase:** Phase 08 shared inventory/purchasing; Phase 09 industry invariants. Legacy workstream: ERP-INVENTORY/PURCHASING (owner defined above).
- **Planned implementation task:** IMP-BUSINESS-004 — deliver/preserve and verify “Inventory integrity” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03, AC-19.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-BUSINESS-004 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Authorized insiders can misuse permitted discounts or collude; business policy still matters.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-001 — Scoped context lifecycle

- **Related threat IDs:** THR-T-005, THR-E-001.
- **Affected component / boundary:** Execution scope → tenant operations; TB-04, TB-06, TB-01, TB-12.
- **Preventive controls:** Tenant execution MUST use canonical internal UUIDs in owned, non-inheritable thread-local scopes, reject in-scope company replacement, close in LIFO order on the owning thread and clear after every outcome.
- **Detection controls:** Preserve physical-reuse tests, runtime guard failures and uncertain-ownership alerts.; Audit role/company selection mismatches and forbidden function attempts.
- **Containment / recovery controls:** Close owned scopes, verify/reset idle sessions; evict owned unsafe connections or stop uncertain pool. Recover pool and replay only verified idempotent work; investigate leaked contexts.; Revoke affected grants/sessions and investigate improper operations.
- **Implementation owner:** Backend/data maintainers.
- **Implementation phase:** Phase 03 foundation maintenance; Phase 07 tenant activation; Phase 11 isolation verification. Legacy workstream: P3-MAINTAIN (owner defined above).
- **Planned implementation task:** IMP-TENANT-001 — deliver/preserve and verify “Scoped context lifecycle” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-16, AC-A-07.
- **Current implementation status:** Implemented.
- **Evidence reference:** [SEC-TENANT-001 canonical record](security-requirements.md) — [TenantContextHolder](../../Backend/src/main/java/com/saaserp/erp/shared/tenant/application/TenantContextHolder.java); TenantContextHolderTest; TenantIdTest.
- **Residual risk:** Any trusted internal caller can bind an identity on an empty thread; this is lifecycle integrity, not membership authorization. Direct raw JDBC/proxy close can bypass supported provider cleanup; trusted internal ownership is required.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-002 — Strict schema identifiers

- **Related threat IDs:** THR-T-004.
- **Affected component / boundary:** Tenant identity → schema routing; TB-04.
- **Preventive controls:** Schema names MUST derive only from validated TenantId through TenantSchemaIdentifier, respecting its fixed lowercase tenant naming contract and PostgreSQL byte limits; arbitrary schema input MUST NOT enter SQL.
- **Detection controls:** Audit invalid identifier attempts and unexpected effective schema/path.
- **Containment / recovery controls:** Parameterized values, internal schema derivation and fail-closed provider route. Restore affected data and repair unsafe query paths with isolation regression evidence.
- **Implementation owner:** Backend/data maintainers.
- **Implementation phase:** Phase 03 foundation maintenance; Phase 07 tenant activation; Phase 11 isolation verification. Legacy workstream: P3-MAINTAIN (owner defined above).
- **Planned implementation task:** IMP-TENANT-002 — deliver/preserve and verify “Strict schema identifiers” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-16, AC-18, AC-20.
- **Current implementation status:** Implemented.
- **Evidence reference:** [SEC-TENANT-002 canonical record](security-requirements.md) — [TenantSchemaIdentifier](../../Backend/src/main/java/com/saaserp/erp/shared/tenant/domain/TenantSchemaIdentifier.java); TenantSchemaIdentifierTest; TenantConnectionProviderTest.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Parameterized queries do not constrain hostile fully qualified SQL from an authorized shared process.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-003 — Fail-closed routing foundation

- **Related threat IDs:** THR-T-004, THR-T-005.
- **Affected component / boundary:** Hibernate → PostgreSQL; TB-04, TB-04, TB-06.
- **Preventive controls:** Hibernate tenant routing MUST require active context/identifier agreement, restricted runtime identity and existing accessible schema, verify the exact effective path and MUST NOT fall back to public, platform, billing or another tenant; central persistence MUST remain separate.
- **Detection controls:** Audit invalid identifier attempts and unexpected effective schema/path.; Preserve physical-reuse tests, runtime guard failures and uncertain-ownership alerts.
- **Containment / recovery controls:** Parameterized values, internal schema derivation and fail-closed provider route. Restore affected data and repair unsafe query paths with isolation regression evidence.; Recover pool and replay only verified idempotent work; investigate leaked contexts.
- **Implementation owner:** Backend/data maintainers.
- **Implementation phase:** Phase 03 foundation maintenance; Phase 07 tenant activation; Phase 11 isolation verification. Legacy workstream: P3-MAINTAIN → TENANT-ACTIVATION (owner defined above).
- **Planned implementation task:** IMP-TENANT-003 — deliver/preserve and verify “Fail-closed routing foundation” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-02, AC-16, AC-18, AC-20, AC-A-02, AC-A-07.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Routing/provider code exists and has tests, but no production tenant persistence unit, tenant entities or authenticated resolver is activated. Parameterized queries do not constrain hostile fully qualified SQL from an authorized shared process.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-004 — Connection session cleanup

- **Related threat IDs:** THR-T-005.
- **Affected component / boundary:** Tenant JDBC lease → HikariCP; TB-04, TB-06.
- **Preventive controls:** TenantConnectionProvider MUST verify idle transaction state and runtime identity, DISCARD ALL and verify catalog-only baseline/JDBC state before reuse, reject unsupported ownership violations and evict owned unsafe leases or stop the pool when ownership is uncertain; cleanup MUST NOT commit caller transactions.
- **Detection controls:** Preserve physical-reuse tests, runtime guard failures and uncertain-ownership alerts.
- **Containment / recovery controls:** Close owned scopes, verify/reset idle sessions; evict owned unsafe connections or stop uncertain pool. Recover pool and replay only verified idempotent work; investigate leaked contexts.
- **Implementation owner:** Backend/data maintainers.
- **Implementation phase:** Phase 03 foundation maintenance; Phase 07 tenant activation; Phase 11 isolation verification. Legacy workstream: P3-MAINTAIN (owner defined above).
- **Planned implementation task:** IMP-TENANT-004 — deliver/preserve and verify “Connection session cleanup” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-16, AC-A-02, AC-A-07.
- **Current implementation status:** Implemented.
- **Evidence reference:** [SEC-TENANT-004 canonical record](security-requirements.md) — [PostgresSessionState](../../Backend/src/main/java/com/saaserp/erp/shared/tenant/infrastructure/persistence/PostgresSessionState.java); TenantConnectionProviderTest; TenantRoutingDatabaseIntegrationTest.
- **Residual risk:** Raw JDBC escape/direct proxy close can bypass provider cleanup; trusted internal callers must obey lease ownership. Driver/network failures can require pool recovery. Direct raw JDBC/proxy close can bypass supported provider cleanup; trusted internal ownership is required.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-005 — Tenant Redis namespaces

- **Related threat IDs:** THR-D-002, THR-E-005.
- **Affected component / boundary:** TenantCache port → Redis; TB-03, TB-04, TB-05, TB-06, TB-05.
- **Preventive controls:** Tenant cache operations MUST derive hich:tenant:<uuid>:<namespace>:<key> from current context on every call, validate components, expose no arbitrary tenant/raw-key or bulk clear API, require atomic bounded TTL writes and use safe string serialization.
- **Detection controls:** Measure queue/pool saturation, per-tenant budgets and cache pressure.; Audit unauthorized key/command access and security-state eviction pressure.
- **Containment / recovery controls:** Bound body/page/value sizes, TTL, concurrency and background quotas; isolate budgets. Drain/cancel bounded work and recover pools without cross-tenant fallback.; Rotate Redis credentials and rebuild only disposable state; revoke affected sessions.
- **Implementation owner:** Backend/data maintainers.
- **Implementation phase:** Phase 03 foundation maintenance; Phase 07 tenant activation; Phase 11 isolation verification. Legacy workstream: P3-MAINTAIN (owner defined above).
- **Planned implementation task:** IMP-TENANT-005 — deliver/preserve and verify “Tenant Redis namespaces” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-02, AC-08, AC-15, AC-16, AC-18, AC-20, AC-21, AC-A-07.
- **Current implementation status:** Implemented.
- **Evidence reference:** [SEC-TENANT-005 canonical record](security-requirements.md) — TenantCacheKey, RedisTenantCache, RedisTenantCacheConfig; TenantCacheKeyTest; RedisTenantCacheTest; TenantRedisCacheIntegrationTest.
- **Residual risk:** Prefixes and shared Redis credentials are routing conventions, not server-enforced tenant authorization; eviction may precede TTL. One process and shared stores retain common capacity/failure domains.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-006 — Async tenant authority

- **Related threat IDs:** THR-S-008, THR-T-005, THR-D-005.
- **Affected component / boundary:** Request/queue → worker; TB-01, TB-06, TB-04, TB-06.
- **Preventive controls:** Every asynchronous job MUST independently establish authenticated/delegated company authority, revalidate applicable membership/entitlement on execution, bind and close its own context and never inherit request thread-local state implicitly.
- **Detection controls:** Audit rejected company selections and worker identity/policy mismatches.; Preserve physical-reuse tests, runtime guard failures and uncertain-ownership alerts.
- **Containment / recovery controls:** Validate current writer membership before scope binding; quarantine unauthorized jobs. Revoke affected delegated authority and reconcile job effects.; Recover pool and replay only verified idempotent work; investigate leaked contexts.
- **Implementation owner:** Worker/application + tenant owners.
- **Implementation phase:** Phase 07 authorized worker boundary; Phases 08–09 delegated ERP jobs. Legacy workstream: TENANT-ACTIVATION + ERP (owner defined above).
- **Planned implementation task:** IMP-TENANT-006 — deliver/preserve and verify “Async tenant authority” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-01, AC-02, AC-08, AC-16, AC-19, AC-A-02, AC-A-07.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Any trusted internal code can bind a context; application-process compromise is a separate risk.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-TENANT-007 — Database-enforced company privileges

- **Related threat IDs:** THR-I-003, THR-E-004.
- **Affected component / boundary:** Compromised backend/runtime → PostgreSQL; TB-04, TB-05, TB-04.
- **Preventive controls:** A future ADR MUST design stronger database-enforced tenant privileges within the existing single database, including controlled role/credential reach and safe pooling/provisioning; shared runtime routing MUST NOT be described as database-enforced per-tenant authorization.
- **Detection controls:** Monitor unexpected schema/key/command access and credential use.; Catalog/grant reviews, denied DDL/role tests and unexpected credential use.
- **Containment / recovery controls:** Least privilege, stronger database design, restricted Redis ACL/contracts and secret scope. Contain application, rotate secrets and restore/reconcile affected tenant data.; Rotate credentials, restore verified schemas/history and review reach.
- **Implementation owner:** Database/security architect + provisioning owner.
- **Implementation phase:** Phase 07 stronger database-enforced privilege design; earlier gate if any prior feature claims that assurance; Phase 12 grant proof. Legacy workstream: TENANT-HARDENING (owner defined above).
- **Planned implementation task:** IMP-TENANT-007 — deliver/preserve and verify “Database-enforced company privileges” under the canonical requirement and proposed design after approval.
- **Verification method:** ADR review, then real restricted-role adversarial SQL tests; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-20, AC-24.
- **Current implementation status:** Deferred.
- **Evidence reference:** [SEC-TENANT-007 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Current shared runtime credentials can reach multiple granted tenant schemas; no containment guarantee against backend-process compromise. Existing search_path/key prefixes are routing isolation, not per-tenant database/Redis authorization.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-DATA-001 — Restricted database roles

- **Related threat IDs:** THR-I-003, THR-E-004.
- **Affected component / boundary:** Runtime/migrator → PostgreSQL; TB-04, TB-05, TB-04.
- **Preventive controls:** Database runtime and migration credentials MUST remain separate, deny superuser/role/database creation, replication and RLS bypass, deny runtime DDL/history modification and restrict environment connections; production grants MUST be explicitly provisioned and verified.
- **Detection controls:** Monitor unexpected schema/key/command access and credential use.; Catalog/grant reviews, denied DDL/role tests and unexpected credential use.
- **Containment / recovery controls:** Least privilege, stronger database design, restricted Redis ACL/contracts and secret scope. Contain application, rotate secrets and restore/reconcile affected tenant data.; Rotate credentials, restore verified schemas/history and review reach.
- **Implementation owner:** Backend/data + infrastructure owners.
- **Implementation phase:** Phase 03 maintenance; Phase 07 tenant data; Phase 12 production privileges/ACLs. Legacy workstream: P3-MAINTAIN + INFRA (owner defined above).
- **Planned implementation task:** IMP-DATA-001 — deliver/preserve and verify “Restricted database roles” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-20, AC-24.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Runtime permissions still span every granted tenant; separate migrator secrets remain process-accessible under current startup configuration. Existing search_path/key prefixes are routing isolation, not per-tenant database/Redis authorization.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-DATA-002 — Migration integrity

- **Related threat IDs:** THR-E-004.
- **Affected component / boundary:** Migration resources → PostgreSQL; TB-04.
- **Preventive controls:** Schema changes MUST use immutable versioned Flyway migrations separated into allowlisted platform, billing and future tenant streams, with no recursive unrestricted tenant execution or automatic Hibernate updates; explicit guarded bootstrap MUST NOT become a startup privilege fallback.
- **Detection controls:** Catalog/grant reviews, denied DDL/role tests and unexpected credential use.
- **Containment / recovery controls:** Separate runtime/migrator, deny DDL/history writes and design stronger tenant privileges. Rotate credentials, restore verified schemas/history and review reach.
- **Implementation owner:** Data/migration maintainers.
- **Implementation phase:** Phase 03 central foundations maintained; Phase 07 allowlisted tenant migrations before activation. Legacy workstream: P3-MAINTAIN (owner defined above).
- **Planned implementation task:** IMP-DATA-002 — deliver/preserve and verify “Migration integrity” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-20.
- **Current implementation status:** Implemented.
- **Evidence reference:** [SEC-DATA-002 canonical record](security-requirements.md) — FlywayConfig, CentralSchemaBootstrap, application.properties; CentralSchemaBootstrapTest; DatabaseMigrationIntegrationTest.
- **Residual risk:** Production tenant migrations/provisioning are not yet implemented; central baseline has independent non-atomic history operations. Current shared runtime grants do not enforce per-company authorization; startup process also receives migration credentials.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-DATA-003 — Constraints and safe data formats

- **Related threat IDs:** THR-T-009, THR-E-008.
- **Affected component / boundary:** Application ↔ database/cache; TB-06, TB-07, TB-06, TB-09, TB-08.
- **Preventive controls:** Persistence MUST enforce business invariants with appropriate constraints/transactions and use reviewed bounded serializers without unsafe native/polymorphic deserialization; foreign references MUST remain tenant-authorized.
- **Detection controls:** Reconcile payment/ledger/stock invariants and duplicate idempotency keys.; Runtime process/file/egress monitoring and security-analysis findings.
- **Containment / recovery controls:** Server calculations, locked/constraint-backed invariants and payload-bound idempotency. Reconcile provider/ledger data and apply audited compensating operations.; Isolate compromised host, rotate secrets and deploy clean verified artifact with restored data.
- **Implementation owner:** Data + ERP module owners.
- **Implementation phase:** Phase 07 tenant constraints/serialization design; Phases 08–09 business constraints before release. Legacy workstream: ERP + TENANT-ACTIVATION (owner defined above).
- **Planned implementation task:** IMP-DATA-003 — deliver/preserve and verify “Constraints and safe data formats” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-03, AC-18, AC-19, AC-21, AC-22.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Authorized insiders can misuse permitted discounts or collude; business policy still matters.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-DATA-004 — Redis ACL and security-state isolation

- **Related threat IDs:** THR-I-003, THR-D-004, THR-E-005, THR-T-011, THR-D-008.
- **Affected component / boundary:** Business cache ↔ security state/Redis; TB-04, TB-05, TB-05.
- **Preventive controls:** Production Redis MUST use least-privilege service/environment ACL identities, encrypted private transport and restricted administration; session/revocation state MUST have a separate application contract and protected key/command scope from disposable tenant caches, with a reviewed non-eviction/durable recovery policy. Logical database numbers MUST NOT be treated as security boundaries.
- **Detection controls:** Monitor unexpected schema/key/command access and credential use.; Detect timeouts, unknown commits and security-state mismatch without logging secrets.
- **Containment / recovery controls:** Least privilege, stronger database design, restricted Redis ACL/contracts and secret scope. Contain application, rotate secrets and restore/reconcile affected tenant data.; Reconcile committed database state/outbox and resume only after authority is verified.
- **Implementation owner:** Identity + Redis/infrastructure owners.
- **Implementation phase:** Phase 04 security-state contract/authority; Phase 12 scoped Redis ACLs, isolation and operational proof. Legacy workstream: P4-DESIGN + INFRA (owner defined above).
- **Planned implementation task:** IMP-DATA-004 — deliver/preserve and verify “Redis ACL and security-state isolation” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-15, AC-20, AC-24, AC-A-02, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-DATA-004 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Existing shared credentials and raw infrastructure clients have broader reach; a shared ACL matching all tenants still cannot isolate one tenant from compromised code. Existing search_path/key prefixes are routing isolation, not per-tenant database/Redis authorization.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-CRYPTO-001 — Sensitive data protection

- **Related threat IDs:** THR-I-005, THR-E-008.
- **Affected component / boundary:** Sensitive data → storage/key management; TB-04, TB-11, TB-06, TB-09, TB-08.
- **Preventive controls:** Sensitive fields, TOTP secrets and backup data MUST be encrypted at rest with approved authenticated encryption and managed keys separate from ciphertext; envelope encryption SHOULD be used where justified by classification, rotation or tenant key scope.
- **Detection controls:** Audit key access, backup exports and unexpected decryption.; Runtime process/file/egress monitoring and security-analysis findings.
- **Containment / recovery controls:** Calibrated hashes, authenticated encryption, key separation and independent encrypted backup. Rotate affected keys/factors, recover clean data and assess disclosure.; Isolate compromised host, rotate secrets and deploy clean verified artifact with restored data.
- **Implementation owner:** Identity/security + secret/operations owners.
- **Implementation phase:** Phase 04 cryptographic design; Phase 11 review; Phase 12 production keys. Legacy workstream: P4-IMPLEMENT + INFRA (owner defined above).
- **Planned implementation task:** IMP-CRYPTO-001 — deliver/preserve and verify “Sensitive data protection” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-07, AC-14, AC-18, AC-21, AC-22, AC-23, AC-24.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Encryption at rest does not prevent an authorized compromised application from decrypting accessible data. At-rest encryption cannot stop an authorized compromised application from using accessible decryption keys.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-CRYPTO-002 — Key and secret management

- **Related threat IDs:** THR-I-004, THR-I-005, THR-I-006, THR-E-004.
- **Affected component / boundary:** Operator/secret storage → process; TB-01, TB-10, TB-11, TB-04, TB-11, TB-06, TB-10, TB-04.
- **Preventive controls:** Passwords, API keys, signing/encryption keys and service credentials MUST use approved secret injection/storage without source/log exposure, documented owners, least-privilege access, rotation and emergency replacement; production keys MUST NOT be shared with development/test.
- **Detection controls:** Canary-secret scans and restricted review of redaction pipelines.; Audit key access, backup exports and unexpected decryption.
- **Containment / recovery controls:** Redact at capture, minimize fields and restrict telemetry access/retention. Rotate exposed authority, remove unsafe collection and preserve safe incident evidence.; Rotate affected keys/factors, recover clean data and assess disclosure.
- **Implementation owner:** Identity/security + secret/operations owners.
- **Implementation phase:** Phase 04 cryptographic design; Phase 11 review; Phase 12 production keys. Legacy workstream: P4-IMPLEMENT + INFRA (owner defined above).
- **Planned implementation task:** IMP-CRYPTO-002 — deliver/preserve and verify “Key and secret management” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-07, AC-14, AC-18, AC-20, AC-21, AC-23, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-CRYPTO-002 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Error-suppression configuration alone does not verify all credential handling.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-CRYPTO-003 — Cryptographic hygiene

- **Related threat IDs:** THR-I-005, THR-E-010, THR-E-012.
- **Affected component / boundary:** Backend; TB-04, TB-11, TB-01 through TB-12.
- **Preventive controls:** Cryptographic operations MUST use maintained standard libraries, approved algorithms and CSPRNGs with explicit key sizes/context binding; custom cryptography MUST NOT be introduced, and algorithm deprecation MUST have an owner and migration path.
- **Detection controls:** Audit key access, backup exports and unexpected decryption.; Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Calibrated hashes, authenticated encryption, key separation and independent encrypted backup. Rotate affected keys/factors, recover clean data and assess disclosure.; Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Identity/security + secret/operations owners.
- **Implementation phase:** Phase 04 cryptographic design; Phase 11 review; Phase 12 production keys. Legacy workstream: P4-IMPLEMENT + SUPPLY (owner defined above).
- **Planned implementation task:** IMP-CRYPTO-003 — deliver/preserve and verify “Cryptographic hygiene” under the canonical requirement and proposed design after approval.
- **Verification method:** Code review, crypto inventory and targeted negative tests; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-07, AC-14, AC-23, AC-24, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-CRYPTO-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. At-rest encryption cannot stop an authorized compromised application from using accessible decryption keys.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.

### SEC-INFRA-001 — HTTPS and edge protection

- **Related threat IDs:** THR-T-003, THR-I-007, THR-D-003.
- **Affected component / boundary:** Internet → Cloudflare → Nginx; TB-02, TB-03, TB-02, TB-03, TB-04, TB-05.
- **Preventive controls:** Production MUST enforce HTTPS with approved TLS and HSTS after domain readiness, use Cloudflare WAF/DDoS controls with monitored rules, encrypt/authenticate edge-to-Nginx origin connections and restrict direct-origin access.
- **Detection controls:** Alert on malformed framing, foreign hosts and anomalous edge/origin request counts.; Monitor certificate, firewall, direct-origin and unapproved data connection events.
- **Containment / recovery controls:** Reject ambiguity and restrict origin/proxy peers and header provenance. Patch parser/proxy configuration and review affected logs/sessions.; Restrict exposure, rotate potentially intercepted credentials and validate affected records.
- **Implementation owner:** Infrastructure + operations owners.
- **Implementation phase:** Phase 12 production readiness; Phase 11 authorized security/load validation. Legacy workstream: INFRA (owner defined above).
- **Planned implementation task:** IMP-INFRA-001 — deliver/preserve and verify “HTTPS and edge protection” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-INFRA-001 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** WAF rules cannot prevent every application attack; edge outages and bypass mistakes remain possible. Different protocol implementations may have undiscovered parser discrepancies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-INFRA-002 — Proxy protocol integrity

- **Related threat IDs:** THR-T-003, THR-I-007, THR-D-003, THR-E-011.
- **Affected component / boundary:** Cloudflare/Nginx → Spring; TB-02, TB-03, TB-02, TB-03, TB-04, TB-05.
- **Preventive controls:** Nginx and upstream parsers MUST reject ambiguous framing, conflicting Content-Length/Transfer-Encoding and invalid Host headers, constrain body/header/time limits and trust forwarded identity/protocol/client-IP headers only from configured proxies which overwrite untrusted values.
- **Detection controls:** Alert on malformed framing, foreign hosts and anomalous edge/origin request counts.; Monitor certificate, firewall, direct-origin and unapproved data connection events.
- **Containment / recovery controls:** Reject ambiguity and restrict origin/proxy peers and header provenance. Patch parser/proxy configuration and review affected logs/sessions.; Restrict exposure, rotate potentially intercepted credentials and validate affected records.
- **Implementation owner:** Infrastructure + operations owners.
- **Implementation phase:** Phase 12 production readiness; Phase 11 authorized security/load validation. Legacy workstream: INFRA (owner defined above).
- **Planned implementation task:** IMP-INFRA-002 — deliver/preserve and verify “Proxy protocol integrity” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-02, AC-18, AC-21, AC-S-01–10, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-INFRA-002 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Different protocol implementations may have undiscovered parser discrepancies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-INFRA-003 — Private services and network limits

- **Related threat IDs:** THR-I-006, THR-I-007, THR-D-003.
- **Affected component / boundary:** Network → data services; TB-06, TB-10, TB-02, TB-03, TB-04, TB-05, TB-02, TB-03.
- **Preventive controls:** PostgreSQL and Redis MUST be private, firewall rules MUST permit only needed flows, environments MUST be segmented, and outbound connections MUST be restricted to approved dependencies with reviewed exceptions.
- **Detection controls:** Alert on blocked private destinations, DNS changes and unusual outbound flows.; Monitor certificate, firewall, direct-origin and unapproved data connection events.
- **Containment / recovery controls:** Purpose allowlists, DNS/redirect revalidation, bounded fetch and network egress restrictions. Rotate disclosed credentials and isolate vulnerable fetch workflow.; Restrict exposure, rotate potentially intercepted credentials and validate affected records.
- **Implementation owner:** Infrastructure + operations owners.
- **Implementation phase:** Phase 12 production readiness; Phase 11 authorized security/load validation. Legacy workstream: INFRA (owner defined above).
- **Planned implementation task:** IMP-INFRA-003 — deliver/preserve and verify “Private services and network limits” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-INFRA-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. DNS/network changes and approved compromised destinations remain residual risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-INFRA-004 — Container and host hardening

- **Related threat IDs:** THR-T-010, THR-D-002, THR-E-008, THR-D-008.
- **Affected component / boundary:** Container → host; TB-06, TB-09, TB-03, TB-04, TB-05, TB-06, TB-06, TB-09, TB-08.
- **Preventive controls:** Production containers MUST run non-root with minimum Linux capabilities and read-only filesystems where applicable, bounded resources, supported seccomp/AppArmor policies and narrowly scoped writable paths; exceptions MUST be reviewed against applicable CIS guidance.
- **Detection controls:** Scan/quarantine files, monitor integrity and verify artifact provenance.; Measure queue/pool saturation, per-tenant budgets and cache pressure.
- **Containment / recovery controls:** Non-executable private storage, restricted runtime mounts and verified immutable deployment. Restore clean artifact/data and rotate credentials if execution occurred.; Drain/cancel bounded work and recover pools without cross-tenant fallback.
- **Implementation owner:** Infrastructure + operations owners.
- **Implementation phase:** Phase 12 production readiness; Phase 11 authorized security/load validation. Legacy workstream: INFRA (owner defined above).
- **Planned implementation task:** IMP-INFRA-004 — deliver/preserve and verify “Container and host hardening” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-16, AC-18, AC-21, AC-22, AC-S-01–10, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-INFRA-004 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Signing proves provenance, not absence of malicious authorized code.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-INFRA-005 — Administration and deployment

- **Related threat IDs:** THR-S-007, THR-T-010, THR-R-003, THR-E-008.
- **Affected component / boundary:** Administrator/CI → production; TB-08, TB-06, TB-09, TB-08, TB-09, TB-06, TB-09, TB-08.
- **Preventive controls:** Production administration MUST use named least-privilege SSH/console identities, hardware-backed administrator authentication, restricted entry paths and audited time-bounded elevation; deployments MUST use approved immutable artifacts and protect secret access.
- **Detection controls:** Audit named operator access, hardware assurance and approval provenance.; Scan/quarantine files, monitor integrity and verify artifact provenance.
- **Containment / recovery controls:** Restrict administrative entry and require independent verified approval. Revoke operator credentials and rebuild trust using incident runbooks.; Restore clean artifact/data and rotate credentials if execution occurred.
- **Implementation owner:** Infrastructure + operations owners.
- **Implementation phase:** Phase 12 production readiness; Phase 11 authorized security/load validation. Legacy workstream: INFRA + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-INFRA-005 — deliver/preserve and verify “Administration and deployment” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-12, AC-18, AC-21, AC-22, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-INFRA-005 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** A fully compromised production administrator may alter controls, credentials, data and logs. Compromised privileged humans and collusion remain broad trust risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-INFRA-006 — Runtime detection

- **Related threat IDs:** THR-T-010, THR-D-007.
- **Affected component / boundary:** Backend; TB-06, TB-09, TB-09, TB-11.
- **Preventive controls:** Operations MUST deploy actionable runtime and file integrity monitoring for unexpected processes, configuration/artifact changes, network activity and resource abuse, with owned alerts and tested response thresholds.
- **Detection controls:** Scan/quarantine files, monitor integrity and verify artifact provenance.; Alert on telemetry gaps, expired exceptions and overdue findings.
- **Containment / recovery controls:** Non-executable private storage, restricted runtime mounts and verified immutable deployment. Restore clean artifact/data and rotate credentials if execution occurred.; Patch verified artifacts, recover clean service and exercise revised runbooks.
- **Implementation owner:** Infrastructure + operations owners.
- **Implementation phase:** Phase 12 production readiness; Phase 11 authorized security/load validation. Legacy workstream: INFRA + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-INFRA-006 — deliver/preserve and verify “Runtime detection” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21, AC-22, AC-23, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-INFRA-006 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Signing proves provenance, not absence of malicious authorized code.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P1 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-SUPPLY-001 — Branch and workflow authority

- **Related threat IDs:** THR-R-003, THR-I-008.
- **Affected component / boundary:** Contributor/Actions → trusted build; TB-08, TB-09, TB-09.
- **Preventive controls:** Protected branches MUST require reviewed changes and mandatory security/build checks; workflows MUST use least-privilege scoped permissions, isolate untrusted PR code from secrets and pin external Actions to reviewed immutable commits.
- **Detection controls:** Correlate named approval, source digest, signed artifact and runtime identity.; Secret/SAST/SCA/tool-integrity findings, provenance mismatch and runner egress alerts.
- **Containment / recovery controls:** Separate authorship/release authority and restrict administrator entry. Preserve release evidence, roll forward to reviewed artifact and rotate affected authority.; Rebuild from reviewed inputs, revoke affected secrets and patch dependencies.
- **Implementation owner:** Repository/release + security owners.
- **Implementation phase:** Phase 01 CI maintenance; Phase 04 policy; Phase 11 security gates; Phase 12 release. Legacy workstream: SUPPLY (owner defined above).
- **Planned implementation task:** IMP-SUPPLY-001 — deliver/preserve and verify “Branch and workflow authority” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-21, AC-22, AC-24.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Solo development cannot provide independent production approval without additional reviewers.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-SUPPLY-002 — Security analysis gates

- **Related threat IDs:** THR-I-008, THR-E-008.
- **Affected component / boundary:** Source/build/test environment; TB-09, TB-06, TB-09, TB-08.
- **Preventive controls:** CI/release checks MUST include redacted secret scanning, SAST, SCA and infrastructure scanning, plus authorized DAST against a representative isolated environment; SonarCloud applicability/configuration MUST be verified rather than inferred from the intended toolchain.
- **Detection controls:** Secret/SAST/SCA/tool-integrity findings, provenance mismatch and runner egress alerts.; Runtime process/file/egress monitoring and security-analysis findings.
- **Containment / recovery controls:** Least-privilege CI, immutable pins, byte verification, isolated untrusted code and authorized artifact verification. Rebuild from reviewed inputs, revoke affected secrets and patch dependencies.; Isolate compromised host, rotate secrets and deploy clean verified artifact with restored data.
- **Implementation owner:** Repository/release + security owners.
- **Implementation phase:** Phase 01 CI maintenance; Phase 04 policy; Phase 11 security gates; Phase 12 release. Legacy workstream: SUPPLY (owner defined above).
- **Planned implementation task:** IMP-SUPPLY-002 — deliver/preserve and verify “Security analysis gates” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21, AC-22.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A signed artifact or clean scan can still contain malicious trusted code.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-SUPPLY-003 — Dependency verification

- **Related threat IDs:** THR-T-001, THR-I-008.
- **Affected component / boundary:** Registry/tool download → build; TB-01, TB-09, TB-09.
- **Preventive controls:** Dependencies and downloaded tools MUST use reviewed versions, integrity/checksum or signature verification and controlled sources; lockfiles MUST be maintained where supported and dependency changes reviewed, including transitive npm risks.
- **Detection controls:** Collect redacted CSP/Trusted Types violations and integrity/dependency alerts.; Secret/SAST/SCA/tool-integrity findings, provenance mismatch and runner egress alerts.
- **Containment / recovery controls:** Block unapproved scripts, disable affected release/content and revoke sessions as needed. Deploy reviewed assets, sanitize stored payloads and investigate operations.; Rebuild from reviewed inputs, revoke affected secrets and patch dependencies.
- **Implementation owner:** Repository/release + security owners.
- **Implementation phase:** Phase 01 CI maintenance; Phase 04 policy; Phase 11 security gates; Phase 12 release. Legacy workstream: SUPPLY (owner defined above).
- **Planned implementation task:** IMP-SUPPLY-003 — deliver/preserve and verify “Dependency verification” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-17, AC-22.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Malicious same-origin code can use current authority despite HttpOnly cookies.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-SUPPLY-004 — Artifact provenance and deployment

- **Related threat IDs:** THR-T-010, THR-R-003, THR-I-008.
- **Affected component / boundary:** Build → artifact → production; TB-06, TB-09, TB-08, TB-09, TB-09.
- **Preventive controls:** Release builds MUST produce an SBOM, signed artifacts and verifiable build provenance tied to reviewed source; deployment MUST verify digest/signature/provenance and explicit authorization before running an immutable production artifact.
- **Detection controls:** Scan/quarantine files, monitor integrity and verify artifact provenance.; Correlate named approval, source digest, signed artifact and runtime identity.
- **Containment / recovery controls:** Non-executable private storage, restricted runtime mounts and verified immutable deployment. Restore clean artifact/data and rotate credentials if execution occurred.; Preserve release evidence, roll forward to reviewed artifact and rotate affected authority.
- **Implementation owner:** Repository/release + security owners.
- **Implementation phase:** Phase 01 CI maintenance; Phase 04 policy; Phase 11 security gates; Phase 12 release. Legacy workstream: SUPPLY + INFRA (owner defined above).
- **Planned implementation task:** IMP-SUPPLY-004 — deliver/preserve and verify “Artifact provenance and deployment” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21, AC-22, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-SUPPLY-004 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Signing proves provenance, not absence of malicious authorized code.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-SUPPLY-005 — Vulnerability and patch lifecycle

- **Related threat IDs:** THR-I-008, THR-D-007, THR-E-008.
- **Affected component / boundary:** Backend; TB-09, TB-09, TB-11, TB-06, TB-09, TB-08.
- **Preventive controls:** Security findings MUST have owners, severity/exploitability triage, approved remediation deadlines and expiry for exceptions; production dependencies, images and hosts MUST receive verified patches and regression checks. Proposed deadlines are 24h containment for actively exploited critical issues, 7 days critical and 30 days high remediation, pending operational approval.
- **Detection controls:** Secret/SAST/SCA/tool-integrity findings, provenance mismatch and runner egress alerts.; Alert on telemetry gaps, expired exceptions and overdue findings.
- **Containment / recovery controls:** Least-privilege CI, immutable pins, byte verification, isolated untrusted code and authorized artifact verification. Rebuild from reviewed inputs, revoke affected secrets and patch dependencies.; Patch verified artifacts, recover clean service and exercise revised runbooks.
- **Implementation owner:** Repository/release + security owners.
- **Implementation phase:** Phase 01 CI maintenance; Phase 04 policy; Phase 11 security gates; Phase 12 release. Legacy workstream: SUPPLY + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-SUPPLY-005 — deliver/preserve and verify “Vulnerability and patch lifecycle” under the canonical requirement and proposed design after approval.
- **Verification method:** Finding-tracker audit, release-gate exercise and patch regression evidence; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-18, AC-21, AC-22, AC-23, AC-24.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-SUPPLY-005 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A signed artifact or clean scan can still contain malicious trusted code.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P1 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUDIT-001 — Structured security events

- **Related threat IDs:** THR-R-001, THR-R-002, THR-R-004.
- **Affected component / boundary:** Security/business decisions → audit; TB-01, TB-04, TB-06, TB-08, TB-11.
- **Preventive controls:** Authentication outcomes, authorization failures, policy/privilege changes, tenant membership changes, privileged operations, suspicious sessions and revocations MUST emit structured security events with trusted actor/company context, outcome, timestamp and validated correlation IDs.
- **Detection controls:** Check event completeness and missing sequences against committed transitions.; Correlate requested digest, approval, current grants and final ledger event.
- **Containment / recovery controls:** Use trusted actor/company context, validated correlation IDs and restricted audit access. Preserve independent evidence, notify affected users and reconstruct timeline.; Reconcile financial state and investigate through independent preserved records.
- **Implementation owner:** Identity/audit + module + operations owners.
- **Implementation phase:** Phase 04 events; Phase 05 audit management; Phases 06–09 module events; Phase 12 preservation. Legacy workstream: P4-IMPLEMENT + ERP (owner defined above).
- **Planned implementation task:** IMP-AUDIT-001 — deliver/preserve and verify “Structured security events” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-13, AC-14, AC-19, AC-23.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [security audit service](../../Backend/src/main/java/com/saaserp/erp/platform/audit/application/SecurityAuditService.java), [V9 authorization correction](../../Backend/src/main/resources/db/migration/platform/V9__bind_authorization_operations_and_retire_obsolete_functions.sql) for role-assignment/consumption audit correlation, [audit integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java), and [authentication integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AuthenticationIntegrationTest.java). Authentication, authorization denials, step-up, approval and company role-assignment events are structured; broader permission-management coverage and external preservation remain incomplete.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Audit evidence is not proof that a compromised user device reflected the user’s intent.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUDIT-002 — No credential exposure

- **Related threat IDs:** THR-R-004, THR-I-004.
- **Affected component / boundary:** All inputs → telemetry/responses; TB-11, TB-01, TB-10, TB-11.
- **Preventive controls:** Logs, traces, error responses, analytics and notifications MUST redact passwords, cookies, Authorization values, OTPs, TOTP secrets, recovery codes, session identifiers and authentication challenges that convey authority; sensitive customer data MUST be minimized and access restricted.
- **Detection controls:** Detect gaps, tampering, invalid correlation structure and export failures.; Canary-secret scans and restricted review of redaction pipelines.
- **Containment / recovery controls:** Structured encoding, minimal event content, restricted independent preservation. Recover preserved copies, verify integrity and rebuild incident timeline.; Rotate exposed authority, remove unsafe collection and preserve safe incident evidence.
- **Implementation owner:** Identity/audit + module + operations owners.
- **Implementation phase:** Phase 04 events; Phase 05 audit management; Phases 06–09 module events; Phase 12 preservation. Legacy workstream: P4-IMPLEMENT + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-AUDIT-002 — deliver/preserve and verify “No credential exposure” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-14, AC-23, AC-S-01–10.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [audit integration tests](../../Backend/src/test/java/com/saaserp/erp/platform/identity/AccountProtectionIntegrationTest.java) and [security audit implementation](../../Backend/src/main/java/com/saaserp/erp/platform/audit/). Event fields exclude credential material; independent storage, alerting and production retention remain deferred.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. A compromised producer can lie before export; all-trust-root compromise defeats simple tamper evidence.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-AUDIT-003 — Audit preservation and alerting

- **Related threat IDs:** THR-R-004, THR-D-006, THR-D-007, THR-E-009.
- **Affected component / boundary:** Application → independently preserved audit; TB-11, TB-09, TB-11.
- **Preventive controls:** Audit storage MUST restrict read/write/delete privileges, enforce approved retention and tamper-evidence, preserve independent copies beyond ordinary application administrator reach where feasible, and alert on security events, gaps and tampering under documented SLAs.
- **Detection controls:** Detect gaps, tampering, invalid correlation structure and export failures.; Verify backup age/integrity, restoration timing and preserved journal coverage.
- **Containment / recovery controls:** Structured encoding, minimal event content, restricted independent preservation. Recover preserved copies, verify integrity and rebuild incident timeline.; PITR restore, fresh external epoch and reconciled authority before reopening.
- **Implementation owner:** Identity/audit + module + operations owners.
- **Implementation phase:** Phase 04 events; Phase 05 audit management; Phases 06–09 module events; Phase 12 preservation. Legacy workstream: INFRA + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-AUDIT-003 — deliver/preserve and verify “Audit preservation and alerting” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-14, AC-21, AC-22, AC-23, AC-24, AC-A-03.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-AUDIT-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Tamper-evidence cannot guarantee truth if all producers and administrative trust roots are compromised. A compromised producer can lie before export; all-trust-root compromise defeats simple tamper evidence.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-RECOVERY-001 — Account recovery assurance

- **Related threat IDs:** THR-S-007, THR-T-007, THR-R-005, THR-E-003, THR-S-009.
- **Affected component / boundary:** Claimant/support → identity; TB-08, TB-01, TB-08, TB-08, TB-11.
- **Preventive controls:** Recovery MUST use approved identity/ownership evidence, single-use expiring scoped tokens and throttling; recovery codes MUST be high-entropy, hashed and atomically single-use. Recovery MUST NOT silently bypass company/privileged MFA or hardware assurance; lost-factor accounts MUST stay restricted until required assurance is re-established, with notifications and session revocation.
- **Detection controls:** Audit named operator access, hardware assurance and approval provenance.; Notify on factor/recovery transitions; audit rejected OTP/challenge reuse.
- **Containment / recovery controls:** Restrict administrative entry and require independent verified approval. Revoke operator credentials and rebuild trust using incident runbooks.; Invalidate factors/recovery codes/sessions and re-establish approved assurance.
- **Implementation owner:** Identity + independent privileged-recovery approver.
- **Implementation phase:** Phase 04 restricted recovery; Phase 05 support workflow; Phase 11 bypass tests. Legacy workstream: P4-IMPLEMENT (owner defined above).
- **Planned implementation task:** IMP-RECOVERY-001 — deliver/preserve and verify “Account recovery assurance” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-05, AC-06, AC-07, AC-12, AC-21, AC-23, AC-24, AC-A-06, AC-S-01–10, AC-S-01–10, AC-S-01–10.; AC-M-01–10 (applicable subcontrols).
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Stage 03 implementation and owner procedure](phase04-stage03-mfa-account-protection.md); NativeWebAuthnCeremoniesTest, MfaPolicyAndCryptographyTest, AccountProtectionServiceTest, AccountProtectionIntegrationTest. Source only, not execution evidence.
- **Residual risk:** Mailbox compromise and fraudulent support evidence remain risks; recovery requires an approved escalation policy. Compromised privileged humans and collusion remain broad trust risks.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-RECOVERY-002 — Incident containment and recovery

- **Related threat IDs:** THR-R-005, THR-D-006, THR-D-007, THR-E-009.
- **Affected component / boundary:** Detection → incident operations; TB-08, TB-11, TB-11, TB-09, TB-11.
- **Preventive controls:** Operations MUST maintain and exercise incident triage, scope assessment, account/session/token containment, credential/key rotation, tenant protection, evidence preservation and clean recovery runbooks with named owners and communication duties.
- **Detection controls:** Review evidence completeness and incident approval timelines.; Verify backup age/integrity, restoration timing and preserved journal coverage.
- **Containment / recovery controls:** Keep accounts/service restricted until independent approvals and verified reconciliation. Revoke affected sessions/epoch and repeat clean recovery with preserved evidence.; PITR restore, fresh external epoch and reconciled authority before reopening.
- **Implementation owner:** Incident commander + independent operations reviewer.
- **Implementation phase:** Phase 04 revocation hooks; Phase 11 exercises; Phase 12 staffed containment/recovery. Legacy workstream: OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-RECOVERY-002 — deliver/preserve and verify “Incident containment and recovery” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-08, AC-12, AC-21, AC-22, AC-23, AC-24, AC-A-03.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-RECOVERY-002 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. If independent records are unavailable, availability must be sacrificed rather than assuming authority.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-RECOVERY-003 — Backups, PITR and restores

- **Related threat IDs:** THR-R-005, THR-I-005, THR-D-006, THR-E-009, THR-T-011.
- **Affected component / boundary:** PostgreSQL/backups → restored production; TB-08, TB-11, TB-04, TB-11, TB-11.
- **Preventive controls:** Production MUST maintain independent encrypted backups and point-in-time recovery with protected keys, approved RPO/RTO and retention, and scheduled isolated restore verification; restoration MUST reconcile durable revocations and tenant integrity before serving traffic. Proposed RPO 15 minutes/RTO 4 hours requires operations approval and tested capacity.
- **Detection controls:** Review evidence completeness and incident approval timelines.; Audit key access, backup exports and unexpected decryption.
- **Containment / recovery controls:** Keep accounts/service restricted until independent approvals and verified reconciliation. Revoke affected sessions/epoch and repeat clean recovery with preserved evidence.; Rotate affected keys/factors, recover clean data and assess disclosure.
- **Implementation owner:** Backup/DR + independent security operations owners.
- **Implementation phase:** Phase 12 encrypted backups/PITR, epoch and authority reconciliation; Phase 11 isolated recovery exercise. Legacy workstream: INFRA + OPERATIONS (owner defined above).
- **Planned implementation task:** IMP-RECOVERY-003 — deliver/preserve and verify “Backups, PITR and restores” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-07, AC-08, AC-12, AC-14, AC-23, AC-24, AC-A-03, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-RECOVERY-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Backup retention leaves a data-loss window; independent storage reduces but cannot eliminate administrator and infrastructure compromise. If independent records are unavailable, availability must be sacrificed rather than assuming authority.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-VERIFY-001 — Verification target and standards

- **Related threat IDs:** THR-E-010, THR-E-012.
- **Affected component / boundary:** Requirements → assurance evidence; TB-01 through TB-12.
- **Preventive controls:** Security verification MUST propose OWASP ASVS Level 2 with selected higher-assurance privileged/sensitive controls, consider the referenced OWASP/NIST/FIDO/RFC/CIS guidance, and pin reviewed editions and applicability later; compliance, certification and exact mappings MUST NOT be claimed without verified evidence.
- **Detection controls:** Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Deny unclassified routes, enforce release gates and keep proposals unimplemented until approval. Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Independent security/QA + component owners.
- **Implementation phase:** Phase 04 target; each Phase 05–10 feature gate; Phase 11 verification; Phase 12 readiness. Legacy workstream: P4-DESIGN → SECURITY-VERIFY (owner defined above).
- **Planned implementation task:** IMP-VERIFY-001 — deliver/preserve and verify “Verification target and standards” under the canonical requirement and proposed design after approval.
- **Verification method:** Independent security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-24, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-VERIFY-001 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Tests and scans are incomplete; no compliance/certification or total-prevention guarantee follows.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-VERIFY-002 — Isolation and architecture regression

- **Related threat IDs:** THR-T-005, THR-E-010.
- **Affected component / boundary:** Code changes → regression assurance; TB-04, TB-06, TB-01 through TB-12.
- **Preventive controls:** Existing Phase 03 tenant, database privilege, Redis and ArchUnit tests MUST remain intact and pass in the supported restricted environment before dependent functionality is accepted; test failures MUST NOT be hidden by disabling security, weakening roles or removing tests.
- **Detection controls:** Preserve physical-reuse tests, runtime guard failures and uncertain-ownership alerts.; Independent requirements/architecture/evidence review and checks for expired exceptions.
- **Containment / recovery controls:** Close owned scopes, verify/reset idle sessions; evict owned unsafe connections or stop uncertain pool. Recover pool and replay only verified idempotent work; investigate leaked contexts.; Reassess boundaries, restore reviewed design and remediate gaps before exposure.
- **Implementation owner:** Independent security/QA + component owners.
- **Implementation phase:** Phase 04 target; each Phase 05–10 feature gate; Phase 11 verification; Phase 12 readiness. Legacy workstream: P3-MAINTAIN + SECURITY-VERIFY (owner defined above).
- **Planned implementation task:** IMP-VERIFY-002 — deliver/preserve and verify “Isolation and architecture regression” under the canonical requirement and proposed design after approval.
- **Verification method:** Automated positive/negative integration tests and security review; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-16, AC-24, AC-A-07.
- **Current implementation status:** Partially implemented.
- **Evidence reference:** [Unverified foundation source, test classes and owner handoff](phase04-authentication-foundation.md). Remaining controls are not inferred as delivered.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. Direct raw JDBC/proxy close can bypass supported provider cleanup; trusted internal ownership is required.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
### SEC-VERIFY-003 — Adversarial verification and release evidence

- **Related threat IDs:** THR-R-005, THR-D-007, THR-E-010, THR-E-012.
- **Affected component / boundary:** Implementation → release acceptance; TB-08, TB-11, TB-09, TB-11, TB-01 through TB-12.
- **Preventive controls:** Delivery MUST add measurable positive/negative tests for each applicable requirement, misuse and concurrency cases, authorized penetration/DAST testing and security failure injection; waivers MUST name owner, scope, expiry and residual risk, and conditional exclusions MUST cite the approved ADR.
- **Detection controls:** Review evidence completeness and incident approval timelines.; Alert on telemetry gaps, expired exceptions and overdue findings.
- **Containment / recovery controls:** Keep accounts/service restricted until independent approvals and verified reconciliation. Revoke affected sessions/epoch and repeat clean recovery with preserved evidence.; Patch verified artifacts, recover clean service and exercise revised runbooks.
- **Implementation owner:** Independent security/QA + component owners.
- **Implementation phase:** Phase 04 target; each Phase 05–10 feature gate; Phase 11 verification; Phase 12 readiness. Legacy workstream: SECURITY-VERIFY + each delivery phase (owner defined above).
- **Planned implementation task:** IMP-VERIFY-003 — deliver/preserve and verify “Adversarial verification and release evidence” under the canonical requirement and proposed design after approval.
- **Verification method:** Evidence review, adversarial testing and release-gate exercise; both canonical positive/negative assertions.
- **Acceptance scenario IDs:** AC-12, AC-21, AC-22, AC-23, AC-24, AC-S-01–10, AC-S-01–10.
- **Current implementation status:** Planned.
- **Evidence reference:** [SEC-VERIFY-003 canonical record](security-requirements.md) — No implementation evidence identified.
- **Residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee. If independent records are unavailable, availability must be sacrificed rather than assuming authority.
- **Pending decisions:** Architecture is approved. Remaining applicable implementation, owner verification and operational evidence are tracked in the Phase 04 handoff; no future phase waives controls before exposure.
- **Release gate:** P0 applies before exposure in the numbered phases above; Phase 11 evidence review and Phase 12 deployment readiness do not replace earlier feature gates.
- **Session revision treatment:** Approved ADR-0004 uses native company-bound sessions and current PostgreSQL authority. Valid stolen cookies may replay; no browser proof-of-possession guarantee. Apply AC-S-01–10 and retain unrelated MFA, recovery, tenant, business and operational gates.


## Coverage and outstanding implementation

Every canonical ID retains its own record. Retired browser-token IDs are not reused. Production database grants, browser/edge behavior, audit preservation and security review remain operator/release gates. Existing isolation/architecture regressions remain intact. The [Stage 02 handoff](phase04-authentication-foundation.md), [Stage 04 handoff](phase04-stage04-authorization-security.md) and [Stage 05 handoff](phase04-stage05-auth-foundation.md) document remaining work and operator actions.

## Stage 03 verification disposition

The final offline backend verification includes the Stage 03 test sources and migrations: all 336 tests passed with no failures, errors or skips. This confirms automated test behavior, not production hardware assurance, browser deployment, external delivery or operator grants. Hardware-required access/recovery remains denied; encrypted outbox delivery is queued only. Use the [Stage 03 owner procedure](phase04-stage03-mfa-account-protection.md). No existing release gate, retired token identifier or unrelated requirement is weakened.

## Stage 04 implementation and verification disposition

The Stage 04 source implements a database-backed company authorization foundation, native WebAuthn step-up and dual-approval state machines, structured append-only audit events, and restrictive API request/response boundaries. Company authorization reads current PostgreSQL membership, company, entitlement and permission state; it does not trust role attributes stored in a session. The bounded integration tests cover stale/revoked authority, transaction binding, expiration, replay, distinct approvers, state races, audit and HTTP protections. See the [Stage 04 handoff](phase04-stage04-authorization-security.md) for deliverables, migration procedure, test command and limitations.

Status is **partially implemented**, not complete or production verified. Platform-administrator authorization and role assignment are not exposed; object/field authorization is not yet attached to tenant business resources; no future business mutation consumes step-up or dual approval in the same transaction. General API throttling, full React CSP/browser verification and deployment-edge controls remain release gates. Audit storage is append-only and tamper-evident within the database, but independent immutable preservation, alerting and retention operations remain deferred.

V5 and V6 are applied to the integration database and must remain unchanged. V7 is a new migration that makes expiration terminal during approval consumption; it was validated and applied by the integration tests. The Stage 04 offline `clean verify` completed with 336 tests, zero failures, zero errors and zero skips. Passing tests do not replace the owner’s production grants, PostgreSQL/Redis failure, browser/edge, audit-preservation and security-review evidence.

## Stage 05 implementation and verification disposition

Stage 05 adds a separate database-backed platform authorization decision that checks the dedicated `admin.hich.com` host, current account version/status, active platform role/permission and a platform-specific session containing recorded hardware-attestation evidence. The company authorization service cannot authorize this context. No platform session provisioning, attestation verifier, platform role assignment or administrative HTTP route is delivered; absent trusted evidence denies access. Company role changes are limited to approved company roles and require current owner authority, transaction-bound requester step-up, a distinct authorized approver, current-state PostgreSQL checks and atomic approval consumption plus membership update. Self-assignment, target-as-approver, cross-company, stale-role, replay and modified-intent cases are denied. V8 is new and does not alter V2–V7.

Application API quotas now use server-observed remote addresses and current authenticated company/account scope with Redis fail-closed behavior. Security CI’s Trivy restore/save paths were made consistent. Trivy 0.76.2 CLI documentation was checked for the workflow’s filesystem DB, Java DB, ignore-file, package-listing and JSON flags. The CI workflow was not run and no GitHub Actions result is claimed.

The [Stage 05 handoff](phase04-stage05-auth-foundation.md) records the manual browser/hardware checklist and operator/migration conditions. The final offline full build passed with 342 tests, zero failures, zero errors and zero skips using `mvn -o -B -ntp -DforkCount=0 clean verify` (the shared host required in-process Surefire after separate-fork runs failed classpath scanning with `Cannot allocate memory`). Real browser, physical authenticator, production grant, external audit preservation and GitHub Actions checks remain separate evidence gates and are not verified. Deferred acceptance conditions remain targeted to Phases 05, 07–12; this stage does not claim tenant business-mutation atomicity or production readiness.

| Stage 05 foundation | Automated acceptance condition | Verification evidence/status | Remaining acceptance condition and target phase |
| --- | --- | --- | --- |
| Separate platform authorization | A decision succeeds only for a current active privileged account/version, active platform role and platform-scoped permission, exact `admin.hich.com` host, unrevoked platform session, recorded hardware-attestation evidence, and 15-minute idle/8-hour absolute bounds. Company session IDs, missing evidence, inactive roles and wrong-host requests deny. | `AccountProtectionIntegrationTest` exercises valid/invalid database-backed decisions; `DatabaseMigrationIntegrationTest` checks runtime cannot read/write the platform session table and can execute only the scoped decision function. | Phase 05 must deliver a trusted attestation verifier and platform-session provisioning/rotation/revocation flow before any real privileged login is permitted; Phase 11 must verify supported physical authenticators and hardware evidence. |
| Company role assignment | Only a currently authorized owner can request a role change with WebAuthn step-up bound to company, target, old role and requested role; a second authorized identity approves; requester and approver cannot be the target. At execution PostgreSQL revalidates state, rejects stale/cross-company/replayed/modified requests, consumes approval and updates one membership atomically, increments its security version and leaves sibling-company membership unchanged. | `AccountProtectionIntegrationTest` covers valid assignment, self-assignment, target-as-approver, altered intent and sibling-company isolation against PostgreSQL. | Phase 05 must add any administrative API/UI only after server authorization and DTO allowlists; Phases 08–10 must attach per-object/field checks to real resources and preserve atomic authorization with their business transactions. |
| API request quotas | Each API request is limited to 300 per server-observed source address and 120 per authenticated company/account scope in a 60-second fixed window. Above-quota is 429; absent source/Redis failure is 503; forwarded-IP headers cannot change the key. | `ApiRequestQuotaFilterTest` covers forwarded-header spoofing and 429/503; `AccountProtectionIntegrationTest` exercises source/account limits using Redis. | Phase 07 must add worker/chunk-specific quotas and tenant-aware resource budgets; Phases 08–10 must add upload, pagination, concurrency and operation-specific limits; Phase 11 must test production proxy/header provenance and load behavior. |
| Security CI review | Restore/save use the same runner-temp database path; tagged Trivy v0.76.2 CLI docs confirm the database download and package-inventory flags; scanner and inventory failures remain fatal. | Static workflow review only. The workflow path was corrected; neither the workflow nor GitHub Actions was executed. | Phase 01 continues CI maintenance; Phase 11 must run the workflow in GitHub and retain results for both clean and vulnerable/incomplete inventory fixtures. |

Unimplemented acceptance conditions remain release gates, not waivers: AC-A-02 business-mutation commit/revocation fencing and AC-A-07 workers target Phase 07; object/field authorization, business dual approval and upload limits target Phases 08–09; actual React session/CSRF flows target Phase 10; adversarial, browser, physical-authenticator, outage and load verification target Phase 11; production DB/Redis grants, trusted network/TLS, independent audit preservation, restore and operator readiness target Phase 12. The browser checklist is planned, not executed.


## V9 focused PostgreSQL authorization security correction — 2026-11-28

The new immutable [V9 migration](../../Backend/src/main/resources/db/migration/platform/V9__bind_authorization_operations_and_retire_obsolete_functions.sql) corrects two confirmed V8 defects: caller-supplied role fields were not independently bound to the approved digest, and assignment invoked the obsolete nine-argument approval consumer. V1–V8 remain unchanged. PostgreSQL now derives the digest from the actual company UUID, target UUID, expected role and new role. It reproduces `OperationIntent.digest()` exactly: UTF-8 strings prefixed with big-endian 32-bit byte lengths, header fields, a 32-bit detail count and lexically sorted key/value pairs. Both the supplied and stored approval digest must match. The helper is internal, with no PUBLIC or runtime execution grant.

Assignment calls the current ten-argument consumer with the caller's correlation UUID. Valid expired approvals become terminal `EXPIRED` through that consumer; successful consumption and role mutation use the same audit correlation. Existing row locks, authoritative requester/approver checks, independent-approver and last-owner protections, target membership version fencing and atomic mutation/audit remain. A suppressed membership update raises an error to roll back consumption instead of returning a partial result.

Review covered V5–V8 workflow functions and their Java services/adapters. Required NULL inputs now explicitly deny in step-up creation and approval request/approve/cancel/expire/consume/assignment; row bindings use NULL-safe comparisons. Approval idempotence checks expiration before returning success. Company/platform decisions and step-up burn/complete/consume use SQL equality predicates that reject NULL rather than bypassing a procedural `IF`. Audit required fields are already constrained and reject NULL. Generic approval/step-up primitives bind stored operation fields and digest; company-role assignment is the delivered business mutation that must independently derive that digest. No future business functionality is added.

After replacing V8's obsolete call, source inspection found no legitimate remaining consumer of the old cancel, expire or consume overloads. V9 drops all three without `CASCADE`; it preserves current function ACLs and introduces no broad grants. Migration privilege tests now assert absence of the removed overloads, retaining all prior restricted-runtime access requirements.

| Canonical controls | Added direct PostgreSQL evidence |
| --- | --- |
| SEC-AUTHZ-001/002/003/006/007 | `restrictedSqlCannotChangeRoleIntentUsingTheOriginalApprovedDigest`: altered company, target, expected/new role and NULL arguments deny; original valid digest cannot authorize a changed role; denial leaves membership/version/approval/audit unchanged; the original operation succeeds once. |
| SEC-AUTHZ-006/007 | `postgresRoleDigestMatchesJavaCanonicalEncoding`: fixed independent SHA-256 fixture, every supported role pair, and multibyte UTF-8/delimiter data agree between Java and PostgreSQL. |
| SEC-AUTHZ-005/007 | `restrictedSqlRevalidatesApproverAuthorityBeforeRoleMutation` and `concurrentRestrictedSqlRoleAssignmentSucceedsExactlyOnce`: current authority and locked single-use execution. |
| SEC-AUTHZ-006/007, SEC-AUDIT-001 | `restrictedSqlRoleAssignmentExpiresThroughCorrelatedCurrentConsumer` and `restrictedSqlRoleMutationApprovalAndAuditRollBackTogether`: terminal expiry and correlated audit, no expired mutation, and transaction rollback restores membership, approval and audit together. |
| SEC-AUTHZ-006/007 | `restrictedSqlWorkflowNullBindingsNeverConsumeOrCancelApproval`: direct request/approve/cancel/expire/consume calls reject each required NULL binding; wrong non-NULL operation bindings deny; a legitimate assignment remains usable. |
| SEC-PRINCIPLE-003/005, SEC-VERIFY-002 | `obsoleteAuthorizationOverloadsAreAbsentAndV9KeepsLeastPrivilege`: V9 applied, obsolete signatures absent, internal helper denied to runtime, current consumer executable, no PUBLIC execution grant on corrected functions. |

All new mutation attempts execute through the existing `hich_runtime_test` datasource; only test setup/state inspection uses the restricted migrator. Existing tests and production/owner release gates remain in force. Final offline full verification completed successfully on 2026-11-28: **350 tests, zero failures, zero errors and zero skips** (342 existing tests plus eight added regressions). The earlier focused migration/account-protection run passed 59 tests with zero failures, errors or skips; the full run also includes the subsequently added rollback test and pinned digest fixture. Maven exited with `BUILD SUCCESS`; Surefire XML totals independently confirm the full result. V9 was applied to the existing test database during focused verification and validated unchanged by the clean full build. No production migration execution is claimed.

```sh
cd /workspace/Backend
MAVEN_OPTS='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC' mvn -o -B -ntp -DforkCount=0 clean verify
```

The correction changes only V9, `AccountProtectionIntegrationTest`, `DatabaseMigrationIntegrationTest` and this matrix. Java canonical encoding/services and V1–V8 remain unchanged. No dependencies were downloaded, Docker images rebuilt, databases reset, broad privileges granted, or commits/pushes made.
