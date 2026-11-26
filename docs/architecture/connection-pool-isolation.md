# PostgreSQL Connection Pool Isolation

## Status and scope

Task 06 extends the existing tenant connection provider. Tenant persistence-unit
activation remains deferred. The central Spring Boot entity manager factory,
platform/billing Flyway streams and restricted runtime datasource are unchanged.
No resolver/provider bean is registered globally.

This document specifies the implemented session-reset policy and tests supplied
for human verification. No Maven compilation, tests, SQL setup or CI verification
were executed for this task. The task brief reports a previously verified baseline
of 133 tests; the current source has 30 additions and 163 expected test methods.

## Confirmed implementation risks addressed

Static inspection of Task 05 and cached dependency bytecode established that:

- Restoring only `search_path` leaves unrelated SQL settings and session resources.
- JDBC autocommit does not indicate a manually opened or failed SQL transaction.
- Hikari's proxy `close()` can explicitly roll back a dirty JDBC transaction.
  It also recycles its pool entry in a finally block, including some failure paths.
- A directly closed old proxy can still refer to a pool entry used by a newer
  borrower. Evicting through that stale proxy can affect the newer lease.
- Membership in a runtime group alone does not pin the expected configured login.

The provider now verifies the configured login, checks driver transaction state,
fully resets supported SQL session state, and handles stale/uncertain ownership
without evicting a newer entry through an old proxy. Unsafe eviction no longer
performs an ordinary proxy close afterward.

## Lifecycle and ownership

Acquisition validates the canonical tenant UUID and its equality with the trusted
context before borrowing. Missing/malformed/mismatched identities never obtain a
connection. Bootstrap acquisition has no tenant identity.

After borrowing the original Hikari proxy:

1. Require JDBC autocommit and PostgreSQL driver transaction state `IDLE`.
2. Verify the exact configured login, effective/session-user equality, runtime
   membership, restricted role attributes and absence of database CREATE/TEMP.
3. Execute the session reset, synchronize tracked JDBC properties and verify it.
4. Establish and verify the catalog-only baseline `pg_catalog, pg_temp`.
5. Recheck database identity; derive the schema using `TenantSchemaIdentifier`.
6. Require tenant schema existence, runtime USAGE and absence of runtime CREATE.
7. Apply and verify the exact tenant path; require server state still idle.
8. Record one lease keyed by the borrowed proxy's object identity.

The runtime guard uses the explicitly configured `HikariDataSource.getUsername()`;
an absent/blank username rejects provider construction. No password is read by the
provider and there is no migration/administrator credential configuration.

Release atomically removes the identity-keyed lease. Unknown and duplicate releases
fail without touching the connection. Metadata and tenant lease types and tenant
IDs must match. A directly closed stale proxy is rejected without eviction, because
its pool entry might already be used elsewhere. An ownership-check error stops the
pool instead of guessing which physical lease is still owned.

An owned open lease is checked for idle state and expected identity, fully reset,
returned to the catalog-only baseline, verified again and only then closed back
to Hikari. Cleanup uses the lease identity and works after thread-local context
cleanup. Independent leases share only a synchronized identity map; no mutable
global tenant identity is introduced. Successful releases and initialization
failures leave no active lease entry.

The Hibernate SPI necessarily returns a JDBC connection. It cannot stop trusted
internal callers from escaping statements/results, unwrapping, directly closing a
proxy or using one connection concurrently. These are unsupported ownership
violations. In particular, a caller's direct close can bypass cleanup before this
provider sees it; stale-handle rejection cannot undo that exposure. Only the
provider may release its owned handles, and all statements/results must remain
inside that lease. No raw JDBC connection is exposed through an HTTP API.

## Session reset policy

`PostgresSessionState` is package-private PostgreSQL infrastructure. It executes
`DISCARD ALL` through a normal JDBC Statement only after the idle and identity
checks. It never runs commit, rollback or setAutoCommit.

`RESET ALL` alone is insufficient: it does not clear advisory locks, LISTEN state,
prepared statements or held cursors. A small targeted GUC allowlist also misses
other mutable built-in settings. PostgreSQL's full discard resets settings to their
startup/role/database reset values and releases supported server session resources.
The following application baseline is then established and checked:

