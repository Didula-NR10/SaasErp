# Shared PostgreSQL Database Configuration

## Refactoring scope

SonarCloud reported 86 duplicated lines in each of
`01_configure_hich_erp.sql` and `02_configure_hich_erp_test.sql` (53.1% per file;
22.7% overall duplication on new code against a 3% gate). The original scripts
contained the same ownership/identity guard structure, database ACL reset, central
schema/history ownership changes, schema ACLs, history protection and default
privilege revocations. Only the environment database, matching migration/runtime
roles and opposite-environment migrator varied.

Both public entry points now contain fixed parameters and a relative psql include:
`\ir configure_application_database.sql`. The included file holds one readable
implementation of the original policy. This removes duplicated source logic; it
changes no SonarCloud setting, threshold, exclusion or Flyway migration. A fresh
analysis must establish the resulting duplication percentage and gate status.

The helper is an include-only administrator provisioning script, outside all Flyway
locations. Do not execute infrastructure SQL with an unrestricted glob: follow the
explicit entry points and order already documented in
[Database Migrations](database-migrations.md).

## Fixed environment contract

| Entry point | Only accepted connected database | Migration owner / CONNECT | Runtime CONNECT | Opposite migrator revoked |
| --- | --- | --- | --- | --- |
| `01_configure_hich_erp.sql` | `hich_erp` | `hich_migrator_dev` | `hich_runtime_dev` | `hich_migrator_test` |
| `02_configure_hich_erp_test.sql` | `hich_erp_test` | `hich_migrator_test` | `hich_runtime_test` | `hich_migrator_dev` |

The wrappers set all four parameters themselves, overwriting command-line values.
The helper accepts only these two complete tuples and verifies that
`current_database()` equals the selected database. It never reconnects or chooses
a database from a request, environment credential or unvalidated role name.
Even direct helper invocation cannot select another database/role combination.
Missing, empty, altered, cross-environment and null-equivalent tuples fail closed.

The execution identity policy is preserved: effective `current_user` must be the
`hich_dev` or `postgres` superuser. The database must already exist and remain owned
by `hich_dev`. Run through a fresh administrator psql session with `--no-psqlrc`
and fail-fast options; application credentials are not provisioning credentials.
No role passwords, login identities or group memberships are changed here; scripts
00 and 03 retain their separate existing responsibilities.

## Parameterization and transaction review

The helper enables `ON_ERROR_STOP` itself; wrappers also enable it so a missing
include fails immediately. A single read-only presence query uses psql's
`:{?variable}` boolean expansion and `\gset`, followed by `\if`. Missing parameters
exit with code 3 before starting the configuration transaction.

Inside the explicit `BEGIN`/`COMMIT`, values cross into PL/pgSQL through
`pg_catalog.set_config(..., :'variable', true)`. `:'variable'` is psql's SQL-literal
quoting, not string concatenation or bare SQL substitution. The `true` flag makes
these custom settings transaction-local. They restore their prior state at commit
or rollback and do not become role, database or persistent session defaults.
They contain only database/role names, never credentials.

psql does **not** interpolate variables inside dollar-quoted DO blocks. The guard
therefore uses `pg_catalog.current_setting(...)`, validates the entire tuple with
a null-safe allowlist check, and then runs every original identity and ownership
predicate before any privilege or ownership change. Subsequent SQL uses
`:"variable"` for SQL identifiers. All variable-bearing identifiers are validated
and quoted; the history-privilege DO block retains its original constant SQL.
There is no caller-controlled dynamic SQL, EXECUTE concatenation or interpolation
attempt inside a dollar-quoted block.

Errors abort execution. All privilege/ownership changes belong to the original
single database transaction and roll back together on failure. After successful
commit, the helper unsets its psql variables and computed presence flag to prevent
stale reuse. A failed batch should be discarded and investigated, not continued
interactively inside an aborted transaction. Cluster setup and the two databases
remain separate transactions, as before; this is not a cross-database transaction.

## Security equivalence review

