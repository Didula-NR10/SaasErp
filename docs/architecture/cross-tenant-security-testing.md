# Cross-Tenant Security and Regression Testing

## Objectives and boundaries

Task 08 verifies that supported PostgreSQL routing and Redis cache APIs select the
same active internal tenant across successful, failed, sequential and concurrent
operations. It adds tests and documentation only. It does not activate a tenant
persistence unit or business caching, implement authentication/authorization, change
permissions or alter any production component.

The source-of-truth contracts are documented in [Tenant Context](tenant-context.md),
[Tenant Schema Identifiers](tenant-schema-identifiers.md), [Hibernate Routing](hibernate-tenant-routing.md),
[Connection Pool Isolation](connection-pool-isolation.md), [Central Bootstrap](central-schema-bootstrap.md)
and [Redis Isolation](redis-tenant-isolation.md). The existing dependency and module
rules and ADR-0001 through ADR-0003 continue to apply.

The tested adversarial cases are accidental stale context, mismatched identifiers,
missing or unauthorized schemas, incorrectly reused sessions, cache key collisions,
unsafe invalidation and dependency paths bypassing infrastructure boundaries.
These are application-contract regressions, not proof of complete authorization.

## New cross-component integration scenarios

`CrossTenantSecurityIntegrationTest` uses the actual `TenantIdentifierResolver`,
`TenantConnectionProvider` and injected `TenantCache` bean, which is wired through
`RedisTenantCacheConfig` to `RedisTenantCache`. Its helpers are test-only fixture
orchestration, not entities, repositories or business cache implementations.

| New method | Evidence required |
| --- | --- |
| `eachTenantCachesOnlyItsOwnRoutedPostgresRepresentation` | A and B read distinct unqualified PostgreSQL markers; each caches its exact schema plus marker using the same logical key. B initially misses A's cached value. Both values are subsequently checked against routed reads. |
| `tenantACacheUpdateDoesNotChangeTenantBOrEitherDatabaseFixture` | Updating A's cache changes neither B's cache nor either fixture read. |
| `tenantACacheDeletionPreservesTenantBAndBothRoutedFixtures` | Deleting A's exact key leaves B's representation and both database markers intact. |
| `reusedWorkerAndPhysicalConnectionSelectTheNewTenantInBothStores` | One executor thread and a one-connection pool process A then B; worker ID and backend PID must match while schema, marker and cache values change correctly. Raw pooled baseline is checked before another routing acquisition can repair it. |
| `simultaneousPostgresLeasesAndCacheWritesRespectIndependentContexts` | Two bounded worker tasks synchronize while both PostgreSQL leases are held, require different backend PIDs, then independently cache/read their own representations. Both workers close their scopes. |
| `missingContextRejectsDatabaseResolutionAcquisitionAndAllCacheOperations` | Resolver, tenant acquisition, cache read/write/delete reject a missing context. Existing mock unit tests additionally prove rejection before resource access. |
| `mismatchedDatabaseIdentifierCannotSwitchTheActiveCacheIdentity` | With A active, requesting B's JDBC lease is rejected; resolver, routed reads and cached values remain A's, and B's cached representation remains intact. |
| `postgresFailureClosesContextBeforeTheWorkerIsReused` | Autocommit SQL division-by-zero propagates SQLSTATE 22012, skips the subsequent cache write, safely releases the lease and closes the scope. The same worker then rejects unscoped operations and successfully processes B. A's prior cache remains intact. |
| `deniedAndMissingRoutesNeverFallBackToAnotherTenantsCachedData` | Existing denied 503 and absent 504 fail routing, skip paired writes and retain the exact context. Explicit later cache access under that identity misses A/B and can only create that identity's own entry. A/B remain unchanged. |
| `closedContextCannotAcquireDatabaseOrReuseItsCachedEntry` | After closing A's scope all unscoped operations reject; B cannot see A's entry, while rebinding A restores access to its own data. |

Every successful fixture read verifies PostgreSQL server-side `current_schema()`,
configured and exact effective search path, no temporary namespace, database name,
runtime user and the single expected marker. JDBC `getSchema()` is deliberately
not used: Hikari can cache it independently of SQL search-path changes.

The paired helper writes only after the fixture read **and lease release** succeed.
That ordering is a test harness decision, not a cross-store transaction guarantee.
A failed PostgreSQL route does not independently revoke a correctly bound Redis
context; business code must deliberately stop or choose an authorized fallback.
The negative test distinguishes this from accidentally falling back to A, B or a
global cache namespace.

