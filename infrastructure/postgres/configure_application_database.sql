-- Included by the fixed development/test wrappers; never a Flyway migration.
\set ON_ERROR_STOP on

-- Presence tests expand to psql-generated booleans, never caller-supplied SQL.
SELECT :{?hich_database} AND :{?hich_migration_role}
    AND :{?hich_runtime_role} AND :{?hich_other_migration_role}
    AS hich_parameters_present \gset
\if :hich_parameters_present
\else
\echo 'Database configuration requires the fixed wrapper parameters'
\quit 3
\endif

BEGIN;

-- psql does not expand variables inside dollar-quoted DO bodies. Bridge only
-- safely quoted SQL literals into transaction-local settings for the guard.
SELECT pg_catalog.set_config('hich.bootstrap_database', :'hich_database', true),
       pg_catalog.set_config('hich.bootstrap_migration_role', :'hich_migration_role', true),
       pg_catalog.set_config('hich.bootstrap_runtime_role', :'hich_runtime_role', true),
       pg_catalog.set_config('hich.bootstrap_other_migration_role', :'hich_other_migration_role', true);

DO $guard$
DECLARE
    expected_database text := pg_catalog.current_setting('hich.bootstrap_database');
    migration_role text := pg_catalog.current_setting('hich.bootstrap_migration_role');
    runtime_role text := pg_catalog.current_setting('hich.bootstrap_runtime_role');
    other_migration_role text := pg_catalog.current_setting('hich.bootstrap_other_migration_role');
    migration_role_oid oid;
BEGIN
    -- Only the two reviewed environment tuples are accepted, including every role.
    IF NOT COALESCE((expected_database, migration_role, runtime_role, other_migration_role) IN (
        ('hich_erp', 'hich_migrator_dev', 'hich_runtime_dev', 'hich_migrator_test'),
        ('hich_erp_test', 'hich_migrator_test', 'hich_runtime_test', 'hich_migrator_dev')
    ), false) THEN
        RAISE EXCEPTION 'Invalid database configuration identity tuple';
    END IF;

    IF current_database() <> expected_database THEN
        RAISE EXCEPTION 'Expected database %, got %', expected_database, current_database();
    END IF;

    IF current_user NOT IN ('hich_dev', 'postgres')
            OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Database privilege setup requires the hich_dev or postgres superuser';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_database
        WHERE datname = expected_database AND pg_get_userbyid(datdba) = 'hich_dev'
    ) THEN
        RAISE EXCEPTION 'Database % must remain owned by hich_dev', expected_database;
    END IF;

    IF (
        SELECT count(*) FROM pg_roles
        WHERE rolname IN (
            'hich_migrator_dev', 'hich_migrator_test', 'hich_runtime',
            'hich_runtime_dev', 'hich_runtime_test', 'hich_provisioner'
        )
    ) <> 6 THEN
        RAISE EXCEPTION 'Run the cluster role setup before configuring %', expected_database;
    END IF;

    SELECT oid INTO migration_role_oid
    FROM pg_roles WHERE rolname = migration_role;
    IF migration_role_oid IS NULL THEN
        RAISE EXCEPTION 'Required migration role does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_namespace
        WHERE nspname = 'public'
          AND pg_get_userbyid(nspowner) = 'pg_database_owner'
    ) THEN
        RAISE EXCEPTION 'Unexpected public schema owner; review before changing privileges';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_namespace
        WHERE nspname IN ('platform', 'billing')
          AND pg_get_userbyid(nspowner) NOT IN ('hich_dev', migration_role)
    ) THEN
        RAISE EXCEPTION 'Unexpected owner of platform or billing schema';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class relation
        JOIN pg_namespace schema ON schema.oid = relation.relnamespace
        WHERE schema.nspname IN ('platform', 'billing')
          AND relation.relkind IN ('r', 'p', 'S', 'v', 'm', 'f')
          AND (
              (relation.relname = 'flyway_schema_history'
                  AND pg_get_userbyid(relation.relowner)
                      NOT IN ('hich_dev', migration_role))
              OR
              (relation.relname <> 'flyway_schema_history'
                  AND relation.relowner <> migration_role_oid)
          )
    ) THEN
        RAISE EXCEPTION
            'Unexpected owner of an object in platform or billing; review before changing ownership';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class relation
        JOIN pg_namespace schema ON schema.oid = relation.relnamespace
        WHERE schema.nspname = 'public'
          AND relation.relname = 'flyway_schema_history'
          AND pg_get_userbyid(relation.relowner)
              NOT IN ('hich_dev', migration_role, 'pg_database_owner')
    ) THEN
        RAISE EXCEPTION 'Unexpected owner of public.flyway_schema_history';
    END IF;
END;
$guard$;

REVOKE ALL PRIVILEGES ON DATABASE :"hich_database" FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE :"hich_database"
    FROM hich_migrator_dev, hich_migrator_test, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_provisioner;
GRANT CONNECT ON DATABASE :"hich_database" TO :"hich_migration_role", :"hich_runtime_role";

CREATE SCHEMA IF NOT EXISTS platform AUTHORIZATION :"hich_migration_role";
CREATE SCHEMA IF NOT EXISTS billing AUTHORIZATION :"hich_migration_role";
ALTER SCHEMA platform OWNER TO :"hich_migration_role";
ALTER SCHEMA billing OWNER TO :"hich_migration_role";

ALTER TABLE IF EXISTS platform.flyway_schema_history OWNER TO :"hich_migration_role";
ALTER TABLE IF EXISTS billing.flyway_schema_history OWNER TO :"hich_migration_role";

REVOKE ALL PRIVILEGES ON SCHEMA public
    FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test,
         hich_migrator_dev, hich_migrator_test, hich_provisioner;
REVOKE ALL PRIVILEGES ON SCHEMA platform, billing
    FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test,
         :"hich_other_migration_role", hich_provisioner;
GRANT USAGE ON SCHEMA platform, billing TO hich_runtime;

DO $history_privileges$
BEGIN
    IF to_regclass('platform.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE platform.flyway_schema_history FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test';
    END IF;
    IF to_regclass('billing.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE billing.flyway_schema_history FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test';
    END IF;
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test';
    END IF;
END;
$history_privileges$;

ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role"
    REVOKE ALL ON TABLES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role"
    REVOKE ALL ON SEQUENCES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role"
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role"
    REVOKE USAGE ON TYPES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA platform
    REVOKE ALL ON TABLES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA billing
    REVOKE ALL ON TABLES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA platform
    REVOKE ALL ON SEQUENCES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA billing
    REVOKE ALL ON SEQUENCES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA platform
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA billing
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA platform
    REVOKE USAGE ON TYPES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE :"hich_migration_role" IN SCHEMA billing
    REVOKE USAGE ON TYPES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, :"hich_other_migration_role", hich_provisioner;

COMMIT;

\unset hich_database
\unset hich_migration_role
\unset hich_runtime_role
\unset hich_other_migration_role
\unset hich_parameters_present
