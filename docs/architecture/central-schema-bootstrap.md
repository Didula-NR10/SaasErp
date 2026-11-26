# Central Schema V1 Bootstrap Compatibility

## Failure and compatibility decision

Both immutable V1 resources contain exactly one statement:

```sql
-- db/migration/platform/V1__create_platform_schema.sql
CREATE SCHEMA IF NOT EXISTS platform;
-- db/migration/billing/V1__create_billing_schema.sql
CREATE SCHEMA IF NOT EXISTS billing;
```

PostgreSQL checks database CREATE privilege before handling the existing-schema
case. IF NOT EXISTS does not bypass that authorization check. Thus a fresh Flyway
history causes SQLSTATE 42501 under the deliberately restricted migrator, even
though administrator scripts already created both schemas. A local database with
successful V1 histories does not execute them again and conceals this fresh-start
incompatibility. Dependent Spring context errors are secondary failures.

The earlier database-migrations document's assertion that these V1 statements are
safe no-ops under restricted credentials was incorrect. Neither V1 is edited.
Existing SQL checksums, original installer identities and applied histories remain
unchanged. Database ownership stays with `hich_dev`; migration accounts receive
neither database CREATE nor TEMP. Runtime credentials remain separate and restricted.

The selected transition is an explicit offline Flyway API baseline at version 1,
only after the exact administrator-provisioned V1 state has been verified. It is
not a SQL migration marked successful: fresh histories contain a distinguishable
BASELINE row with description `Administrator-provisioned central schema V1`, the
restricted migrator as installer and no SQL checksum. V2 and later migrations
remain pending until normal startup migrates and validates them. Later applied SQL
checksums continue to be validated by Flyway. The command alone uses the exact
validation ignore pattern `*:pending` because standalone validation precedes normal
`migrate()`. It does not ignore applied, missing, failed or checksum-invalid
migrations; normal application Flyway configuration is unchanged.

## Explicit command and guards

`com.saaserp.erp.config.CentralSchemaBootstrap` is a standalone Java entry point,
not a Spring bean, initializer, callback or startup fallback. It does not start
Spring, JPA, Redis or tenant routing. It accepts exactly:

```text
--baseline-central-v1 hich_erp
--baseline-central-v1 hich_erp_test
```

It reads only the selected database's existing HOST, PORT, NAME, MIGRATION_USER
and MIGRATION_PASSWORD environment variables (`DB_*` or `TEST_DB_*`). It requires
the matching restricted migrator and expected database name; it never accepts an
administrator login, arbitrary JDBC URL or alternate database fallback. Supported
hosts are simple ASCII DNS names/IPv4 addresses and ports 1–65535. Passwords are
passed separately from the URL and never printed by the command. No runtime or
administrator password is needed.

Run it only during a serialized, quiescent provisioning/bootstrap window. Its
preflight verifies both central streams before either baseline API call:

- Both packaged V1 files still contain only their reviewed schema-creation SQL.
- Flyway targets exactly one allowlisted schema/location per instance and uses
  baseline version 1, explicit description, `createSchemas(false)`,
  `baselineOnMigrate(false)`, `validateOnMigrate(true)` and `cleanDisabled(true)`.
- Both instances use the same explicitly selected datasource.
- The actual connection is an independent autocommit session for the expected
  migrator/database, with administrator-owned database and restricted role flags.
- Neither migrator nor matching runtime role has database CREATE or TEMP.
- Both schemas exist, belong to the expected migrator with schema USAGE/CREATE,
  grant runtime USAGE without CREATE, and have no PUBLIC schema grants or unapproved
  table default grants.
- Existing history relations must be ordinary migrator-owned tables inaccessible
  to runtime for table privileges. Other relation kinds are rejected.
- A fresh schema has no namespace-dependent objects except administrator-installed
  default ACL metadata. Functions, types, relations and other untracked objects
  therefore reject fresh baselining.

Only when **both** histories are absent and both schemas are pristine does the
command call the two independent `Flyway.baseline()` APIs. It then rechecks identity,
history ownership/access, recognized V1 markers and Flyway validation. It does not
run migrate, repair, clean, grants, role changes or schema DDL itself.

When both histories already exist, the command only recognizes successful SQL V1
with a checksum or the explicitly described V1 baseline, then calls Flyway validate.
A historical SQL installer may be the original administrator; it is not rewritten.
An existing empty history, unrecognized baseline, failed/checksum-invalid history,
one missing history or unexpected schema state is rejected for operator review.
The existing test fixture's separate baseline version 0 is unaffected.

Normal `FlywayConfig` is unchanged. Application startup never invokes this command
and never enables automatic baselining. Starting a fresh environment without the
explicit reviewed bootstrap step still fails closed.

## Exact proposed CI change — human review/apply only

No infrastructure SQL changes are needed. Existing scripts 00–04 already establish
the required roles, ownership, namespaces, restricted credentials and tenant fixtures.
They remain unchanged. Do not add database CREATE/TEMP grants or modify the V1 files.