## Security coverage matrix

Existing tests are retained rather than copied into another permission or key
validation suite. Names below identify executable test sources; results require
human execution.

| Requirement | Existing coverage reused / new coverage |
| --- | --- |
| Canonical internal identity and schema mapping; reject malformed identifiers | `TenantIdTest`, `TenantSchemaIdentifierTest`; provider unit test `rejectsMalformedReservedAndMaliciousIdentifiersBeforeBorrowing`. |
| Trusted context lifecycle, nesting, owner thread and cleanup | `TenantContextTest`, `TenantContextHolderTest`; new combined missing/closed context, failed worker and reused worker cases. |
| Runtime cannot create schemas/tables | `DatabaseMigrationIntegrationTest.runtimeCannotCreateSchemasOrTables`; combined setup also verifies database CREATE/TEMP denial. |
| Runtime cannot create temporary objects | `TenantRoutingDatabaseIntegrationTest.forbiddenTemporaryObjectsAndSessionAuthorizationDoNotChangeIdentity`; `DatabaseMigrationIntegrationTest.bothEnvironmentsDenyDatabaseCreateAndTempToMigratorsAndRuntime`. |
| Migrators cannot create arbitrary schemas | `migrationRoleExecutesTestMigrationAndOwnsItsIndependentHistory`, `developmentMigrationIdentityOwnsDevelopmentMigrationSchemas` and the both-environments CREATE/TEMP privilege check. These inspect effective database CREATE denial, without attempting unsafe schema creation. |
| Migrators cannot create databases or inherit runtime authority | New `DatabaseMigrationIntegrationTest.migrationIdentitiesCannotCreateDatabasesOrInheritRuntimePrivileges` reads role catalogs for both restricted migration logins: no superuser, CREATEDB, CREATEROLE, replication, BYPASSRLS, inheritance or runtime-group membership. Existing `00_create_roles.sql` supplies the reviewed policy. No CREATE DATABASE is attempted. |
| Development/test credentials cannot cross-connect | `DatabaseMigrationIntegrationTest.testAndDevelopmentRuntimeIdentitiesCannotCrossConnect` covers both runtime and migration identities in both directions; maintenance database denial is also retained. |
| Runtime cannot modify Flyway histories or assume stronger roles | `runtimeCannotCreateRolesOrModifyFlywayHistory`, `runtimeCannotAssumeAdministratorOrMigrationRoles`, plus default-privilege and history ownership checks. |
| Independent histories, immutable V1 compatibility and safe bootstrap | `CentralSchemaBootstrapTest`, `centralV1HistoryIsEitherPreservedSqlOrExplicitVerifiedBaseline`, independent-history and initializer-order tests. No bootstrap is invoked by the new combined tests. |
| Missing/inaccessible schemas never fall back | `nonexistentSchemaFailsWithoutFallbackAndLeavesPoolUsable`, `existingButUnauthorizedSchemaIsRejected`, provider admission unit tests; new combined denied/missing cache case. |
| Context/identifier agreement before connection borrowing | `TenantConnectionProviderTest.rejectsAnotherTenantEvenWhenItsUuidIsValid` and `requiresActiveContextBeforeBorrowing`; resolver tests; combined mismatch case. |
| Exact server schema/path and physical reuse | Existing routing fixture tests, especially `releaseRestoresBaselineOnTheSamePhysicalConnection`; new paired reads, reused worker and backend PID assertions. |
| Session reset before pool reuse | Existing `mutableSqlSessionSettingsAreResetBeforePhysicalReuse`, `jdbcPropertyChangesCannotUndoVerifiedBaselineOnProxyClose`, advisory-lock/LISTEN/prepared-statement and repeated-reuse cases. New combined baseline inspection verifies release before the next admission. |
| Transaction ownership, unsafe session eviction and uncertain close | Provider unit tests for OPEN/FAILED state, no transaction control, eviction failure, stale proxies, uncertain close and pool shutdown; routing integration tests for manual/JDBC failed transactions, initial dirty checkout and cancellation. |
| Tenant-only cache keys, invalid components and no arbitrary tenant override | `TenantCacheKeyTest`, `RedisTenantCacheTest.everyOperationRejectsRawKeyInjectionBeforeRedisAccess`, `applicationBoundaryExposesOnlyScopedStringOperations`; combined context/identifier consistency. |
| Platform-wide cache namespace remains inaccessible through tenant port | Strict component/delimiter tests reject injected `hich:platform` and raw full keys. The sole key factory always prepends the active `hich:tenant:<uuid>:` prefix. No platform-wide access method exists; this is a contract/source review, not a Redis ACL assertion. |
| Mandatory bounded expiration and string serialization | Adapter TTL/empty-value tests; real Redis UTF-8 round-trip, TTL and expiration tests in `TenantRedisCacheIntegrationTest`. |
| Tenant-specific invalidation | Unit exact-key delegation and Redis-only deletion/concurrency tests; new combined update/delete cases. |
| Redis failure and timeout propagation; no retry/fallback | Adapter failure/timeout/unconfirmed-delete unit tests. Combined SQL failure and denied-route cases show cache state and context do not change to another tenant. |
| Concurrent and sequential cross-component isolation | New bounded two-lease and same-worker/same-backend cases; existing holder, provider and Redis-only concurrency coverage remains intact. |
| Inner layers cannot bypass cache/routing contracts | Existing `ArchitectureTest`, `ModuleArchitectureTest`; new `TenantBoundaryArchitectureTest` described below. |