Static expansion for each tuple was compared with the Git versions of both old
scripts. The complete SQL body after the guard matches the original corresponding
environment after accounting for quoted identifiers/whitespace. All original
ownership and execution-identity predicates also match after parameter expansion;
only diagnostic wording and the additional parameter allowlist differ.

| Original behavior | Preserved shared implementation |
| --- | --- |
| Database identity and administrator ownership | Exact connected database, allowlisted effective superuser and `hich_dev` database owner checks. |
| Required cluster roles | All six restricted/group/provisioner roles must exist; matching migrator OID is required. |
| Public schema owner | Must remain `pg_database_owner`; unexpected ownership rejects setup. |
| Central schema owners | Only administrator or that environment's migrator accepted before targeted ownership transfer. |
| Existing central object owners | Non-history relations must belong to the matching migrator; history relations may initially belong to administrator or matching migrator. Relation-kind checks are unchanged. |
| Legacy public history owner | Only administrator, matching migrator or `pg_database_owner` accepted; no history data moved or rewritten. |
| Database CONNECT / CREATE / TEMP | Revoke all from PUBLIC and all six named roles, then grant only CONNECT to the matching migrator/runtime logins. Neither CREATE nor TEMP is granted. |
| Platform/billing ownership | Create only if absent, assign matching migrator, and transfer existing central history table ownership only after guards. |
| Public schema restrictions | Revoke all schema privileges from PUBLIC and every named restricted/group/provisioner role. |
| Central schema access | Revoke from PUBLIC, runtime identities/group, opposite migrator and provisioner; grant only USAGE to the existing `hich_runtime` group. No runtime CREATE grant. |
| History confidentiality/integrity | Retain all three conditional runtime/PUBLIC revocations for platform, billing and public histories. |
| Global default privileges | Preserve TABLES, SEQUENCES, FUNCTIONS and TYPES revocations from PUBLIC, runtimes/group, opposite migrator and provisioner. Global function/type revocations are essential and remain global. |
| Schema-scoped default privileges | Preserve all four object categories separately for platform and billing; schema revokes do not replace global revokes. |
| Credential and tenant separation | No password/membership changes; the original opposite-environment migrator difference is retained in schema/default ACLs. Tenant fixture script 04 is unchanged. |

Existing `CentralSchemaBootstrap` and `DatabaseMigrationIntegrationTest` assumptions
remain valid. In particular, the schemas are already administrator-provisioned and
migrator-owned without database CREATE/TEMP. Explicit baseline compatibility still
follows [Central Schema Bootstrap](central-schema-bootstrap.md); this refactoring
never baselines, repairs, edits, inserts into or deletes a migration history.
Previously applied V1 SQL resources and checksums remain untouched.

## Invocation compatibility

CI already uses explicit `psql --file` paths from the checked-out repository. psql
resolves `\ir` relative to the wrapper's file directory, independent of the current
working directory. Both wrappers and the helper are present in the same checkout,
so `.github/workflows/backend-ci.yml` needs no change. Its credential, tenant-fixture,
central-baseline and Maven steps remain unchanged.

For local psql with access to the repository files, the same `--file` entry points
work. For Windows piping SQL into `docker exec ... psql`, neither the original
script path nor the helper file is transported by stdin. A stdin `\ir` instead
uses the psql process's working directory, which is not the host checkout. Copying
only the wrapper would also fail. Do not retain the old piping commands or concatenate
the source manually to conceal the include requirement.

