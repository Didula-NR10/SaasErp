BEGIN;

DO $guard$
DECLARE
    migration_role_oid oid;
BEGIN
    IF current_database() <> 'hich_erp' THEN
        RAISE EXCEPTION 'Expected database hich_erp, got %', current_database();
    END IF;

    IF current_user NOT IN ('hich_dev', 'postgres')
            OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Database privilege setup requires the hich_dev or postgres superuser';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_database
        WHERE datname = 'hich_erp' AND pg_get_userbyid(datdba) = 'hich_dev'
    ) THEN
        RAISE EXCEPTION 'Database hich_erp must remain owned by hich_dev';
    END IF;

    IF (
        SELECT count(*) FROM pg_roles
        WHERE rolname IN (
            'hich_migrator_dev', 'hich_migrator_test', 'hich_runtime',
            'hich_runtime_dev', 'hich_runtime_test', 'hich_provisioner'
        )
    ) <> 6 THEN
        RAISE EXCEPTION 'Run the cluster role setup before configuring hich_erp';
    END IF;

    SELECT oid INTO migration_role_oid
    FROM pg_roles WHERE rolname = 'hich_migrator_dev';
    IF migration_role_oid IS NULL THEN
        RAISE EXCEPTION 'Required role hich_migrator_dev does not exist';
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
          AND pg_get_userbyid(nspowner) NOT IN ('hich_dev', 'hich_migrator_dev')
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
                      NOT IN ('hich_dev', 'hich_migrator_dev'))
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
              NOT IN ('hich_dev', 'hich_migrator_dev', 'pg_database_owner')
    ) THEN
        RAISE EXCEPTION 'Unexpected owner of public.flyway_schema_history';
    END IF;
END;
$guard$;

REVOKE ALL PRIVILEGES ON DATABASE hich_erp FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE hich_erp
    FROM hich_migrator_dev, hich_migrator_test, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_provisioner;
GRANT CONNECT ON DATABASE hich_erp TO hich_migrator_dev, hich_runtime_dev;

CREATE SCHEMA IF NOT EXISTS platform AUTHORIZATION hich_migrator_dev;
CREATE SCHEMA IF NOT EXISTS billing AUTHORIZATION hich_migrator_dev;
ALTER SCHEMA platform OWNER TO hich_migrator_dev;
ALTER SCHEMA billing OWNER TO hich_migrator_dev;

ALTER TABLE IF EXISTS platform.flyway_schema_history OWNER TO hich_migrator_dev;
ALTER TABLE IF EXISTS billing.flyway_schema_history OWNER TO hich_migrator_dev;

REVOKE ALL PRIVILEGES ON SCHEMA public
    FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test,
         hich_migrator_dev, hich_migrator_test, hich_provisioner;
REVOKE ALL PRIVILEGES ON SCHEMA platform, billing
    FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test,
         hich_migrator_test, hich_provisioner;
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

ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev
    REVOKE ALL ON TABLES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev
    REVOKE ALL ON SEQUENCES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev
    REVOKE USAGE ON TYPES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA platform
    REVOKE ALL ON TABLES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA billing
    REVOKE ALL ON TABLES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA platform
    REVOKE ALL ON SEQUENCES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA billing
    REVOKE ALL ON SEQUENCES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA platform
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA billing
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA platform
    REVOKE USAGE ON TYPES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;
ALTER DEFAULT PRIVILEGES FOR ROLE hich_migrator_dev IN SCHEMA billing
    REVOKE USAGE ON TYPES FROM PUBLIC, hich_runtime, hich_runtime_dev,
         hich_runtime_test, hich_migrator_test, hich_provisioner;

COMMIT;