## Architecture checks

Three new `TenantBoundaryArchitectureTest` cases exclude test classes and inspect
production bytecode with the existing ArchUnit 1.5.1 dependency:

- Application/domain code cannot depend on Spring Data Redis, Lettuce or Jedis raw
  clients. Infrastructure adapters and configuration wiring remain permitted.
- Application/domain code cannot depend on JDBC, DataSource, pgjdbc, Hikari or
  Hibernate JDBC engine clients. Existing rules already prohibit dependencies on
  infrastructure implementation classes, including the routing provider.
- The specific tenant cache application package stays independent of Spring,
  Hibernate/JPA, infrastructure and configuration packages.

These rules enforce direct static dependencies. They do not detect malicious
reflection, dynamic bean lookup, leaked handles or arbitrary code execution.
No existing architecture rule was removed or relaxed.

## Fixture isolation and reliability

The existing reviewed `04_create_test_tenant_fixtures.sql` provides read-only
`routing_probe` data for tenants 501 (`task05-a`) and 502 (`task05-b`), schema 503
without runtime USAGE, and intentionally absent schema 504. Combined tests verify
these preconditions and the actual `hich_runtime_test` / `hich_erp_test` connection
before copying those credentials into test-owned pools. They never create, update
or delete PostgreSQL fixture data or change any privileges. They issue provider
session-reset SQL and read-only fixture/catalog queries only, apart from a deliberate
failing arithmetic SELECT. Existing migration tests retain their established
separate test fixture behavior.

Redis keys use a new random `security_<uuid-without-hyphens>` namespace per test
instance and the existing fixture tenant IDs. Those tenant IDs must remain fixed
because PostgreSQL fixtures are fixed; randomized namespaces isolate test runs.
All writes use the identical logical key `same_record` and a 60-second TTL. The
thread-safe ownership set is populated before SET, so an ambiguous failed write
is still scheduled for exact-key cleanup. Cleanup uses scoped single-key deletion
for every registered identity, reports failures, and closes the private pool in a
finally block. No scans, pattern deletion, flushes or development cache entries
are involved.

Single-connection pools make healthy physical reuse deterministic. Two-connection
pools and a bounded barrier prove overlapping leases; futures use explicit typed
results and bounded waits. Workers assert empty context before and after scoped
work, shutdown occurs in finally, and concurrent pools close after executor shutdown.
Each test has a 45-second timeout; JDBC query/socket timeouts and pool acquisition
limits mirror the existing routing test conventions. There are no arbitrary sleeps
or timing-based expiration assertions in this new suite; Redis expiration coverage
is reused from Task 07.

All context scopes use the existing holder contract. Provider-owned leases are
released only through that provider, after statements/results close. Successful
lease release is required before caching a database-derived representation.
A failure still triggers scope, statement/result, pool and test-key cleanup.

## Session cleanup security review and findings

The Task 06 policy and its tests were reviewed without changing production code:

- Admission/release inspect both JDBC autocommit and pgjdbc server transaction state;
  they do not commit, rollback or toggle autocommit to recover an owner's work.
- DISCARD ALL, driver notification draining, JDBC state synchronization and server
  verification precede reuse. Unsafe cleanup fails closed through eviction; failed
  eviction or uncertain close ownership can stop the pool.
