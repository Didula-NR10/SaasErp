CREATE TABLE platform.security_privilege_fixture (
    fixture_id UUID PRIMARY KEY,
    payload TEXT NOT NULL
);

CREATE TABLE platform.security_ungranted_fixture (
    fixture_id UUID PRIMARY KEY
);

CREATE FUNCTION platform.security_privilege_function()
RETURNS INTEGER
LANGUAGE SQL
AS $$ SELECT 1 $$;

CREATE TYPE platform.security_privilege_type AS ENUM ('allowed');

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE platform.security_privilege_fixture
    TO hich_runtime_test;
