# Tenant Context Management

## Purpose and boundaries

A tenant is an internal company identity shared by its authorized employees.
`TenantId` is an immutable Java record around a non-null UUID. Its `parse(String)`
factory accepts only lowercase canonical UUID strings in the 8-4-4-4-12 format;
uppercase, abbreviated UUIDs, whitespace and arbitrary business/schema names are
rejected. The UUID constructor accepts a non-null UUID directly. Equality and
hash codes are value based; `toString()` returns the canonical UUID string.
No identity is generated or substituted on invalid input.

`TenantContext` is an immutable record containing only a non-null `TenantId`.
It contains no employee identity, credentials, tokens or request objects.

Package placement follows ADR-0001 through ADR-0003 and the existing module rules:

- `com.saaserp.erp.shared.tenant.domain`: `TenantId`, a reusable domain value.
- `com.saaserp.erp.shared.tenant.application`: `TenantContext` and
  `TenantContextHolder`, an execution lifecycle concern outside the domain.

These classes depend only on the JDK and the tenant value object. Shared code
has no dependency on business modules, configuration, Spring, persistence or
servlet APIs. No repository ports, adapters or framework configuration are needed.

## Public API

- `new TenantId(UUID)`, `TenantId.parse(String)`, `TenantId.value()` and
  `TenantId.toString()` represent and validate internal company identity.
- `new TenantContext(TenantId)` and `TenantContext.tenantId()` represent the
  current company's context.
- `TenantContextHolder.bind(TenantContext)` returns an owned `Scope`.
- `TenantContextHolder.current()` returns `Optional<TenantContext>` for this thread.
- `TenantContextHolder.requireCurrent()` returns the context or throws
  `IllegalStateException` if absent; there is no fallback tenant or schema.
- `TenantContextHolder.Scope.close()` provides explicit cleanup and implements
  `AutoCloseable` without declaring checked exceptions.

There is no unrestricted set, replace or clear operation. The scope handle is the
only authority to release a binding. A separate presence method is unnecessary:
use `current().isPresent()` when optional behavior is appropriate.

## Usage

At a trusted boundary, after authentication, membership and applicable access
checks have succeeded, bind the already validated company identity:

```java
import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;

TenantId tenantId = TenantId.parse("12345678-1234-abcd-9876-123456789abc");
// Example identity only; parsing it does not authorize access.
try (var scope = TenantContextHolder.bind(new TenantContext(tenantId))) {
    TenantId currentCompany = TenantContextHolder.requireCurrent().tenantId();
    // Execute the company operation here.
}
// Context is empty again on this thread.
```

Always prefer try-with-resources. It performs deterministic cleanup after normal
completion, checked exceptions, runtime exceptions and errors. Manual binding
requires closing the returned handle in a finally block. This foundation cannot
clean up a scope that a caller abandons without closing it.

Same-tenant operations can nest:

```java
TenantContext context = new TenantContext(tenantId);
try (var outer = TenantContextHolder.bind(context)) {
    try (var inner = TenantContextHolder.bind(new TenantContext(tenantId))) {
        TenantContext nestedContext = TenantContextHolder.requireCurrent();
    }
    // The exact outer context object has been restored.
}
```

## Lifecycle and failure behavior

Each binding creates a private frame referring to its previous frame and owning
thread. The current frame lives in a standard `ThreadLocal`, never an
`InheritableThreadLocal`. There is no global default context.

- An empty thread may bind a context.
- Same-tenant nesting creates a separate scope and restores the exact outer
  context when the inner scope closes.
- A different tenant cannot replace any active context. Rejection leaves the
  current frame unchanged. After a valid outermost close, another tenant may bind.
- Scopes must close in LIFO order. Prematurely closing an outer scope throws
  `IllegalStateException` without marking it closed or modifying the current
  frame. Close the inner scope first, then retry the outer close.
