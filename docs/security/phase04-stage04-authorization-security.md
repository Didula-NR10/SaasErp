# Phase 04 Stage 04 — Authorization and security foundation

## Delivered

- Database-backed company authorization uses current PostgreSQL membership, company, entitlement and permission state. Company roles include OWNER, ADMIN, MANAGER, CASHIER and EMPLOYEE; decisions default to deny. Platform administrator authorization and role-assignment management are intentionally not exposed.
- Native Spring Security WebAuthn step-up binds a fresh ceremony to the current identity, company, hostname, session and canonical operation details. Consumption is expiring and single-use.
- Reusable dual approval requires distinct requester and approver identities, current authorization and separately verified step-up. PostgreSQL transitions lock state and bind the approved operation digest; expiry, cancellation and idempotency are enforced.
- Structured security audit events carry actor/company, action, target, result, timestamp and correlation ID. Database controls prevent runtime updates/deletes and event digests provide tamper evidence.
- The API boundary limits request bodies, validates correlation IDs, disables sensitive-response caching and applies restrictive API CSP and related browser headers. Existing exact-host/origin, CSRF, host-only secure session cookies and default-deny routing remain enabled.

## Migrations and operator actions

- V5 and V6 are applied to the integration database and must not be edited. V7 is a new migration that makes an expired approval terminal when consumption is attempted; it was validated and applied by the integration tests.
- For deployment, apply V7 using the existing restricted platform migration identity after V6. Do not use Flyway repair, modify prior migrations, broaden runtime grants, or reset the database. Review production grants and ensure runtime execution is limited to the intended functions.
- No production role-assignment, platform-administrator or tenant business-mutation endpoint is introduced.

## Boundaries and remaining requirements

- This is a tested foundation, not complete Stage 04 production authorization. No tenant ERP business mutation currently consumes authorization, step-up or dual approval in the same transaction. Atomic authorization of future business writes is not claimed.
- Platform administrator authority and role/permission assignment workflows remain deferred. Company session authorization does not implement tenant schema routing or paid-operation/provisioning enforcement.
- General endpoint quotas, upload limits, production proxy-aware throttling, full React CSP/Trusted Types and browser/edge verification remain outstanding.
- Audit events are append-only and tamper-evident within the database. Independent immutable storage, alerting, production retention and restoration procedures remain operator requirements.
- Production PostgreSQL/Redis ACLs, failure behavior, trusted TLS/proxy configuration, origin/RP ownership, production migration grants and independent security review still require operator verification.

## Verification

Executed offline from `/workspace/Backend`:

```bash
mvn -o -B -ntp clean verify
```

Result: **336 tests run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.** This includes PostgreSQL/Redis integration, Flyway migration/grant checks, native WebAuthn, HTTP security, tenant isolation and ArchUnit tests. It does not replace the production/operator checks listed above.
