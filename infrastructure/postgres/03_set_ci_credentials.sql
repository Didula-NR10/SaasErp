DO $guard$
BEGIN
    IF current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Run CI role password setup while connected to postgres';
    END IF;
END;
$guard$;

\getenv development_migration_password DB_MIGRATION_PASSWORD
\getenv test_migration_password TEST_DB_MIGRATION_PASSWORD
\getenv development_runtime_password DB_RUNTIME_PASSWORD
\getenv test_runtime_password TEST_DB_RUNTIME_PASSWORD

\if :{?development_migration_password}
\else
\echo 'DB_MIGRATION_PASSWORD must be set'
\quit 3
\endif
\if :{?test_migration_password}
\else
\echo 'TEST_DB_MIGRATION_PASSWORD must be set'
\quit 3
\endif
\if :{?development_runtime_password}
\else
\echo 'DB_RUNTIME_PASSWORD must be set'
\quit 3
\endif
\if :{?test_runtime_password}
\else
\echo 'TEST_DB_RUNTIME_PASSWORD must be set'
\quit 3
\endif

SELECT :'development_migration_password' ~ '[^[:space:]]'
    AS development_migration_password_valid \gset
SELECT :'test_migration_password' ~ '[^[:space:]]'
    AS test_migration_password_valid \gset
SELECT :'development_runtime_password' ~ '[^[:space:]]'
    AS development_runtime_password_valid \gset
SELECT :'test_runtime_password' ~ '[^[:space:]]'
    AS test_runtime_password_valid \gset

\if :development_migration_password_valid
\else
\echo 'DB_MIGRATION_PASSWORD must not be empty or whitespace-only'
\quit 3
\endif
\if :test_migration_password_valid
\else
\echo 'TEST_DB_MIGRATION_PASSWORD must not be empty or whitespace-only'
\quit 3
\endif
\if :development_runtime_password_valid
\else
\echo 'DB_RUNTIME_PASSWORD must not be empty or whitespace-only'
\quit 3
\endif
\if :test_runtime_password_valid
\else
\echo 'TEST_DB_RUNTIME_PASSWORD must not be empty or whitespace-only'
\quit 3
\endif

BEGIN;

ALTER ROLE hich_migrator_dev PASSWORD :'development_migration_password';
ALTER ROLE hich_migrator_test PASSWORD :'test_migration_password';
ALTER ROLE hich_runtime_dev PASSWORD :'development_runtime_password';
ALTER ROLE hich_runtime_test PASSWORD :'test_runtime_password';

COMMIT;

\unset development_migration_password
\unset test_migration_password
\unset development_runtime_password
\unset test_runtime_password
\unset development_migration_password_valid
\unset test_migration_password_valid
\unset development_runtime_password_valid
\unset test_runtime_password_valid
