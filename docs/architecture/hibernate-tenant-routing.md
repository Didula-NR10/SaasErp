# Hibernate Tenant Routing Foundation

## Activation and persistence boundaries

Task 05 implements routing infrastructure and supplies automated tests. It does
not activate a tenant persistence unit. The existing Boot-managed entity manager factory remains the
central persistence unit for platform and billing operations. It requires no
customer context. Central entity mappings and native SQL must explicitly qualify
`platform` or `billing`; they must not depend on a customer's search path.

The repository has no tenant entities, tenant repositories or provisioning
boundary. A separate tenant entity manager factory and transaction manager must
be introduced when those capabilities exist. It must scan only tenant entities;
central repositories must remain bound to the central factory. Do not globally
register these components using a `HibernatePropertiesCustomizer` bean, component
scanning or global `spring.jpa.properties` settings. There are deliberately no
Spring annotations or bean registrations on the new classes.

Independent platform/billing Flyway instances, initializer ordering, the runtime
datasource and `hibernate.ddl-auto=validate` remain unchanged. No schema-generation
setting is changed to create, create-drop or update. No additional production
datasource or Flyway initializer is created.

## Locally verified API compatibility

Read-only `javap` inspection of the locally cached dependencies confirmed:

| Component | Cached/managed version | Contract used |
| --- | --- | --- |
| Spring Boot | 4.2.0 | Existing central auto-configuration stays unchanged; `HibernatePropertiesCustomizer` is in `org.springframework.boot.hibernate.autoconfigure`. |
| Hibernate ORM | 7.4.15.Final | Generic `CurrentTenantIdentifierResolver<String>` and `MultiTenantConnectionProvider<String>`; provider overrides `handlesConnectionSchema()`. |
| HikariCP | 7.1.0 | `HikariDataSource.getConnection()` and `evictConnection(Connection)` on the original borrowed proxy. |
| PostgreSQL JDBC | 42.7.14 | JDBC connections support PostgreSQL session configuration; `setSchema` alone is insufficient verification. |

The Boot dependency-management POM matches these cached versions. No dependencies
were added, upgraded or downloaded. This is API inspection, not compilation.

Hibernate's inherited read-only connection methods delegate to normal acquisition
and release. The provider declares `handlesConnectionReadOnly() == false`, leaving
read-only state to Hibernate/Hikari, and `supportsAggressiveRelease() == false`.
It unwraps only to its own class or `MultiTenantConnectionProvider`, never to the
pool or a raw connection. Null unwrap types are rejected.

## Location and identity resolution

Both new classes reside in
`com.saaserp.erp.shared.tenant.infrastructure.persistence`. This reusable technical
integration depends inward on the existing shared application context and domain
values. Domain and application classes remain unchanged and framework independent.
No business module, vertical or application-config dependency is introduced.

`TenantIdentifierResolver.resolveCurrentTenantIdentifier()` obtains only
`TenantContextHolder.requireCurrent().tenantId().toString()`. It returns the
canonical internal company UUID, not the schema name or an employee identifier.
Missing context throws `IllegalStateException`, including after a previous scope
has closed. It requests existing-current-session validation and never declares a
root tenant or administrator bypass.

`TenantConnectionProvider.getConnection(String)` parses the UUID using the existing
`TenantId` and requires equality with the current trusted context before borrowing.
Malformed strings, raw schema names, reserved names and missing identities fail
before database access. Valid UUIDs for a different active company also fail.
Schema derivation uses only `TenantSchemaIdentifier.from(tenantId)` and its stable
39-character naming contract.

Binding or parsing an identity is not authorization. Future authentication must
validate the principal, company membership and applicable subscription/entitlement
before entering tenant persistence. No request header, cookie, query/body value,
subdomain or unverified token claim is accepted by these components.

## Restricted runtime and effective search path

