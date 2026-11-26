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

Integration tests require the separate `hich_erp_test` database and the
`TEST_DB_HOST`, `TEST_DB_PORT`, `TEST_DB_NAME`, `TEST_DB_RUNTIME_USER`,
`TEST_DB_RUNTIME_PASSWORD`, `TEST_DB_MIGRATION_USER`, and
`TEST_DB_MIGRATION_PASSWORD` variables. The development runtime and migration
identities are supplied separately for cross-database denial checks. Test
configuration pins the application and test fixture URLs to `hich_erp_test`;
the development values are used only for read-only identity/privilege checks
and attempted denied connections. Missing, empty, or whitespace-only
credentials fail rather than falling back to an administrator or another
environment.

The test profile verifies its effective PostgreSQL username and database,
uses the restricted test runtime identity for application queries, and uses
the migration identity only for Flyway. Negative permission checks run inside
transactions that are rolled back. A test-only Flyway migration creates a
fixture owned by the migration role and grants the test runtime only the
fixture DML rights needed by the positive test. The fixture and its separate
history remain in `hich_erp_test`; tests never clean or drop them.

The fixture uses its own `platform.privilege_test_schema_history` table,
separate from the central platform history. If the fixture history is absent,
the test explicitly baselines only this test-only Flyway instance at version
`0` with `baselineOnMigrate=false`, then applies fixture version `1`. It uses
`hich_migrator_test` against `hich_erp_test`, verifies the baseline, migration
and ownership, and reuses the history on later test runs. This baseline does
not modify either central migration history or enable baseline-on-migrate in
application configuration.

The tests attempt all four prohibited cross-database connections: test
runtime and test migrator to `hich_erp`, and development runtime and
development migrator to `hich_erp_test`. The security fixture is executed
only in `hich_erp_test`; development-database checks are read-only. Tests
assert PostgreSQL permission SQLSTATEs and inspect the effective session
identity. A dedicated disposable test database is sufficient; no additional
test-container dependency or Docker image rebuild is required. Until an
administrator completes the role setup and all four restricted credentials
are configured, PostgreSQL integration tests are blocked and must not be
reported as passing.

## Database Privilege Separation

The version-controlled scripts in `infrastructure/postgres/` are proposals
for an administrator to review and execute; application startup does not
apply them. They target the existing `hich_erp` and `hich_erp_test`
databases, require their current owner to remain `hich_dev`, and stop before
changes if the target database, schema, public-schema owner, role membership,
or central object ownership differs from the documented assumptions. The
cluster-wide role script must be run while connected to `postgres`; the other
scripts must be run against the exact named database. PostgreSQL roles are
cluster-wide, while database ACLs, schema/object ownership, and default
privileges are database-specific.

| Identity | Login | Intended authority |
| --- | --- | --- |
| `hich_dev` | Existing administrator | Retains current elevated development administration; never used by the backend. |
| `hich_migrator_dev` | Yes | Development migration identity. Connects only to `hich_erp`, owns its `platform` and `billing` schemas and histories, and executes central migrations. `NOINHERIT`; no database-level `CREATE` or `TEMP`. |
| `hich_migrator_test` | Yes | Test migration identity. Connects only to `hich_erp_test`, owns its central schemas and histories, and executes test migrations. `NOINHERIT`; no database-level `CREATE` or `TEMP`. |
| `hich_runtime` | No; group role | Receives schema `USAGE` and only explicitly granted application table/sequence privileges. No database `CONNECT`, schema/table creation, DDL, or Flyway history access. |
| `hich_runtime_dev` | Yes | Member of `hich_runtime`; connects only to `hich_erp`. |
| `hich_runtime_test` | Yes | Member of `hich_runtime`; connects only to `hich_erp_test`. |
| `hich_provisioner` | No | Reserved for future provisioning design; currently has no memberships, login, or database/schema/table privileges. `NOINHERIT`. |

Every non-administrative role is explicitly constrained to
`NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS`. The
migrators and non-login group roles are `NOINHERIT`. Runtime logins inherit
only `hich_runtime` privileges; the membership has `SET FALSE` and
`ADMIN FALSE`, so a runtime session cannot switch to or administer the group.
The scripts reject unexpected memberships involving any of these identities
instead of silently retaining a path to an elevated role.

The database scripts:

- Revoke all database privileges inherited from `PUBLIC` and all six
  restricted identities on `postgres` and `template1`. In each application
  database, revoke `CONNECT`, `CREATE`, and `TEMP` from `PUBLIC`, then grant
  `CONNECT` directly only to that database's migrator and runtime login.
  The cluster script first removes existing application-database grants for
  the restricted identities; the target-specific scripts then grant only
  the matching identities. Fail if a restricted identity has `CONNECT`,
  `CREATE`, or `TEMP` on any other connectable database.
