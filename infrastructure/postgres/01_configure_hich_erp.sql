-- Fixed environment entry point. Use psql --file so its sibling include resolves.
\set ON_ERROR_STOP on
\set hich_database hich_erp
\set hich_migration_role hich_migrator_dev
\set hich_runtime_role hich_runtime_dev
\set hich_other_migration_role hich_migrator_test
\ir configure_application_database.sql