| State | Reusable policy |
| --- | --- |
| search_path | `pg_catalog, pg_temp`; effective schemas exactly `pg_catalog`; no temporary namespace. |
| Customer path while leased | Validated tenant plus explicit `pg_temp`; effective schemas exactly `pg_catalog`, then that tenant. |
| Login/role | Configured runtime username equals both effective and session user; original privilege guards remain mandatory. |
| JDBC autocommit / server transaction | Autocommit enabled and driver state `IDLE` at both handoffs. |
| Transaction isolation | Named Hikari isolation configuration, or PostgreSQL's reset/default isolation if unspecified; JDBC state synchronized and SQL default/current values checked. |
| Transaction read-only | Hikari's configured read-only value; JDBC and SQL default/current values synchronized and checked. |
| TimeZone | `UTC`, matching the existing Hibernate JDBC time-zone requirement. |
| application_name | `hich-erp`, matching the existing application name. |
| statement_timeout | 30 seconds. |
| lock_timeout | 5 seconds. |
| idle_in_transaction_session_timeout | 30 seconds. |
| Other built-in GUCs | Reset to PostgreSQL reset values by DISCARD; representative extra-setting coverage uses `enable_seqscan`. |
| Advisory locks | Released by DISCARD; no advisory locks for this backend may remain. |
| LISTEN | Removed by DISCARD; listening channels must be empty. |
| Notifications already in the driver | Drained after server subscriptions are removed, without logging payloads. |
| SQL PREPARE / held cursors | Discarded; no `from_sql` prepared statements or holdable cursors may remain. |
| Driver-managed prepared statements | Allowed after reset; pgjdbc invalidates discarded server statement names before reuse. |
| Existing temporary namespace | Conservative rejection/eviction even if its objects were discarded; no TEMP privilege is granted. |

The timeout values are explicit infrastructure policy, not new environment or CI
settings. Cleanup statements have a five-second JDBC query timeout. A very small
inherited statement timeout, dead session or failed reset can prevent cleanup;
that connection is evicted rather than reused. JDBC socket/network failures may
still require operational recovery. Test pools use a five-second driver socket
timeout and a three-second acquisition timeout to bound failure tests.

Hikari tracks JDBC read-only, isolation, schema, catalog and network-timeout changes,
but does not reset arbitrary SQL-level GUCs. After DISCARD, the provider explicitly
synchronizes read-only and isolation through the proxy and synchronizes its schema
tracking before setting the verified path. This prevents a dirty Hikari schema or
isolation flag from reintroducing an old value during proxy close. Catalog/network
properties remain subject to Hikari's ordinary configured handling; the JDBC
property integration case checks network-timeout restoration on the reused session. Arbitrary
vendor API, client-side type-map/client-info/driver-cache mutations and extension
session state are not certified by this policy.

HikariCP 7.1.0 caches the driver's current schema after `setSchema()`. Task 06's
`setSchema(pool.getSchema())` can cache null for a restricted login when resetting
to the default path with no accessible user/public schema. Later SQL `set_config`
changes do not refresh Hikari's cache: proxy `getSchema()` can report that cached
value even though PostgreSQL is correctly routed. Task 05 did not call the JDBC
schema setter, so its getter delegated to the driver, which queries `current_schema()`.
The physical-reuse integration test therefore checks `pg_catalog.current_schema()`,
the configured and exact effective search paths, absence of temporary namespaces,
and the tenant fixture marker directly on the server. It still checks backend PID
reuse and the clean baseline before another provider acquisition can repair state.
The cached getter is not an authority for effective server routing. The JDBC setter
remains necessary for cleanup tracking; removing it to change getter behavior would
undermine the session-reset contract.

Pool configuration and role/database reset defaults must be stable for the pool's
lifetime. Administrative changes to login defaults require pool recreation and
reverification; otherwise Hikari's cached startup defaults may differ from later
server reset defaults. Supported explicit isolation names are the four standard
`TRANSACTION_*` levels PostgreSQL accepts; unsupported settings fail closed.

## Driver and pool compatibility

Cached APIs/bytecode were inspected for Boot 4.2.0, Hibernate 7.4.15.Final,
PostgreSQL JDBC 42.7.14 and HikariCP 7.1.0. No versions or dependencies were added.
The existing PostgreSQL dependency changes from runtime scope to default compile
scope so infrastructure can directly use the exact driver transaction-state API.
It remains a runtime dependency as before; no reflective invocation is needed.
Domain/application code does not depend on it.

`BaseConnection.getTransactionState()` distinguishes `IDLE`, `OPEN` and `FAILED`,
including SQL BEGIN while JDBC autocommit remains true. This is a version-specific
core driver API: recheck it and these security tests before upgrading pgjdbc.
A failed unwrap, closed physical session or unknown state fails closed.

The inspected `QueryExecutorImpl` recognizes the `DISCARD ALL` completion tag and
increments its prepared-statement invalidation epoch when cache flushing is enabled.
The reset explicitly enables that existing driver behavior. Driver-managed prepared
statements can be reparsed; they must not be retained by application code beyond a
lease. Integration tests set `prepareThreshold=1` only on isolated test pools and
exercise identical prepared SQL across tenants on the same backend. SQL-level
named PREPARE is tested separately and must disappear after release. No production
prepared-statement cache configuration is changed.

