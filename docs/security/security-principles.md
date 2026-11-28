# Security principles

Phase 04 — Task 01. Proposed baseline, 2026-11-27.

These are the canonical SEC-PRINCIPLE requirements. Normative vocabulary, priorities, phase/owner definitions, status meanings, trust boundaries and residual risk IDs are defined in [security-requirements.md](security-requirements.md). They apply to the existing modular monolith and every future delivery phase. Planned means a governing proposed requirement; existing individual controls do not establish full-system implementation of a principle.

A customer’s OS, browser, extensions and frontend remain outside the backend trust boundary. A UUID, header, role claim, price or paid flag is untrusted until independently checked by the responsible server boundary. Security protects authority using layered controls; it cannot make a compromised authorized browser trustworthy. No individual principle or control provides complete security.

### SEC-PRINCIPLE-001 — Defense in Depth

- **Requirement:** Designs MUST combine independent preventive, detective and recovery controls across client, application, data and operational boundaries.
- **Rationale:** One control can fail.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Review records identify at least two independent controls for each critical asset. Negative — A design relying solely on a WAF, CSP or schema prefix fails review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-002 — Zero Trust

- **Requirement:** Each protected operation MUST establish identity, device/session assurance where applicable, resource authorization and current tenant policy without trusting network location.
- **Rationale:** Internal placement is not authority.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — The same authorization policy applies through public and internal entry points. Negative — An internal-network request with no valid authority is denied.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-003 — Least Privilege

- **Requirement:** Human and machine identities MUST receive only documented necessary permissions, with periodic review and time-bounded elevated access.
- **Rationale:** Limit consequences of compromise.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Role review records identify owner, purpose and expiry for every elevation. Negative — Runtime credentials cannot assume a migration or administrator role.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-004 — Deny by Default

- **Requirement:** Unmatched routes, permissions, memberships and policy cases MUST deny protected access.
- **Rationale:** New features must not inherit broad access.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — An explicitly authorized operation succeeds. Negative — A new unclassified protected route is blocked.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-005 — Fail Closed

- **Requirement:** An unavailable or indeterminate required security decision MUST deny the protected operation without tenant, credential or policy fallback.
- **Rationale:** Failures cannot create authority.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Access resumes after required policy services recover and validate state. Negative — Timeouts and corrupt security state grant no access.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-006 — Secure by Design

- **Requirement:** Security review MUST precede release of new trust boundaries and sensitive workflows, retaining modular monolith and inward dependency rules.
- **Rationale:** Late boundary repairs are costly.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — A reviewed design lists assets, entry points and abuse cases. Negative — A feature bypassing application contracts is rejected.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-007 — Secure by Default

- **Requirement:** Fresh installations MUST expose only necessary capabilities and MUST require explicit reviewed configuration for risky features.
- **Rationale:** Operators should not have to discover unsafe defaults.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — A minimal supported configuration denies unnecessary interfaces. Negative — Debug output and default administrator credentials fail release review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-008 — Separation of Duties

- **Requirement:** Critical role grants, deployments, recovery and selected financial operations MUST separate requester and approver identities.
- **Rationale:** One compromised operator should not control an entire critical workflow.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Independent authorized approval completes a critical workflow. Negative — A requester approving their own critical action is denied.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-009 — Complete Mediation

- **Requirement:** Authorization MUST run at every protected entry point and state-changing execution boundary, including background jobs and alternate APIs.
- **Rationale:** Earlier checks can become stale.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Current authority is checked at execution and commit-sensitive boundaries. Negative — A stale approval or alternate route cannot bypass current policy.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-010 — Minimization of Attack Surface

- **Requirement:** Releases MUST inventory and remove unnecessary endpoints, dependencies, privileges and exposed administrative functions.
- **Rationale:** Fewer reachable capabilities reduce exposure.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — An exposure inventory matches deployed routes and ports. Negative — An undocumented debug endpoint fails release review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-011 — Blast Radius Reduction

- **Requirement:** Designs MUST constrain compromise by tenant, environment, account and administrative scope using explicit boundaries.
- **Rationale:** A shared process is a broad trust zone.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Containment review identifies scopes and credential reach. Negative — A claim that schema routing contains backend-process compromise fails review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-012 — Explicit Trust Boundaries

- **Requirement:** Designs MUST identify every transition of identity, data and authority and name its enforcing component.
- **Rationale:** Unstated assumptions conceal bypasses.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — Each boundary in the boundary inventory has an enforcement owner. Negative — Client-supplied claims without backend validation fail review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.

### SEC-PRINCIPLE-013 — Prevention, Detection, Containment and Recovery

- **Requirement:** Each critical threat class MUST have prevention, detection, containment and tested recovery procedures with operational owners.
- **Rationale:** Prevention alone cannot handle successful attacks.
- **Priority:** P0.
- **Component / trust boundary:** All trust boundaries.
- **Responsible phase:** P4-DESIGN + all delivery phases (owner defined above).
- **Implementation status:** Planned.
- **Evidence:** No implementation evidence identified.
- **Verification method:** Architecture review and tabletop exercises.
- **Measurable acceptance criteria:** Positive — An exercise records detection, revocation, restoration and owners. Negative — A runbook containing only blocking controls fails review.
- **Limitations / residual risk:** Residual risks R1–R11 apply; this control provides no complete security guarantee.