- Remove runtime schema privileges from `public`; revoke `PUBLIC` privileges
  from `platform` and `billing`; grant the runtime group only schema `USAGE`.
- Transfer only the central `platform` and `billing` schemas and their
  `flyway_schema_history` tables to that database's migrator. Existing
  `public.flyway_schema_history` is preserved and made inaccessible to
  `PUBLIC` and runtime identities; it is not moved, dropped, or rewritten.
- Revoke default `PUBLIC`, runtime, opposite-environment migrator, and
  provisioner grants for future migration-created tables, sequences,
  functions, and types. PostgreSQL default privileges are
  database-specific. Global `ALTER DEFAULT PRIVILEGES` revokes are required
  for default `PUBLIC EXECUTE` on functions and `PUBLIC USAGE` on types:
  schema-scoped revokes cannot undo globally granted defaults. Schema-scoped
  revokes are also configured. Runtime access to future tables or sequences
  is never granted automatically; business migrations must grant only the
  specific DML and sequence privileges the application needs.
- Leave `hich_provisioner` inactive. Tenant provisioning privileges are not
  implemented in this task.

The setup uses targeted ownership changes; it does not use `REASSIGN OWNED`,
drop schemas/tables, or change administrator passwords. Existing non-history
objects in central schemas are accepted only when owned by that database's
migrator; unexpected ownership is a fail-fast condition for manual review.

`hich_dev` is a superuser with elevated cluster privileges in the current
development environment. It is not suitable as a production runtime identity.
No database roles or grants have been changed by application startup.

The immutable V1 migrations contain `CREATE SCHEMA IF NOT EXISTS`. PostgreSQL
checks database CREATE permission even when these schemas already exist, so
restricted migrators cannot execute V1 in a fresh history. The earlier assumption
that these statements were safe no-ops under restricted permissions was incorrect.
Administrator scripts still create and transfer both schemas without granting
database CREATE/TEMP to migrators or runtime accounts. Fresh environments require
the explicit, verified version-1 bootstrap described in
[Central Schema V1 Bootstrap Compatibility](central-schema-bootstrap.md).
Existing SQL V1 histories/checksums are preserved. `FlywayConfig` continues to use
`baselineOnMigrate(false)` and `createSchemas(false)`; normal startup does not
baseline or repair any history. Missing or unexpected prerequisite state fails
closed instead of granting schema-creation authority.

`postgres` and `template1` are outside the application's access boundary.
The cluster script removes `PUBLIC` and restricted-role database privileges
there and verifies no restricted identity retains `CONNECT`, `CREATE`, or
`TEMP` on any non-application database. Revoking `PUBLIC` access can affect
other non-superuser maintenance, monitoring, or support identities; review
the current database ACLs first and grant such tools only the explicit access
they require. The scripts do not change other identities' passwords or
memberships.

### Credential Handling and Execution

The backend receives only `DB_RUNTIME_*`, `DB_MIGRATION_*`, and test-specific
restricted credentials. The Compose workspace service does not receive
`POSTGRES_USER` or `POSTGRES_PASSWORD`. Configure the real, untracked `.env`
manually; do not overwrite it with `.env.example`. Set the runtime and
migration passwords to the values established by the administrator. The
sample file contains placeholders only.

From Windows PowerShell at the repository root, an administrator can review
and apply the scripts to the existing PostgreSQL container without recreating it
or changing volumes. The fixed development/test wrappers now include
`configure_application_database.sql` using psql's `\ir`. **Do not pipe the wrappers
with `Get-Content ... | docker exec ... psql`:** stdin does not carry the wrapper's
file location or its sibling include into the container.

Copy the reviewed directory into a unique container-local temporary directory and
use `--file`, preserving relative includes. These commands require an interactive
terminal; psql can prompt for the existing administrator password if needed. No
password is placed in a command argument or printed:

