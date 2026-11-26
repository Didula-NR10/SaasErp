BEGIN;

DO $task05_fixture_guard$
BEGIN
    IF current_database() <> 'hich_erp_test'
        OR current_user NOT IN ('hich_dev', 'postgres')
        OR NOT (
            SELECT rolsuper
            FROM pg_catalog.pg_roles
            WHERE rolname = current_user
        )
    THEN
        RAISE EXCEPTION
            'Task 05 fixtures require the test database and its administrator';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_namespace
        WHERE nspname IN (
            'tenant_00000000000000000000000000000501',
            'tenant_00000000000000000000000000000502',
            'tenant_00000000000000000000000000000503',
            'tenant_00000000000000000000000000000504'
        )
    ) THEN
        RAISE EXCEPTION
            'Task 05 fixture names must be unused; inspect existing objects before proceeding';
    END IF;
END;
$task05_fixture_guard$;

CREATE SCHEMA tenant_00000000000000000000000000000501
    AUTHORIZATION hich_migrator_test;

CREATE SCHEMA tenant_00000000000000000000000000000502
    AUTHORIZATION hich_migrator_test;

CREATE SCHEMA tenant_00000000000000000000000000000503
    AUTHORIZATION hich_migrator_test;

REVOKE ALL ON SCHEMA
    tenant_00000000000000000000000000000501,
    tenant_00000000000000000000000000000502,
    tenant_00000000000000000000000000000503
FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test;

GRANT USAGE ON SCHEMA
    tenant_00000000000000000000000000000501,
    tenant_00000000000000000000000000000502
TO hich_runtime_test;

SET LOCAL ROLE hich_migrator_test;

CREATE TABLE tenant_00000000000000000000000000000501.routing_probe (
    marker text PRIMARY KEY
);

CREATE TABLE tenant_00000000000000000000000000000502.routing_probe (
    marker text PRIMARY KEY
);

INSERT INTO tenant_00000000000000000000000000000501.routing_probe
VALUES ('task05-a');

INSERT INTO tenant_00000000000000000000000000000502.routing_probe
VALUES ('task05-b');

REVOKE ALL ON TABLE
    tenant_00000000000000000000000000000501.routing_probe,
    tenant_00000000000000000000000000000502.routing_probe
FROM PUBLIC, hich_runtime, hich_runtime_dev, hich_runtime_test;

GRANT SELECT ON TABLE
    tenant_00000000000000000000000000000501.routing_probe,
    tenant_00000000000000000000000000000502.routing_probe
TO hich_runtime_test;

RESET ROLE;

COMMIT;