The provider receives the existing restricted runtime `HikariDataSource`, never
a migration or administrator datasource. It has no credential configuration or
credential fallback. A runtime guard verifies that the effective user equals the
session login, belongs to `hich_runtime`, has none of the superuser/role-creation/
database-creation/replication/RLS-bypass attributes, and has neither database CREATE
nor TEMP privilege. A tenant schema must exist, grant runtime USAGE and deny runtime
CREATE. Failure does not create a schema or broaden privileges.

Schema switching uses a prepared, parameterized function call:

```sql
SELECT pg_catalog.set_config('search_path', ?, false)
```

The bound value is the generated schema identifier followed by `, pg_temp`.
There is no arbitrary identifier interpolation into SQL and no quoting utility.
All verification functions and catalog relations are explicitly qualified with
`pg_catalog` so a changed path cannot shadow them.

The provider verifies all of the following before returning a tenant connection:

- The returned `set_config` value equals the requested path.
- `current_setting('search_path')` equals the exact requested path.
- `current_schema()` equals the generated tenant schema.
- `current_schemas(true)` contains exactly `pg_catalog` and that tenant schema,
  in that order.
- `pg_my_temp_schema()` is zero.

PostgreSQL implicitly searches its system catalog before the tenant when
`pg_catalog` is omitted from the configured path. It is not a customer fallback
schema. The explicit `pg_temp` placeholder prevents PostgreSQL from implicitly
prepending a temporary namespace. An existing temporary namespace is rejected
altogether, including a namespace whose temporary objects were dropped. This is
conservative protection against temporary-table/type shadowing; the runtime must
not have TEMP privilege. Broader session-state hardening remains Task 06.

There is no `public`, platform, billing, information-schema, administrator or
second-tenant entry in the effective tenant path. Nonexistent or inaccessible
schemas cannot silently disappear from the path and fall back to another company:
existence/privilege checks and effective-path verification must both succeed.

## Bootstrap and release lifecycle

`getAnyConnection()` is metadata access, not tenant access. It does not consult an
active customer context and never invents a bootstrap UUID. It uses the same
restricted runtime pool and explicitly establishes `pg_catalog, pg_temp` with an
effective path of exactly `pg_catalog` and no temporary namespace. The null identity
in the private lease record distinguishes metadata ownership only; it is never a
resolver return value or customer fallback. Use `releaseAnyConnection()` for it.

Every successful acquisition is tracked by connection object identity. Normal
releases must match the original tenant identity and lease type; bootstrap and
tenant releases cannot be interchanged. Unknown or already released handles are
rejected without touching another component's connection. Mismatches on owned
leases invalidate that lease instead of returning it to the pool.

Release does not require the request's thread-local context to remain present:
cleanup must still work after a scope closes or an operation fails. It uses the
recorded lease identity. Normal release rechecks runtime privileges, restores the
catalog-only baseline, verifies it, and only then closes the proxy back to Hikari.
It does not restore an arbitrary previous path or reset to a server/user default
that could expose a previous tenant. When the same pool is shared, subsequent
central SQL must remain schema-qualified.

Acquisition and release require JDBC autocommit to be enabled at the handoff.
Hibernate or another transaction owner may disable it while using the connection,
but must complete its transaction and restore the original autocommit state before
returning it. The provider never calls commit, rollback or setAutoCommit. A reset
performed inside a transaction could later be undone by rollback; therefore a
non-autocommit handoff is rejected and the connection evicted rather than reset
inside that transaction. This conservative rule may discard an otherwise healthy
connection whose owner forgot to restore autocommit. It is intentional.

On acquisition, switching, verification, reset or close failure, the provider
invalidates the original borrowed Hikari proxy using `evictConnection` before any
ordinary close. Cached Hikari bytecode confirms that eviction marks the pool entry
evicted and removes the owned entry for physical closure; an ordinary proxy close
alone is not physical destruction. No unwrapped connection is used for eviction.

If eviction throws, the provider attempts to shut down the pool before closing the
proxy. This last-resort fail-closed action can interrupt other work sharing that
pool. If both eviction and shutdown fail, it does not close/recycle the unsafe
handle; it remains checked out and requires operational recovery. SQL causes and
suppressed cleanup failures are retained. Top-level messages contain no credentials,
raw malicious identifier input or customer data; callers must not expose exception
cause chains through HTTP responses. These components log nothing.