DISCARD is PostgreSQL-specific, cannot run inside an active transaction and clears
server plans/resources. It is not applied blindly to arbitrary pooled connections:
identity and both transaction indicators are checked first. It resets supported
SQL session state; it is not a claim that every conceivable extension or escaped
JDBC resource is reversible.

## Transaction protection and physical invalidation

JDBC autocommit alone is never the transaction guard. Both handoffs require server
state IDLE before any reset SQL. OPEN/FAILED/unknown state causes eviction. Direct
SQL transaction control is unsupported application usage, but its retained server
state is detected. The provider never completes an owner's transaction to make a
connection reusable. Physical destruction aborts outstanding server work instead.

Normal successful cleanup returns the verified proxy to Hikari. Unsafe states,
initialization failures, mismatched owned releases and cleanup failures call
`HikariDataSource.evictConnection()` with the original borrowed proxy. Cached
Hikari code confirms that it marks/removes the owned entry and schedules physical
closure. There is no ordinary proxy close after eviction: that would risk an
implicit Hikari rollback and redundant recycling. This also avoids double-close
and duplicate cleanup-error artifacts.

If a normal close throws while the proxy still appears owned, eviction is attempted
without a second close. If it already appears closed, ownership may have passed to
a newer borrower: the pool is stopped instead of evicting through a stale proxy.
Eviction errors also trigger last-resort pool shutdown. Original causes and
suppressed invalidation/shutdown failures are preserved. If even shutdown fails,
no unsafe proxy is ordinary-closed for availability; operational recovery is required.

Shutdown can interrupt unrelated work sharing the pool. It is reserved for failed
invalidation or uncertain ownership, not ordinary session contamination. Healthy
sessions are reused on the same backend after verified reset; not every physical
connection is discarded. Full resets on both acquisition and release add SQL round
trips and discard prepared server plans. That cost is intentional; any future
optimization must preserve this isolation contract and its integration tests.

## Tests and human verification

The existing 133 tests remain represented. The provider's original 36 cases were
extended for baseline-before-admission and the eviction-without-proxy-close contract;
unsafe-close assertions now require no proxy close. The previous redundant second
close failure assertion now requires one close attempt and the original cause.
A cleanup exception test now covers suppressed eviction failure. Security coverage
was not removed or skipped.

There are 17 added provider unit tests and 13 added PostgreSQL integration tests:
53 provider, 6 resolver and 21 routing integration methods, with 163 total source
annotations across the backend. New cases cover full reset, configured defaults,
server OPEN/FAILED/unknown state, reset/verification failure, stale/uncertain
ownership, exact exception retention, bounded concurrent leases and tracking cleanup.

Actual PostgreSQL cases cover same-backend reuse, SQL/JDBC setting contamination,
advisory locks, LISTEN/queued notifications, SQL and driver prepared statements,
repeated tenant alternation, dirty initial checkout, manual and JDBC aborted
transactions, timeout cancellation, privilege denial and simultaneous tenants in a
bounded two-connection pool. Assertions inspect pooled state directly before a
fresh provider acquisition can conceal a broken release reset. Concurrency uses
latches/futures with deadlines, not sleeps. `pg_sleep(1)` appears only as a deliberately
slow statement for a 100ms server statement-timeout test, never as synchronization.

Reuse the existing test-only fixtures in
[infrastructure/postgres/04_create_test_tenant_fixtures.sql](../../infrastructure/postgres/04_create_test_tenant_fixtures.sql):
501/502 have markers `task05-a`/`task05-b`, 503 denies runtime USAGE and 504 remains
absent. No fixture data, schemas, grants, scripts or CI were changed. No additional
fixtures or privileges are required. Missing fixtures fail explicitly; tests are
not skipped. TEMP/session-authorization positive contamination cannot be created
under the restricted role; tests verify denial rather than granting capabilities.
All application tests use only the configured restricted runtime credentials.

From the existing Dev Container, the human developer should run:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=TenantConnectionProviderTest,TenantIdentifierResolverTest test
mvn -o -B -ntp -Dtest=ArchitectureTest,ModuleArchitectureTest,TenantContextHolderTest,TenantIdTest,TenantSchemaIdentifierTest test
mvn -o -B -ntp -Dtest=TenantRoutingDatabaseIntegrationTest test
mvn -o -B -ntp clean verify
```

No command above was executed by the agent. API inspection and static review do
not establish that compilation, PostgreSQL integration tests or CI passed.

## Security boundary

Verified reusable session state is a separate concern from application tenant
routing, database-enforced authorization and authenticated company membership.
A shared runtime role able to access multiple tenant schemas can still execute
fully qualified cross-tenant SQL if application code is compromised. Search-path
routing does not prevent it. No application-wide tenant isolation is claimed.

Authentication/membership resolution and tenant persistence-unit activation remain
future work. Central schema-qualified SQL remains compatible with the catalog-only
baseline; central Boot wiring and existing migration/privilege tests remain intact.