Insert this step in `.github/workflows/backend-ci.yml` after **Install isolated
tenant routing test fixtures** and before **Build and run tests**. This is a proposal;
the agent has not changed the workflow or executed these commands:

```yaml
      - name: Explicitly baseline administrator-provisioned central V1 schemas
        run: |
          set -euo pipefail
          mvn -B -ntp spring-boot:run \
            -Dspring-boot.run.main-class=com.saaserp.erp.config.CentralSchemaBootstrap \
            "-Dspring-boot.run.arguments=--baseline-central-v1 hich_erp"
          mvn -B -ntp spring-boot:run \
            -Dspring-boot.run.main-class=com.saaserp.erp.config.CentralSchemaBootstrap \
            "-Dspring-boot.run.arguments=--baseline-central-v1 hich_erp_test"
```

The existing job-level variables and previously generated restricted passwords are
sufficient; do not attach `PGPASSWORD` or administrator credentials to this step.
The command selects dev/test credential prefixes independently. Both databases are
included because normal development startup and tests must have compatible histories.
The existing subsequent `mvn -B -ntp clean verify` remains unchanged.

The cached Boot 4.2.0 plugin supports both `spring-boot.run.main-class` and
`spring-boot.run.arguments`. Its loader tools prefer the single
`@SpringBootApplication` main class when packaging, so the existing `ErpApplication`
remains the packaged application entry point. This extra unannotated CLI does not
register another persistence context or datasource bean. No Maven dependency/plugin
or version changes are needed.

For a fresh local environment after the human administrator applies the existing
role/schema scripts, run the corresponding commands from `/workspace/Backend`,
using `mvn -o -B -ntp` when dependencies are cached. Existing databases with successful
platform and billing histories require no transition: ordinary verification/startup
continues to use those histories unchanged.

## Existing, failed and partial environments

Do not delete histories or run repair, baseline-on-migrate, database resets or
checksum updates to adopt this change. In particular, a failed migration run may
have left an empty `platform.flyway_schema_history`. Flyway 12.11.0 explicitly rejects
baselining an existing empty history; this tool rejects it before writing either
stream. Such a database requires separately reviewed operator handling. A fresh CI
job's newly initialized service can run the proposed bootstrap before its first
application migration attempt; do not treat the proposal as recovery for a reused
failed database.

The two schema histories are independent Flyway operations, not an atomic
cross-schema transaction. Preflight rejects known partial state before either
operation. An unexpected failure after the first baseline can still leave partial
initialization; stop and review it rather than automatically completing or erasing
history. Stop application/migration processes during bootstrap and do not run this
command concurrently with another provisioner. Malformed state or validation errors
on an already initialized database require review through the normal migration/
deployment process; this tool never repairs them automatically.

Baselined V1 has no SQL checksum by design. Its equivalence is guaranteed by the
explicit state/resource guards, not by pretending its SQL executed. The immutable
resource contract test pins V1 to the reviewed DDL. Do not broaden V1 or casually
change the baseline description; new database changes need new versioned migrations.

## Regression tests and verification

`CentralSchemaBootstrapTest` adds 20 unit tests covering both-stream preflight,
restricted configuration/environment selection, missing/wrong schema state, untracked
objects, partial/empty histories, preserved legacy SQL history and installer,
recognized baseline reuse, validation failure propagation and absence of repair,
clean or migrate calls, plus the pending-only validation policy. JDBC and Flyway are mocked; unit tests do not connect to a
database or create histories.

`DatabaseMigrationIntegrationTest` preserves its existing cases and adds two actual
PostgreSQL checks: central V1 is either validated original SQL or the controlled
baseline, and both migrator/runtime identities in both environments lack database
CREATE/TEMP while the databases retain administrator ownership. Its existing Flyway
configuration test now explicitly checks `createSchemas(false)` for both streams.
Existing independent histories, fixture initialization, privilege tests, tenant
routing/session isolation and architecture tests remain intact. A fresh CI run with
the approved explicit step exercises real baseline creation followed by application
migrations under restricted permissions; no test silently provisions schema privileges.

There are 22 added test methods, giving an expected source total of 185. No Maven
build, database migration, administrative SQL, automated test or CI run was executed
by the agent. Cached Flyway 12.11.0 and Boot 4.2.0 APIs/bytecode were inspected, and
static correctness review is distinct from executed verification.

After reviewing/applying the CI proposal or establishing local prerequisites, the
human developer should run:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=CentralSchemaBootstrapTest test
mvn -o -B -ntp -Dtest=DatabaseMigrationIntegrationTest,ErpApplicationTests test
mvn -o -B -ntp -Dtest=ArchitectureTest,ModuleArchitectureTest test
mvn -o -B -ntp clean verify
```

CI remains blocked on fresh databases until the human applies the proposed bootstrap
step. Existing CI references to a 133-test run are not evidence that the current
185-method source suite has run. No security checks should be disabled to compensate.