Physical invalidation can abort an unfinished transaction. It does not transfer
transaction ownership to the provider or issue transaction-control statements on
another component's behalf. Callers must use standard JDBC transaction control;
manual SQL BEGIN/COMMIT, direct proxy close/unwrap, concurrent connection use and
arbitrary connection escape are outside this internal API contract.

## Future tenant persistence integration

The intended construction contract is:

```java
import com.saaserp.erp.shared.tenant.infrastructure.persistence.TenantConnectionProvider;
import com.saaserp.erp.shared.tenant.infrastructure.persistence.TenantIdentifierResolver;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.cfg.MultiTenancySettings;
import java.util.HashMap;
import java.util.Map;

// Method fragment for future tenant factory configuration. The parameter must be
// the existing qualified runtime pool, not a migration datasource.
static Map<String, Object> tenantOnlySettings(HikariDataSource runtimePool) {
    Map<String, Object> settings = new HashMap<>();
    settings.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER,
            new TenantConnectionProvider(runtimePool));
    settings.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
            new TenantIdentifierResolver());
    return settings;
}
// Apply this map only to the future tenant factory, never the central factory.
```

This is a wiring fragment, not an installed bean or a complete factory definition.
A future factory must also explicitly separate entity/repository scans and
transaction managers, preserve Flyway ordering, verify that generated tenant SQL
uses unqualified tenant tables, and avoid a central or bootstrap default schema
being baked into tenant mappings. Bootstrap is catalog-only and cannot validate
unprovisioned tenant tables. The tenant factory's schema-validation strategy must
be designed against provisioned fixtures before activation; do not copy an unsafe
schema-management setting or fabricate a tenant to make bootstrap pass.

The context scope must outlive the whole tenant session/transaction. Hibernate's
current-session validation checks context identity when supported current sessions
are retrieved; it is not a per-statement authorization check. Do not reuse an
EntityManager/session across companies or treat resolver validation as proof of
membership. Async work must independently establish a validated context.

## PostgreSQL test fixtures: proposed human-only setup

Unit tests need no database. Actual integration tests use only `hich_erp_test` and
`hich_runtime_test`. They do not execute fixture DDL or use administrator/migrator
credentials. Their dedicated one-connection test pool reuses the configured runtime
credentials and is closed after each test; it is never a Spring datasource bean.
This makes same-physical-connection reuse and invalidation assertions deterministic.

Current restricted migrators have no database CREATE privilege. Five integration
test methods require test-only schemas that cannot be automatically provisioned
under current permissions. They fail explicitly if fixtures are absent; there are
no assumptions, disabled tests, skip conditions or privilege escalation. Bootstrap,
central-boundary and nonexistent-schema tests need no added tenant fixture.

A human DBA may review the following proposal, then connect to **hich_erp_test**
using the existing administrative identity and execute it with psql's fail-fast
setting. For example, in the existing administrative psql session:

```sql
\set ON_ERROR_STOP on
\connect hich_erp_test
BEGIN;
DO $task05_fixture_guard$
BEGIN
    IF current_database() <> 'hich_erp_test'
            OR current_user NOT IN ('hich_dev', 'postgres')
            OR NOT (SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Task 05 fixtures require the test database and its administrator';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace
        WHERE nspname IN (
            'tenant_00000000000000000000000000000501',
            'tenant_00000000000000000000000000000502',
            'tenant_00000000000000000000000000000503',
            'tenant_00000000000000000000000000000504'
        )
    ) THEN
        RAISE EXCEPTION 'Task 05 fixture names must be unused; inspect existing objects before proceeding';
    END IF;
END;
$task05_fixture_guard$;

CREATE SCHEMA tenant_00000000000000000000000000000501 AUTHORIZATION hich_migrator_test;
CREATE SCHEMA tenant_00000000000000000000000000000502 AUTHORIZATION hich_migrator_test;
CREATE SCHEMA tenant_00000000000000000000000000000503 AUTHORIZATION hich_migrator_test;
REVOKE ALL ON SCHEMA tenant_00000000000000000000000000000501,
    tenant_00000000000000000000000000000502,
    tenant_00000000000000000000000000000503
    FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test;
GRANT USAGE ON SCHEMA tenant_00000000000000000000000000000501,
    tenant_00000000000000000000000000000502 TO hich_runtime_test;

SET LOCAL ROLE hich_migrator_test;
CREATE TABLE tenant_00000000000000000000000000000501.routing_probe (
    marker text PRIMARY KEY
);
CREATE TABLE tenant_00000000000000000000000000000502.routing_probe (
    marker text PRIMARY KEY
);
INSERT INTO tenant_00000000000000000000000000000501.routing_probe VALUES ('task05-a');
INSERT INTO tenant_00000000000000000000000000000502.routing_probe VALUES ('task05-b');
REVOKE ALL ON TABLE tenant_00000000000000000000000000000501.routing_probe,
    tenant_00000000000000000000000000000502.routing_probe
    FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test;
GRANT SELECT ON TABLE tenant_00000000000000000000000000000501.routing_probe,
    tenant_00000000000000000000000000000502.routing_probe TO hich_runtime_test;
RESET ROLE;
COMMIT;
```

This proposal is not an applied migration or a change to existing permission
scripts. It creates only three named test schemas, two fixture tables and narrowly
scoped grants. It never grants database CREATE/TEMP to runtime or migration roles.
Schema 503 deliberately has no runtime USAGE; schema 504 must remain absent.
Review ACLs and role defaults before execution. Existing fixtures should be inspected
and reused after successful setup; the proposal intentionally refuses to overwrite
or silently adopt existing objects. No automatic drop/reset or cleanup DDL is run.

Fixture-dependent methods are: two-tenant reads, same-backend baseline restoration,
owned-transaction rollback/reset, transactional handoff eviction and inaccessible
schema rejection. Positive temporary-schema contamination tests are deferred because
the runtime has no TEMP privilege; unit tests exercise effective-path rejection.
The existing database privilege tests continue to verify TEMP denial.

## Verification and limits

There are 50 new test methods: 6 resolver, 36 provider unit and 8 PostgreSQL/Spring
integration tests. With 83 existing test methods, the expected total is 133. These
are source counts, not executed results. Mockito is used only for deterministic
JDBC/pool failure paths; existing tenant value/context tests remain unchanged.
The existing central startup, independent migrations, privilege and ArchUnit tests
are preserved. No Maven build, test suite, SQL setup or CI run was executed here.

After human review and setup of the restricted test database:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=TenantIdentifierResolverTest,TenantConnectionProviderTest test
mvn -o -B -ntp -Dtest=ArchitectureTest,ModuleArchitectureTest,TenantIdTest,TenantSchemaIdentifierTest,TenantContextTest,TenantContextHolderTest test
mvn -o -B -ntp -Dtest=TenantRoutingDatabaseIntegrationTest test
mvn -o -B -ntp clean verify
```

Current CI does not install these proposed fixtures; the fixture-dependent tests
will fail there until a human authorizes and supplies test-only setup. No CI checks
were bypassed or modified. Compilation, actual PostgreSQL behavior and CI acceptance
remain unverified and must not be reported as passing.

A shared runtime database role with access to multiple tenants does not provide
complete database-enforced isolation against compromised application code. Fully
qualified cross-tenant SQL can bypass search-path routing. There is no application-
wide tenant isolation claim. Session/request authorization, provisioning, an active
tenant persistence unit, per-statement context enforcement and advanced pooled
session-state hardening remain outside this foundation.

Task 06 must extend testing and hardening for other PostgreSQL session settings,
prepared statements, connection escape, role changes, manual SQL transaction
control, aborted transactions, pool concurrency and failure recovery. This task
already provides the fundamental verified-path reset and fail-closed invalidation;
it does not claim complete pooled-session isolation.