The exact safe PowerShell replacement is in
[Credential Handling and Execution](database-migrations.md#credential-handling-and-execution):
copy the whole reviewed directory into an invocation-specific temporary container
directory, invoke each existing entry point with `--file`, check every exit status,
then remove only that temporary copy in finally. This needs no volume mount, Docker
Compose change, container recreation or installed dependency. Both wrappers require
the sibling helper to stay with them when copied or distributed.

## Human verification commands

No SQL, Maven, Docker mutation, database/permission change or Sonar analysis was
executed by the agent. The 238 existing automated tests and their sources are
unchanged. Static checking is not proof of SQL execution or a passing quality gate.

First review the source and run local regression checks using existing restricted
credentials and already configured services; an existing local database does not
need provisioning scripts rerun merely to adopt this source refactoring:

```bash
cd /workspace
git diff --check
cd Backend
mvn -o -B -ntp -Dtest=DatabaseMigrationIntegrationTest,CentralSchemaBootstrapTest test
mvn -o -B -ntp -Dtest=TenantConnectionProviderTest,TenantRoutingDatabaseIntegrationTest,CrossTenantSecurityIntegrationTest test
mvn -o -B -ntp clean verify
```

Fresh Backend CI is required to exercise actual initialization from the shared
script with the existing role setup, restricted credentials, tenant fixtures and
explicit central baseline. The human should rerun Backend CI and the PR SonarCloud
analysis, inspect both results and confirm the actual new-code duplication metric
meets the existing 3% gate. No local Sonar command/configuration is invented here.

For a reviewed fresh/disposable instance, the positive entry commands from the
repository root are unchanged (use existing administrator authentication, or psql's
interactive password prompt; never put passwords in command arguments):

```bash
psql -X -W -h localhost -U hich_dev -d hich_erp -v ON_ERROR_STOP=1 --file infrastructure/postgres/01_configure_hich_erp.sql
psql -X -W -h localhost -U hich_dev -d hich_erp_test -v ON_ERROR_STOP=1 --file infrastructure/postgres/02_configure_hich_erp_test.sql
```

These commands configure privileges and are human-only actions after review. The
existing cluster roles/databases must already exist. Inside a Dev Container, use
the existing PostgreSQL service host instead of `localhost` when appropriate, or
use the exact container-local PowerShell procedure. Do not run the tenant fixture
creation script again against its existing fixed names.

Negative guard checks below are also human-only and should exit nonzero without
committing privilege/ownership changes. Use a fresh psql process for each command
and explicitly check its status; no `--single-transaction` wrapper is needed:

```bash
# Development wrapper on testing database: expected connected-database rejection.
psql -X -W -h localhost -U hich_dev -d hich_erp_test -v ON_ERROR_STOP=1 --file infrastructure/postgres/01_configure_hich_erp.sql
# Testing wrapper on development database: expected connected-database rejection.
psql -X -W -h localhost -U hich_dev -d hich_erp -v ON_ERROR_STOP=1 --file infrastructure/postgres/02_configure_hich_erp_test.sql
# Include-only helper without parameters: expected missing-parameter exit 3.
psql -X -W -h localhost -U hich_dev -d hich_erp_test -v ON_ERROR_STOP=1 --file infrastructure/postgres/configure_application_database.sql
# Mismatched role tuple: expected allowlist rejection, even under an administrator.
psql -X -W -h localhost -U hich_dev -d hich_erp_test -v ON_ERROR_STOP=1 \
  -v hich_database=hich_erp_test -v hich_migration_role=hich_migrator_dev \
  -v hich_runtime_role=hich_runtime_test -v hich_other_migration_role=hich_migrator_dev \
  --file infrastructure/postgres/configure_application_database.sql
# Runtime identity on its own database: expected execution-identity rejection.
psql -X -W -h localhost -U hich_runtime_test -d hich_erp_test -v ON_ERROR_STOP=1 --file infrastructure/postgres/02_configure_hich_erp_test.sql
```

For every expected failure, verify a nonzero status and the stated guard diagnostic
rather than assuming an authentication/transport failure proves the guard ran.
After positive initialization, the existing catalog/permission/history regression
tests verify effective restrictions under restricted accounts; they do not require
administrator credentials during application execution.

Remaining risks are operational: keeping all included files together, running a
compatible psql client (the existing PostgreSQL 17 tooling supports these commands),
administrator concurrency/ownership changes during setup, and the pre-existing
cross-script transaction boundary. Run provisioning in a quiescent reviewed window.
Source equivalence does not replace fresh-database execution or SonarCloud analysis.
