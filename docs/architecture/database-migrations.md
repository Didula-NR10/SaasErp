# Database Migrations

## Central Schemas

Application startup runs two independent Flyway instances:

- `platform` reads `db/migration/platform` and stores its history in
  `platform.flyway_schema_history`.
- `billing` reads `db/migration/billing` and stores its history in
  `billing.flyway_schema_history`.

The platform migration initializer completes first. The billing initializer
declares a dependency on it and completes second. Spring Boot makes the JPA
entity manager factory depend on both Flyway migration initializers, so
Hibernate starts only after both migration streams succeed. A migration error
fails application startup.

Spring Boot's single-Flyway auto-configuration is disabled because its default
initializer uses the global migration location. In Boot 4.2.0 that
auto-configuration also imports
`DatabaseInitializationDependencyConfigurer`; `FlywayConfig` imports the
dependency configurer directly so JPA ordering is retained while the default
Flyway initializer is absent.

Flyway validation is enabled. Baseline-on-migrate and clean are disabled.
The existing `public.flyway_schema_history` is not a target of either
instance and must be preserved; do not move, rename, or baseline it as part of
this configuration.

Tenant migration directories are deliberately excluded from central startup.
When tenant provisioning is implemented, a tenant migration run must select
`tenant/core` and exactly one applicable industry directory for that tenant,
using one Flyway history table in the tenant schema.

## Versioning

Use immutable, monotonically increasing Flyway versions. Allocate each tenant
migration version from one shared sequence across `tenant/core` and all
industry directories; never maintain independent counters by directory,
reuse a version, or edit a migration that has been applied. This gives every
core/industry combination a unique ordered sequence. A migration deployment
must run every pending migration from the selected locations before the
tenant is considered current. Gaps caused only by migrations in an industry
directory that is not selected for that tenant are expected, not skipped
applicable migrations.

Allocate tenant versions in deployment order. Before merging a migration,
check its version against every migration in the core and industry directories
and check the relevant core-plus-industry location combinations for conflicts.
Do not assume branches or industry directories can allocate versions
independently.

Platform and billing are independent migration streams, each with its own
history table, so their version numbers are independent of each other and of
the future tenant stream.

## Integration Test Database

Integration tests use `hich_erp_test` by default, rather than inheriting the
application's development database name. `TEST_DB_HOST`, `TEST_DB_PORT`,
`TEST_DB_NAME`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` can override the test
connection; host, port, user, and password otherwise reuse the existing
`DB_*` environment settings. The test database must be created once on the
existing PostgreSQL service. The CI workflow already provides
`hich_erp_test`; locally, provision that separate database using an authorized
database administrator on the existing service. No new Maven dependencies or
Docker image rebuild are required. Tests do not drop databases, schemas, or
tables, and should not be pointed at the development database.

## Database Roles

Production must separate database responsibilities and use least privilege:

- **Database administrator:** administers the database and roles; not used
  by the application.
- **Migration role:** owns or has DDL privileges on the schemas it migrates
  and can update their Flyway history tables. It is not the application role.
- **Provisioning role:** can create tenant schemas and grant the required
  ownership/privileges for provisioning; it does not need broad access to
  application data.
- **Application runtime role:** has only `CONNECT`, required schema `USAGE`,
  and the specific table/sequence DML privileges required at runtime. It
  cannot create schemas or perform DDL.

The current development datasource reuses the Compose `POSTGRES_USER`, which
is the PostgreSQL cluster superuser created by the official PostgreSQL image.
This elevated development identity is not suitable as a production runtime
identity. This task does not change database users, passwords, or grants.
