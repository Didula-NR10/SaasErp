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
were added, upgraded or downloaded. Task 06 promotes the existing PostgreSQL driver
to compile scope for direct server transaction-state inspection. This is API inspection, not compilation.

Hibernate's inherited read-only connection methods delegate to normal acquisition
and release. The provider declares `handlesConnectionReadOnly() == false`, leaving
in-use read-only state to Hibernate and restoring pool defaults at handoff, and `supportsAggressiveRelease() == false`.
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
explicitly configured session login, belongs to `hich_runtime`, has none of the superuser/role-creation/
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
not have TEMP privilege. The broader reset policy is specified in
[PostgreSQL Connection Pool Isolation](connection-pool-isolation.md).

There is no `public`, platform, billing, information-schema, administrator or
second-tenant entry in the effective tenant path. Nonexistent or inaccessible
schemas cannot silently disappear from the path and fall back to another company:
existence/privilege checks and effective-path verification must both succeed.

## Bootstrap and release lifecycle

`getAnyConnection()` is metadata access and never invents a customer identity.
It uses the restricted runtime pool and the verified catalog-only baseline
`pg_catalog, pg_temp`, with no temporary namespace. `releaseAnyConnection()` and
tenant releases have distinct ownership contracts. Identity-keyed lease tracking
rejects unknown, duplicate and mismatched releases.

Task 06 adds a full PostgreSQL session reset on both acquisition and release,
checks actual server transaction state in addition to JDBC autocommit, restores
application settings and verifies retained server resources. Unsafe connections
are physically evicted without a subsequent proxy close. Closed stale proxies
cannot evict newer pool leases. Uncertain ownership or failed eviction can stop
the pool as a last resort. No commit, rollback or setAutoCommit is performed by
the provider. See [PostgreSQL Connection Pool Isolation](connection-pool-isolation.md)
for the complete supported ownership contract, settings, driver-cache reasoning,
failure recovery and limitations.

Cleanup still works after a thread-local context closes. Central consumers sharing
the pool must use schema-qualified SQL. The context must outlive any active tenant
session, and callers must not bypass provider release through direct proxy close.

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

## PostgreSQL test fixtures

The established source of truth is
[04_create_test_tenant_fixtures.sql](../../infrastructure/postgres/04_create_test_tenant_fixtures.sql).
It creates test-only schemas 501 and 502 with markers `task05-a` and `task05-b`,
schema 503 without runtime USAGE, and deliberately leaves 504 absent. It grants
only the test runtime's required schema USAGE and table SELECT. Migrators still
have no database CREATE permission; application tests never create these fixtures
or use administrator credentials. Missing fixtures fail explicitly.

The fixture script and existing CI fixture invocation are unchanged by Task 06.
No additional fixture, grant or setup is required. Isolated test-specific pools
reuse the configured `hich_runtime_test` credentials only against `hich_erp_test`;
they are not Spring datasource beans. Their bounded sizes permit deterministic
physical-reuse, eviction and concurrent-tenant assertions.

## Verification and limits

The Task 06 source contains 53 provider unit tests, 6 resolver tests and 21 routing
integration tests. With the unchanged remaining tests, the expected total is 163.
This includes 30 additions to the task brief's 133-test verified baseline. No
Maven build, automated tests, SQL setup or CI verification was executed by the
agent. Static review and cached API inspection are not executed verification.

Use the commands in [PostgreSQL Connection Pool Isolation](connection-pool-isolation.md)
for unit, architecture, database integration and full Maven verification. Existing
central startup, independent Flyway migrations, database privileges and tenant
value/context tests remain intact. Tenant-specific Hibernate persistence-unit
activation is still deferred and has not been tested as an active application unit.

A shared runtime role does not enforce complete database isolation against
compromised application code. Fully qualified SQL can access another schema if
the role has privileges there. Routing is not authorization or membership proof.
The session-reset policy applies only within the documented connection ownership
contract; escaped handles, arbitrary vendor mutations and extension-defined
resources are not certified. No application-wide tenant isolation is claimed.
