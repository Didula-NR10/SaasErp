# Redis Tenant Isolation Foundation

## Architecture and scope

This foundation provides explicit tenant-scoped string storage. It does not enable
Spring caching, cache any business data automatically, resolve HTTP tenants, or
implement authentication, company membership, billing or provisioning.

Package placement follows ADR-0001 through ADR-0003:

- `shared.cache.application.TenantCache` is the framework-independent application
  port: `get(namespace, logicalKey)`, `put(namespace, logicalKey, value, ttl)` and
  `delete(namespace, logicalKey)`. There is no raw-key or arbitrary-tenant argument,
  native connection, RedisTemplate, pattern deletion or global clearing method.
- `shared.cache.application.TenantCacheKey` is a JDK-only key factory outside the
  domain because it depends on the application execution context. Its sole public
  factory, `current(namespace, logicalKey)`, derives identity from
  `TenantContextHolder.requireCurrent().tenantId()` on every call. It has no public
  constructor or arbitrary-tenant overload. Exposing a generated string to the
  infrastructure does not expose raw-key operations on the application port.
- `shared.cache.infrastructure.redis.RedisTenantCache` implements the port using
  Spring Data Redis. Infrastructure depends on application, never the reverse.
- `config.RedisTenantCacheConfig` supplies a `TenantCache` bean backed by a private,
  dedicated `StringRedisTemplate` using the existing Boot `RedisConnectionFactory`.
  The template does not participate in Spring transactions and is not exposed by
  the port. PostgreSQL transactions and Redis writes are not atomic together.

The existing tenant context, PostgreSQL migrations, privileges, Hibernate routing,
connection cleanup and central schema bootstrap are unchanged. No dependencies,
Docker or CI configuration changes are needed.

## Key contract

Keys have the exact format (braces below denote placeholders, not literal Redis
Cluster hash tags):

```text
hich:tenant:{internal-tenant-uuid}:{namespace}:{logical-key}
```

For example:

```text
hich:tenant:12345678-1234-abcd-9876-123456789abc:inventory:item_123
hich:tenant:22345678-1234-abcd-9876-123456789abc:inventory:item_123
```

The internal UUID is canonical lowercase and comes exclusively from the active
trusted context. No request input is used to select a tenant. Keys are rebuilt on
every operation; no tenant identity is retained by the singleton adapter.

Components are validated rather than trimmed, normalized, hashed or encoded:

| Component | Length | Exact accepted ASCII syntax |
| --- | --- | --- |
| Namespace | 1–64 characters | `[a-z][a-z0-9_-]*` |
| Logical key | 1–128 characters | `[A-Za-z0-9][A-Za-z0-9_-]*` |

Maximum complete key length is 242 ASCII bytes. Logical key case is significant.
Null, empty, oversized, whitespace, Unicode, colons, dots, slashes, backslashes,
braces, wildcards and control characters are rejected. This prevents delimiters,
Redis glob syntax and hash tags from crossing component boundaries. There are no
ambiguous normalization aliases or wildcard-expansion operations. Callers needing
richer identifiers must design an explicit collision-free representation first;
do not concatenate untrusted fragments or relax this grammar casually.

`hich:tenant:` is reserved for tenant data. A namespace named `platform` here still
belongs to the active tenant. Future platform-wide infrastructure must use a
separate contract and root such as `hich:platform:`; this API cannot access it and
never falls back to it. The prefix is an application naming contract, not a Redis
ACL or authorization boundary.

## Context lifecycle and trust

Bind a `TenantContext` only at a trusted boundary after independently verifying
authentication, company membership and applicable access policies. The existing
holder rejects tenant switching within an active scope. Always close its scope
using try-with-resources. Missing context throws `IllegalStateException` before
any Redis interaction, including deletes and writes. Closing a scope removes the
authority to generate keys; no default tenant is substituted.

The context is thread-local and is not inherited or automatically propagated.
Async workers must independently establish an authorized context and close it on
their own thread. This implementation does not change those lifecycle rules.
The presence of a context is not proof of authorization: internal code can still
construct and bind an identity on an empty thread.

## Serialization and TTL policy

Keys and values use explicit UTF-8 string serialization via `StringRedisTemplate`.
All key, value and hash serializers in that template are string serializers. No
Java object deserialization, polymorphic JSON or arbitrary type resolution is
used. The adapter accepts non-null strings, including empty strings; a missing
Redis value is `Optional.empty()`, distinct from a stored empty string.

Every write requires a TTL from **1 millisecond through 24 hours inclusive**, in
whole milliseconds. Null, zero, negative, sub-millisecond/fractional-millisecond
and larger durations are rejected before Redis access. Comparisons precede any
conversion, so extreme durations cannot overflow a millisecond conversion. This
foundation permits no persistent writes or caller-selected unlimited lifetimes.

The adapter uses one `SET` with expiration, not SET followed by EXPIRE, preventing
a crash between two commands from leaving a persistent entry. Overwriting a key
also sets the new explicit TTL. Redis may evict values before their TTL; consumers
must treat stored data as disposable and implement any source-of-truth lookup at
the business layer. Entry-size limits and quotas remain future policy decisions.

## Failures and invalidation

The adapter does not catch or log Redis exceptions, retry operations, change
tenants or synthesize successful writes/deletes. Spring Data access exceptions,
including connection failures, timeouts and server errors such as WRONGTYPE,
propagate. Configured production connection and command timeouts are currently
5 seconds each. The adapter adds no retry loop; driver connection behavior remains
subject to the existing Lettuce configuration.