- Session assertions inspect the effective PostgreSQL schema/path, not the cached
  JDBC schema getter. The known cached-null getter behavior remains documented.
- Direct proxy close, escaped statements, concurrent use of one lease and arbitrary
  vendor/extension session mutation remain unsupported ownership violations. A
  caller closing a provider lease directly can bypass its release cleanup. This is
  an existing limitation, not something stale-handle rejection can retroactively fix.
- Hikari/catalog/network defaults and login defaults must remain stable during the
  pool lifetime; role/database default changes need pool recreation and revalidation.
  Unknown JDBC/extension state and shutdown failures require operational handling.

No new confirmed production-code defect was identified by static review. A coverage
gap in direct post-bootstrap migrator role-flag assertions was closed by the new
read-only catalog test. Runtime behavior, full driver compatibility and operational
failure handling still require execution/review by the human developer. No production
fix or permission broadening was made under this testing task.

## Threat model and security limitations

Shared PostgreSQL runtime credentials can potentially read other explicitly
qualified tenant schemas where they have privileges. Routing controls supported
unqualified access; it does not provide tenant-specific PostgreSQL credentials or
revoke those cross-schema grants. Shared Redis credentials can potentially access
multiple tenant prefixes through raw clients. Logical databases and key prefixes
are not credential-level isolation.

Tenant context is neither authentication nor authorization. Trusted binding must
eventually depend on verified company membership and applicable access policies.
Compromised application code can bind an arbitrary identity or use raw clients;
schema routing and key namespacing alone do not protect against it.

The production tenant persistence unit remains inactive. These tests invoke the
routing SPI directly and do not prove production Hibernate activation, repository
isolation or complete HTTP tenant authorization. PostgreSQL and Redis do not share
an atomic transaction; crashes, timeouts and partial cross-store outcomes remain
possible. Passing this suite cannot establish those deferred guarantees.

## Counts, verification and remaining acceptance

The existing 224 test methods remain intact. Added methods:

- 10 combined PostgreSQL/Redis integration tests.
- 3 tenant boundary architecture tests.
- 1 migration-role catalog integration test.

Expected source total: **238 tests**. This is a static count, not a run result.
Only test sources and this document change. CI, Docker Compose, dependencies,
permissions, migration histories and production source remain unchanged.

The existing Dev Container and CI services already provide PostgreSQL 17 and
Redis 7.4. No new infrastructure settings, privileges, credentials or dependencies
are needed. The human must have the reviewed roles, restricted dev/test credentials,
central schema bootstrap/history and Task 05 fixtures already installed. Do not run
fixture scripts again against existing fixture names or repair histories to make
verification pass. No administrator credentials are needed by the new tests.

From the existing configured Dev Container, the human developer should run:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=CrossTenantSecurityIntegrationTest test
mvn -o -B -ntp -Dtest=DatabaseMigrationIntegrationTest,TenantRoutingDatabaseIntegrationTest,TenantConnectionProviderTest test
mvn -o -B -ntp -Dtest=TenantCacheKeyTest,RedisTenantCacheTest,TenantRedisCacheIntegrationTest test
mvn -o -B -ntp -Dtest=ArchitectureTest,ModuleArchitectureTest,TenantBoundaryArchitectureTest test
mvn -o -B -ntp clean verify
```

Static compilation-risk review covers imports, actual tenant APIs, Java 21 record/
thread APIs, Hikari signatures, JDBC parameter/result types, ArchUnit package
selectors, JUnit lifecycle/futures and explicit AssertJ inference. Maven, compilation,
automated tests, Redis commands and database operations were not executed by the
agent. The human developer owns execution, security verification, Git and CI.

Remaining Phase 03 acceptance requires the human to execute the complete 238-test
suite on the established local environment and fresh CI, review the coverage matrix
and ownership contract, confirm cleanup and least-privilege behavior, and investigate
any failures without weakening assertions. This task does not declare Phase 03
accepted merely because test sources exist.

Deferred security work includes verified HTTP/JWT company membership and tenant
selection, subscription/entitlement enforcement, tenant provisioning/migration
lifecycles and races, tenant persistence-unit activation and repository tests,
authorization-aware asynchronous jobs, cache poisoning/invalidation policy, real
production Redis ACL/TLS/credential rotation, database/Redis administrator access,
deployment network controls, availability/failover, backup/restore and operational
pool-shutdown recovery. These need tests against their actual future implementations.
