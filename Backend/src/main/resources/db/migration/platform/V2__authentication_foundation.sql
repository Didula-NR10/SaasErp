-- Run as the existing restricted platform migrator, never the application role.
CREATE TABLE platform.accounts (
    id uuid PRIMARY KEY,
    normalized_email varchar(254) COLLATE "C" NOT NULL UNIQUE,
    password_hash varchar(512) NOT NULL CHECK (password_hash LIKE '$argon2id$%'),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    email_verified boolean NOT NULL DEFAULT false,
    mfa_required boolean NOT NULL DEFAULT false,
    privileged boolean NOT NULL DEFAULT false,
    security_version bigint NOT NULL DEFAULT 1 CHECK (security_version > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (normalized_email = lower(btrim(normalized_email)) AND length(normalized_email) > 3)
);
-- Explicit authentication contracts for future company/billing/membership application services.
-- These records do not provision tenant resources and grant no tenant SQL privileges.
CREATE TABLE platform.auth_companies (
    id uuid PRIMARY KEY,
    hostname varchar(253) COLLATE "C" NOT NULL UNIQUE
        CHECK (hostname = lower(hostname) AND hostname ~ '^[a-z0-9]+([.-][a-z0-9]+)*$'),
    status varchar(16) NOT NULL DEFAULT 'SUSPENDED' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    entitled boolean NOT NULL DEFAULT false,
    mfa_required boolean NOT NULL DEFAULT true,
    security_version bigint NOT NULL DEFAULT 1 CHECK (security_version > 0)
);
CREATE TABLE platform.company_memberships (
    account_id uuid NOT NULL REFERENCES platform.accounts(id),
    company_id uuid NOT NULL REFERENCES platform.auth_companies(id),
    active boolean NOT NULL DEFAULT false,
    mfa_required boolean NOT NULL DEFAULT true,
    privileged boolean NOT NULL DEFAULT false,
    security_version bigint NOT NULL DEFAULT 1 CHECK (security_version > 0),
    PRIMARY KEY (account_id, company_id)
);
CREATE TABLE platform.authentication_sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES platform.accounts(id),
    company_id uuid NOT NULL REFERENCES platform.auth_companies(id),
    hostname varchar(253) NOT NULL,
    account_version bigint NOT NULL,
    membership_version bigint NOT NULL,
    company_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz,
    FOREIGN KEY (account_id, company_id) REFERENCES platform.company_memberships(account_id, company_id)
);
CREATE INDEX authentication_sessions_account_idx ON platform.authentication_sessions(account_id);
REVOKE ALL ON platform.accounts, platform.auth_companies, platform.company_memberships,
    platform.authentication_sessions FROM PUBLIC;
GRANT SELECT ON platform.accounts, platform.auth_companies, platform.company_memberships,
    platform.authentication_sessions TO hich_runtime;
GRANT INSERT (id, normalized_email, password_hash) ON platform.accounts TO hich_runtime;
GRANT UPDATE (password_hash) ON platform.accounts TO hich_runtime;
GRANT INSERT (id, account_id, company_id, hostname, account_version, membership_version, company_version)
    ON platform.authentication_sessions TO hich_runtime;
GRANT UPDATE (revoked_at) ON platform.authentication_sessions TO hich_runtime;

-- Every security-policy change fences existing sessions, including deactivate/reactivate cycles.
CREATE FUNCTION platform.bump_auth_security_version() RETURNS trigger LANGUAGE plpgsql
    SET search_path = pg_catalog AS $$
BEGIN
    NEW.security_version := OLD.security_version + 1;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION platform.bump_auth_security_version() FROM PUBLIC;
CREATE TRIGGER account_security_changed BEFORE UPDATE OF status, normalized_email, email_verified, mfa_required, privileged
    ON platform.accounts FOR EACH ROW EXECUTE FUNCTION platform.bump_auth_security_version();
CREATE TRIGGER company_security_changed BEFORE UPDATE OF hostname, status, entitled, mfa_required
    ON platform.auth_companies FOR EACH ROW EXECUTE FUNCTION platform.bump_auth_security_version();
CREATE TRIGGER membership_security_changed BEFORE UPDATE OF active, mfa_required, privileged
    ON platform.company_memberships FOR EACH ROW EXECUTE FUNCTION platform.bump_auth_security_version();