`delete` issues exactly one single-key DEL. It returns false only for a confirmed
absent key; a null/unconfirmed response throws `IllegalStateException`. There is
no bulk deletion, scan, KEYS, FLUSHDB or FLUSHALL operation in this foundation.
A tenant deleting its own key cannot delete another tenant's identical logical key.

Failures may have an ambiguous server outcome, particularly a timeout after SET
or DEL. Callers must not interpret an exception as proof the command did not run.
Future optional business caches may explicitly catch an appropriate infrastructure
failure and read their tenant-authorized source of truth, but this port does not
implement such fallback. Do not cache authorization decisions without a separate
security design. Never log cached values, raw logical keys, connection secrets or
customer data; this implementation adds no logging.

## Real Redis integration test isolation

Repository inspection found Redis 7.4, development password authentication, an
unauthenticated ephemeral CI service, and no separate test Redis settings. The
application and test profile currently use logical database 0. Tests therefore
use the same configured service/database without changing infrastructure:

- A Redis-only Spring test context imports the cached Boot 4.2.0
  `DataRedisAutoConfiguration` and actual `RedisTenantCacheConfig`. It does not
  load PostgreSQL, Flyway, JPA, web/security configuration or the main application.
- Host, port and password come from existing `REDIS_HOST`, `REDIS_PORT` and
  `REDIS_PASSWORD` environment variables. No credentials are embedded in source.
  Timeouts match the application; database 0 is explicit for this existing setup.
- Every test instance generates two fresh tenant UUIDs and an independent
  `it_` namespace containing a random test-run UUID. No real company identity or
  development cache key is used.
- Tests register exact tenant/key pairs before attempting writes, including
  potentially ambiguous failed writes. Cleanup attempts every recorded pair through
  the tenant-scoped API, reports errors and never scans or flushes the service.
  All test writes also expire within 60 seconds if cleanup is interrupted.
- Expiration uses a bounded poll and verifies the entry exists first. Concurrency
  uses bounded barriers/futures and shuts executors down before key cleanup.
- A raw string template is used only for white-box TTL and serialization reads of
  exact test-owned keys; application-facing operations use the scoped port.

No test Redis logical database or infrastructure change is required. A logical
database index would not be a security boundary. Tests still consume small shared
service resources; a dedicated ephemeral service is preferable for high-volume,
destructive or infrastructure-specific testing, which is outside this task.

## Tests and static review

New coverage consists of:

- `TenantCacheKeyTest`: 13 tests for deterministic keys, tenant/namespace separation,
  missing context, cleanup, invalid components, injection, wildcards, controls,
  boundary lengths and absence of an arbitrary-tenant factory.
- `RedisTenantCacheTest`: 17 tests for exact delegation, missing/empty values,
  atomic TTL writes, duration limits, pre-access validation, scoped deletion,
  unavailable Redis, timeout/error propagation, unconfirmed deletion and the
  restricted port surface.
- `TenantRedisCacheIntegrationTest`: 9 real-service tests for independent values,
  cross-tenant misses, updates/deletes, TTL, expiration, missing context, sequential
  worker reuse and concurrent tenant operations.

All 185 existing test methods are retained. The expected source total is **224**
(39 additions). This is a static method count, not an executed test result.

Static review checks package dependencies, context derivation on every operation,
component validation and limits, TTL conversion/atomicity, single-key invalidation,
exception paths, test cleanup, bounded concurrency and method signatures against
locally cached Spring Data Redis 4.2.0 / Spring Framework 7.1.0 APIs. Generic future
results and TTL observations have explicit types to avoid ambiguous AssertJ
inference. No Maven, tests, Redis commands or database operations were executed by
the agent; compilation and runtime behavior require human verification.

## Threat model, limitations and future ACLs

This foundation reduces accidental cross-tenant access by callers using the port.
It does not protect against compromised code with raw Redis access or shared
credentials, a malicious internal caller binding an arbitrary context, incorrect
authorization before binding, a Redis administrator, network compromise, or
sensitive data being cached contrary to policy. Boot's existing raw Redis beans
remain available to infrastructure; application modules should depend on the port.
Prefixes alone do not provide complete tenant isolation.

Future Redis ACL work should consider separate environment/service users, minimal
command permissions, key-pattern constraints such as `~hich:tenant:*`, transport
security, restricted administrative access and credential rotation. Such a shared
ACL still permits every tenant prefix for that user. Stronger per-tenant Redis
credential/connection policies require a separate design and do not replace
membership authorization. No ACL or Docker changes are implemented here.

## Human verification

Use the existing Dev Container with its Redis service and environment configured;
do not print the environment or substitute administrator database credentials.
The Redis-only integration class needs the existing REDIS variables, including an
empty `REDIS_PASSWORD` only where the service is deliberately unauthenticated.
Run these commands manually:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=TenantCacheKeyTest,RedisTenantCacheTest test
mvn -o -B -ntp -Dtest=TenantRedisCacheIntegrationTest test
mvn -o -B -ntp -Dtest=ArchitectureTest,ModuleArchitectureTest test
mvn -o -B -ntp clean verify
```

Full verification also requires the existing restricted PostgreSQL setup and
central schema histories. The existing CI Redis service and environment already
satisfy the test configuration; Surefire discovers all three new `*Test` classes.
The human developer performs execution, security review, Git and CI acceptance.
