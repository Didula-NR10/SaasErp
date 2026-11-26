BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Run this cluster-role script while connected to database postgres';
    END IF;

    IF current_user NOT IN ('hich_dev', 'postgres')
            OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Role setup requires the hich_dev or postgres superuser';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_migrator') THEN
        RAISE EXCEPTION
            'Legacy hich_migrator role exists; audit its memberships and ownership before proceeding';
    END IF;
END;
$guard$;

DO $roles$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_migrator_dev') THEN
        CREATE ROLE hich_migrator_dev LOGIN NOINHERIT NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_migrator_test') THEN
        CREATE ROLE hich_migrator_test LOGIN NOINHERIT NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_runtime') THEN
        CREATE ROLE hich_runtime NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_runtime_dev') THEN
        CREATE ROLE hich_runtime_dev LOGIN INHERIT NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_runtime_test') THEN
        CREATE ROLE hich_runtime_test LOGIN INHERIT NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hich_provisioner') THEN
        CREATE ROLE hich_provisioner NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
END;
$roles$;

ALTER ROLE hich_migrator_dev LOGIN NOINHERIT NOSUPERUSER NOCREATEDB
    NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE hich_migrator_test LOGIN NOINHERIT NOSUPERUSER NOCREATEDB
    NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE hich_runtime NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB
    NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE hich_runtime_dev LOGIN INHERIT NOSUPERUSER NOCREATEDB
    NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE hich_runtime_test LOGIN INHERIT NOSUPERUSER NOCREATEDB
    NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE hich_provisioner NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB
    NOCREATEROLE NOREPLICATION NOBYPASSRLS;

REVOKE hich_migrator_dev, hich_migrator_test, hich_provisioner
    FROM hich_runtime_dev, hich_runtime_test;

GRANT hich_runtime TO hich_runtime_dev, hich_runtime_test
    WITH INHERIT TRUE, SET FALSE, ADMIN FALSE;

DO $membership_guard$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_auth_members membership
        JOIN pg_roles granted_role ON granted_role.oid = membership.roleid
        JOIN pg_roles member_role ON member_role.oid = membership.member
        WHERE (
            member_role.rolname IN (
                'hich_migrator_dev', 'hich_migrator_test', 'hich_runtime',
                'hich_runtime_dev', 'hich_runtime_test', 'hich_provisioner'
            )
            OR granted_role.rolname IN (
                'hich_migrator_dev', 'hich_migrator_test', 'hich_runtime',
                'hich_runtime_dev', 'hich_runtime_test', 'hich_provisioner'
            )
        )
        AND NOT (
            granted_role.rolname = 'hich_runtime'
            AND member_role.rolname IN ('hich_runtime_dev', 'hich_runtime_test')
            AND membership.admin_option = false
            AND membership.inherit_option = true
            AND membership.set_option = false
        )
    ) THEN
        RAISE EXCEPTION
            'Unexpected role membership involving a restricted Hich role; review memberships before retrying';
    END IF;
END;
$membership_guard$;

REVOKE ALL PRIVILEGES ON DATABASE postgres FROM PUBLIC,
    hich_migrator_dev, hich_migrator_test, hich_runtime,
    hich_runtime_dev, hich_runtime_test, hich_provisioner;
REVOKE ALL PRIVILEGES ON DATABASE template1 FROM PUBLIC,
    hich_migrator_dev, hich_migrator_test, hich_runtime,
    hich_runtime_dev, hich_runtime_test, hich_provisioner;

DO $application_database_acl$
DECLARE
    application_database text;
BEGIN
    FOREACH application_database IN ARRAY ARRAY['hich_erp', 'hich_erp_test'] LOOP
        IF EXISTS (SELECT 1 FROM pg_database WHERE datname = application_database) THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON DATABASE %I FROM PUBLIC, '
                'hich_migrator_dev, hich_migrator_test, hich_runtime, '
                'hich_runtime_dev, hich_runtime_test, hich_provisioner',
                application_database
            );
        END IF;
    END LOOP;
END;
$application_database_acl$;

DO $database_guard$
DECLARE
    unexpected_access text;
BEGIN
    SELECT string_agg(
        format('%s (%s: %s)', database.datname, restricted_roles.role_name,
               privilege.privilege_name),
        ', ' ORDER BY database.datname, restricted_roles.role_name,
                     privilege.privilege_name
    )
    INTO unexpected_access
    FROM pg_database database
    CROSS JOIN (VALUES
        ('hich_migrator_dev'),
        ('hich_migrator_test'),
        ('hich_runtime_dev'),
        ('hich_runtime_test')
    ) AS restricted_roles(role_name)
    CROSS JOIN (VALUES ('CONNECT'), ('CREATE'), ('TEMP')) AS privilege(privilege_name)
    WHERE database.datallowconn
      AND database.datname NOT IN ('hich_erp', 'hich_erp_test')
      AND has_database_privilege(
          restricted_roles.role_name, database.datname::text,
          privilege.privilege_name);

    IF unexpected_access IS NOT NULL THEN
        RAISE EXCEPTION
            'Restricted application roles have access to non-application database privilege(s): %',
            unexpected_access;
    END IF;
END;
$database_guard$;

COMMIT;