- Only the creating thread can close a scope, including an already closed scope.
  Foreign-thread attempts throw before inspecting mutable lifecycle state and
  leave both threads' contexts unchanged.
- A successful close is idempotent on its owning thread. Reclosing a stale handle
  cannot clear a newer context, even if that context belongs to a different tenant.
- Closing the outermost scope calls `ThreadLocal.remove()`, not `set(null)`.
  Empty reads also remove the null entry that `ThreadLocal.get()` can initialize.

Null arguments throw `NullPointerException`; invalid string identities throw
`IllegalArgumentException`. Missing context, tenant replacement and invalid close
operations throw `IllegalStateException`. Messages do not echo supplied identity
strings or reveal credentials or database internals. The implementation logs nothing.

Thread-local state needs no synchronization across independent threads. Scope
lifecycle state is accessed only by its owner. Concurrent tasks remain isolated
when they establish and close their own scopes. Reused executor threads are clean
after correctly scoped operations, including failed operations. New child threads
and asynchronous tasks do not inherit a parent's context.

## Trust boundary and remaining work

Tenant Context Management alone does not provide database-level tenant isolation
or prove tenant authorization. Any internal caller can construct a UUID and bind
it on an empty thread. The holder enforces lifecycle integrity and prevents
accidental in-scope tenant replacement; it is not an authorization service.

Future authenticated tenant resolution must verify the authenticated principal,
authorized company membership and applicable server-side subscription/entitlement
checks before binding. Headers, cookies, query parameters, request bodies,
frontend tenant IDs, unverified JWT claims and subdomains are untrusted inputs,
never sufficient evidence of tenant authorization. No HTTP resolver or
authentication integration is implemented here.

Future Hibernate routing must require an active validated context, map its internal
identity to an independently validated schema identifier, and reset tenant-specific
connection state before returning a connection to the pool. It must never fall back
to the public schema. Schema identifier generation is now documented in
[Tenant Schema Identifiers](tenant-schema-identifiers.md). The subsequent
[Hibernate Tenant Routing Foundation](hibernate-tenant-routing.md) implements
routing infrastructure with tenant persistence-unit activation deferred.
Provisioning and complete database-level isolation remain unimplemented.
Existing migration and role configuration is unchanged.

Future asynchronous operations must establish an independently validated context
at their execution boundary and close it there. Do not automatically propagate
context through executors or assume a request and its tasks run on the same thread.

Future Redis operations must include the validated tenant identity in cache keys
and verify cross-tenant isolation. This implementation performs no cache access
and supplies no tenant-aware Redis integration.

The older database-migrations document describes tenant context as pending for its
migration-foundation task. This document records the subsequent context foundation;
tenant persistence activation and provisioning remain pending. The architecture overview's
JWT stack entry describes the intended stack, not authentication delivered here.
The root README is currently empty.

## Verification handoff

The new framework-independent JUnit tests cover identity validation and value
semantics, context validation, lifecycle ownership, exception cleanup, nesting,
stale handles, invalid state transitions, child-thread behavior, concurrent
isolation and reused executor threads. Concurrency uses futures and latches with
bounded waits; executors are shut down in finally blocks. No sleeps, mocks,
Spring test contexts or forced context-clearing fixtures are used.

There are 36 new test methods: 9 identity tests, 3 context tests and 24 holder
tests. The inspected checkout has 30 existing test methods (rather than the
brief's 29-test baseline), for an expected total of 66; this is a source count,
not an executed result.

Only static review was performed for this task. Compilation, test execution and
CI acceptance remain the human developer's responsibility. From the Dev Container:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=TenantIdTest,TenantContextTest,TenantContextHolderTest test
mvn -o -B -ntp -Dtest=ArchitectureTest,ModuleArchitectureTest test
mvn -o -B -ntp clean verify
```

Full verification requires the existing restricted development/test database
credentials and database setup documented in `database-migrations.md`. Do not
substitute administrator credentials or disable failing security checks.