```powershell
$postgresContainer = @(docker compose ps -q postgres)
if ($LASTEXITCODE -ne 0 -or $postgresContainer.Count -ne 1 -or [string]::IsNullOrWhiteSpace($postgresContainer[0])) {
    throw 'Expected exactly one running PostgreSQL service; inspect the existing container first'
}
$postgresContainer = $postgresContainer[0].Trim()
$bootstrapDirectory = '/tmp/hich-bootstrap-' + [guid]::NewGuid().ToString('N')
docker exec $postgresContainer mkdir -m 700 -- $bootstrapDirectory
if ($LASTEXITCODE -ne 0) { throw 'Could not create the temporary SQL directory' }
try {
    # Copying the directory creates its postgres child inside the existing destination.
    docker cp .\infrastructure\postgres "${postgresContainer}:$bootstrapDirectory/"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the reviewed PostgreSQL scripts' }

    docker exec -it $postgresContainer psql -X -U hich_dev -d postgres -v ON_ERROR_STOP=1 --file "$bootstrapDirectory/postgres/00_create_roles.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Cluster role setup failed; stop and review' }
    docker exec -it $postgresContainer psql -X -U hich_dev -d hich_erp -v ON_ERROR_STOP=1 --file "$bootstrapDirectory/postgres/01_configure_hich_erp.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Development database setup failed; stop and review' }
    docker exec -it $postgresContainer psql -X -U hich_dev -d hich_erp_test -v ON_ERROR_STOP=1 --file "$bootstrapDirectory/postgres/02_configure_hich_erp_test.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Test database setup failed; stop and review' }
} finally {
    # Remove only this invocation's randomly named directory of copied SQL files.
    docker exec $postgresContainer rm -rf -- $bootstrapDirectory
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Temporary SQL directory cleanup failed; review the path manually' }
}
```

The databases must already exist and remain administrator-owned. This replaces
only the invocation method, not the setup order or review requirements. Do not
rerun setup merely because source was refactored. Successful cluster setup and
each database setup are separate transactions; stop and inspect any failure.
The existing CI `psql --file ../infrastructure/postgres/01_...sql` and `02_...sql`
commands remain valid because the checkout contains the sibling shared file.
See [Shared Database Configuration](database-configuration-scripts.md) for the
parameter/security review and human verification cases.

These scripts are proposals and are not executed by this task. Review the
current role memberships, database grants, and ownership assumptions first.
The cluster script intentionally stops if the legacy `hich_migrator` role
still exists; audit that role and its owned objects before any manual cleanup.
The two database scripts create/transfer central schemas and histories using
targeted ownership changes. Do not grant the migrators database-level
`CREATE` merely to bypass the schema bootstrap.

Set four independent restricted passwords interactively so they are not
placed in shell history or command arguments:

```powershell
docker exec -it saas2-postgres-1 psql -U hich_dev -d postgres
```

At the `psql` prompt, set unique passwords using
`\password hich_migrator_dev`, `\password hich_migrator_test`,
`\password hich_runtime_dev`, and `\password hich_runtime_test`, then exit
with `\q`. Enter the matching values manually into the untracked `.env`
variables shown in `.env.example`. The development and test runtime
credentials and migration credentials must all be distinct. Never place
passwords in SQL arguments, shell history, source control, or backend logs.
The CI-only `03_set_ci_credentials.sql` script instead reads four temporary
passwords from environment variables using psql's quoted variable
substitution; it is not the local administrator password workflow.

The scripts revoke database privileges inherited from `PUBLIC`; this does
not revoke table/schema access from unrelated users unless those users
received access through `PUBLIC`. Carefully review the effect on maintenance
tools before running them. No script is run automatically by the application
or this change.

The existing workspace container must be recreated once to receive changed
Compose environment values. This does not require restarting PostgreSQL or
Redis and does not recreate volumes:

```powershell
docker compose up -d --no-deps --force-recreate workspace
```

From the Dev Container, after the administrator has completed setup and the
restricted credentials are configured, run:

```bash
cd /workspace/Backend
mvn -o -B -ntp clean verify
```

The backend intentionally fails startup if required runtime or migration
credentials are missing. It never falls back to `hich_dev`.

### CI and Production

Backend CI provisions fresh PostgreSQL 17 databases, applies the same
role/database scripts, and generates four independent throwaway runtime and
migration passwords. It applies the role script to `postgres`, creates the
development database as the CI administrator, and runs each database
configuration script with an explicit target and psql fail-fast options.
Configure the GitHub Actions secret
`CI_POSTGRES_ADMIN_PASSWORD` with a CI-only value for the temporary database
administrator; do not use production credentials. Test execution uses the
restricted identities, not the setup administrator. Gitleaks, Trivy,
ArchUnit, and existing Maven quality gates remain enabled.

Development startup migrations and production deployment migrations are
different operational approaches. The current development contract runs
custom Flyway migrations at application startup using its separate
development migration credentials. Production should ultimately run
migrations as a separate, short-lived deployment operation using a dedicated
deployment identity; migration credentials must not be present in the
long-running runtime process. Tenant data isolation is not complete: tenant
context, schema routing, and tenant provisioning are not implemented here.
