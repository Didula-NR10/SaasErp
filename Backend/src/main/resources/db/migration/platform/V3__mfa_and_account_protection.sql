-- New immutable migration, executed only by the existing restricted platform migrator.
ALTER TABLE platform.company_memberships ADD COLUMN auth_role varchar(24) NOT NULL DEFAULT 'EMPLOYEE'
    CHECK (auth_role IN ('PLATFORM_ADMIN','OWNER','ADMIN','MANAGER','EMPLOYEE'));
ALTER TABLE platform.company_memberships ADD COLUMN totp_allowed boolean NOT NULL DEFAULT false;
ALTER TABLE platform.company_memberships ADD COLUMN mfa_enrollment_until timestamptz;
ALTER TABLE platform.company_memberships ADD COLUMN mfa_enrolled boolean NOT NULL DEFAULT false;
ALTER TABLE platform.auth_companies ADD COLUMN totp_allowed boolean NOT NULL DEFAULT false;
ALTER TABLE platform.authentication_sessions ADD COLUMN assurance varchar(16) NOT NULL DEFAULT 'PASSWORD'
    CHECK (assurance IN ('PASSWORD','WEBAUTHN','TOTP'));
ALTER TABLE platform.authentication_sessions ADD COLUMN privileged_session boolean NOT NULL DEFAULT false;
CREATE TRIGGER membership_factor_policy_changed BEFORE UPDATE OF auth_role, totp_allowed, mfa_enrollment_until, mfa_enrolled
    ON platform.company_memberships FOR EACH ROW EXECUTE FUNCTION platform.bump_auth_security_version();
CREATE TRIGGER company_factor_policy_changed BEFORE UPDATE OF totp_allowed
    ON platform.auth_companies FOR EACH ROW EXECUTE FUNCTION platform.bump_auth_security_version();

CREATE TABLE platform.mfa_transactions (
    id uuid PRIMARY KEY, account_id uuid NOT NULL, company_id uuid NOT NULL,
    hostname varchar(253) NOT NULL, binding_hash char(64) NOT NULL,
    account_version bigint NOT NULL, membership_version bigint NOT NULL, company_version bigint NOT NULL,
    initial_enrollment boolean NOT NULL, recovery_only boolean NOT NULL DEFAULT false,
    verified_method varchar(16) CHECK (verified_method IN ('WEBAUTHN','TOTP')),
    verified_at timestamptz, completed boolean NOT NULL DEFAULT false,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 10),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '5 minutes',
    FOREIGN KEY (account_id,company_id) REFERENCES platform.company_memberships(account_id,company_id)
);
ALTER TABLE platform.authentication_sessions ADD COLUMN mfa_transaction_id uuid UNIQUE REFERENCES platform.mfa_transactions(id);
CREATE TABLE platform.webauthn_credentials (
    company_id uuid NOT NULL REFERENCES platform.auth_companies(id),
    account_id uuid NOT NULL REFERENCES platform.accounts(id), credential_id bytea NOT NULL,
    public_key bytea NOT NULL, signature_count bigint NOT NULL CHECK (signature_count >= 0),
    uv_initialized boolean NOT NULL, backup_eligible boolean NOT NULL, backup_state boolean NOT NULL,
    attestation_object bytea NOT NULL, client_data bytea NOT NULL,
    created_at timestamptz NOT NULL, last_used timestamptz,
    PRIMARY KEY(company_id,credential_id),
    FOREIGN KEY (account_id,company_id) REFERENCES platform.company_memberships(account_id,company_id)
);
CREATE INDEX webauthn_account_company_idx ON platform.webauthn_credentials(account_id,company_id);
CREATE TABLE platform.mfa_challenges (
    id uuid PRIMARY KEY, transaction_id uuid NOT NULL REFERENCES platform.mfa_transactions(id),
    purpose varchar(16) NOT NULL CHECK (purpose IN ('REGISTER','AUTHENTICATE')),
    options_json text NOT NULL CHECK (octet_length(options_json) <= 65536),
    expires_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '2 minutes', consumed_at timestamptz
);
CREATE TABLE platform.account_totp (
    account_id uuid NOT NULL, company_id uuid NOT NULL,
    secret_ciphertext text, pending_ciphertext text, pending_transaction uuid REFERENCES platform.mfa_transactions(id),
    enabled boolean NOT NULL DEFAULT false, last_step bigint NOT NULL DEFAULT -1,
    PRIMARY KEY(account_id,company_id),
    FOREIGN KEY(account_id,company_id) REFERENCES platform.company_memberships(account_id,company_id)
);
CREATE TABLE platform.recovery_codes (
    account_id uuid NOT NULL, company_id uuid NOT NULL, digest char(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '1 year', consumed_at timestamptz,
    PRIMARY KEY(account_id,company_id,digest),
    FOREIGN KEY(account_id,company_id) REFERENCES platform.company_memberships(account_id,company_id)
);
CREATE TABLE platform.account_tokens (
    digest char(64) PRIMARY KEY, account_id uuid NOT NULL REFERENCES platform.accounts(id),
    purpose varchar(16) NOT NULL CHECK(purpose IN ('EMAIL_VERIFY','PASSWORD_RESET','MFA_ENROLL')),
    hostname varchar(253) NOT NULL, intended_email varchar(254) NOT NULL, account_version bigint NOT NULL,
    company_id uuid REFERENCES platform.auth_companies(id), company_version bigint, membership_version bigint,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '15 minutes', consumed_at timestamptz
);
CREATE TABLE platform.security_notifications (
    id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES platform.accounts(id), company_id uuid,
    kind varchar(64) NOT NULL, created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    encrypted_delivery text, delivery_hostname varchar(253), recipient_email varchar(254), delivered_at timestamptz,
    CHECK ((encrypted_delivery IS NULL AND delivery_hostname IS NULL AND recipient_email IS NULL)
        OR (encrypted_delivery IS NOT NULL AND delivery_hostname IS NOT NULL AND recipient_email IS NOT NULL))
);
CREATE INDEX security_notifications_pending_idx ON platform.security_notifications(created_at) WHERE delivered_at IS NULL;

-- No direct runtime UPDATE on account activation/email verification/security_version is added.
-- These narrowly scoped functions run as the restricted table owner, not an administrator.
CREATE FUNCTION platform.advance_account_security(p_account uuid,p_expected bigint) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE v_version bigint;
BEGIN
    UPDATE platform.accounts SET security_version = security_version + 1
        WHERE id = p_account AND security_version = p_expected RETURNING security_version INTO v_version;
    IF v_version IS NULL THEN RAISE EXCEPTION USING ERRCODE='40001', MESSAGE='Security state changed'; END IF;
    UPDATE platform.authentication_sessions SET revoked_at = COALESCE(revoked_at,CURRENT_TIMESTAMP) WHERE account_id=p_account;
    RETURN v_version;
END;
$$;
CREATE FUNCTION platform.consume_account_token(p_token text,p_hostname text,p_purpose text,p_new_hash text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE v_account platform.accounts%ROWTYPE; v_token platform.account_tokens%ROWTYPE; v_digest text;
BEGIN
    IF p_purpose NOT IN ('EMAIL_VERIFY','PASSWORD_RESET','MFA_ENROLL')
        OR p_token IS NULL OR p_token !~ '^[A-Za-z0-9_-]{43}$' THEN RETURN false; END IF;
    v_digest := pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(p_token,'UTF8')),'hex');
    SELECT a.* INTO v_account FROM platform.accounts a
      WHERE EXISTS (SELECT 1 FROM platform.account_tokens t WHERE t.account_id=a.id AND t.digest=v_digest)
      FOR UPDATE;
    IF NOT FOUND OR v_account.status='DISABLED' THEN RETURN false; END IF;
    SELECT t.* INTO v_token FROM platform.account_tokens t
      WHERE t.digest=v_digest AND t.account_id=v_account.id AND t.purpose=p_purpose
        AND t.hostname=p_hostname AND t.intended_email=v_account.normalized_email
        AND t.account_version=v_account.security_version AND t.consumed_at IS NULL
        AND pg_catalog.clock_timestamp()<t.expires_at FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    IF v_token.company_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM platform.auth_companies c WHERE c.id=v_token.company_id
          AND c.hostname=v_token.hostname AND c.security_version=v_token.company_version) THEN RETURN false; END IF;
    IF p_purpose='PASSWORD_RESET' THEN
      IF p_new_hash IS NULL OR p_new_hash NOT LIKE '$argon2id$%' OR NOT v_account.email_verified
        OR v_account.privileged OR EXISTS(SELECT 1 FROM platform.company_memberships m
          WHERE m.account_id=v_account.id AND (m.privileged OR m.auth_role='PLATFORM_ADMIN')) THEN RETURN false; END IF;
      UPDATE platform.accounts SET password_hash=p_new_hash,security_version=security_version+1 WHERE id=v_account.id;
    ELSIF p_purpose='EMAIL_VERIFY' THEN
      UPDATE platform.accounts SET email_verified=true,status='ACTIVE' WHERE id=v_account.id;
    ELSE
      IF NOT v_account.email_verified OR v_account.privileged THEN RETURN false; END IF;
    END IF;
    IF p_purpose IN ('EMAIL_VERIFY','MFA_ENROLL') THEN
      UPDATE platform.company_memberships m SET mfa_enrollment_until=CURRENT_TIMESTAMP+INTERVAL '15 minutes'
        FROM platform.auth_companies c WHERE m.account_id=v_account.id AND m.company_id=c.id
          AND c.hostname=p_hostname AND c.id=v_token.company_id AND c.security_version=v_token.company_version
          AND m.security_version=v_token.membership_version AND NOT m.mfa_enrolled AND m.active AND c.status='ACTIVE' AND c.entitled
          AND NOT m.privileged AND m.auth_role<>'PLATFORM_ADMIN'
          AND NOT EXISTS(SELECT 1 FROM platform.webauthn_credentials w WHERE w.account_id=m.account_id AND w.company_id=m.company_id)
          AND NOT EXISTS(SELECT 1 FROM platform.account_totp o WHERE o.account_id=m.account_id AND o.company_id=m.company_id AND o.enabled);
      IF p_purpose='MFA_ENROLL' AND NOT FOUND THEN RETURN false; END IF;
    END IF;
    UPDATE platform.account_tokens SET consumed_at=CURRENT_TIMESTAMP WHERE digest=v_digest;
    UPDATE platform.authentication_sessions SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP) WHERE account_id=v_account.id;
    INSERT INTO platform.security_notifications(id,account_id,kind)
      VALUES (pg_catalog.gen_random_uuid(),v_account.id,CASE WHEN p_purpose='PASSWORD_RESET' THEN 'PASSWORD_RESET' WHEN p_purpose='MFA_ENROLL' THEN 'MFA_ENROLLMENT_APPROVED' ELSE 'EMAIL_VERIFIED' END);
    RETURN true;
END;
$$;
CREATE FUNCTION platform.consume_mfa_enrollment(p_account uuid,p_company uuid) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_version bigint;
BEGIN
  UPDATE platform.company_memberships SET mfa_required=true,mfa_enrolled=true,mfa_enrollment_until=NULL
    WHERE account_id=p_account AND company_id=p_company AND NOT mfa_enrolled AND pg_catalog.clock_timestamp()<mfa_enrollment_until
    RETURNING security_version INTO v_version;
  IF v_version IS NULL THEN RAISE EXCEPTION USING ERRCODE='40001',MESSAGE='Enrollment approval unavailable'; END IF;
  RETURN v_version;
END;
$$;
REVOKE ALL ON FUNCTION platform.consume_mfa_enrollment(uuid,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.consume_mfa_enrollment(uuid,uuid) TO hich_runtime;
REVOKE ALL ON FUNCTION platform.advance_account_security(uuid,bigint) FROM PUBLIC;
REVOKE ALL ON FUNCTION platform.consume_account_token(text,text,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.advance_account_security(uuid,bigint),
    platform.consume_account_token(text,text,text,text) TO hich_runtime;

REVOKE ALL ON platform.mfa_transactions,platform.webauthn_credentials,platform.mfa_challenges,
    platform.account_totp,platform.recovery_codes,platform.account_tokens,platform.security_notifications FROM PUBLIC;
GRANT SELECT ON platform.mfa_transactions,platform.webauthn_credentials,platform.mfa_challenges,
    platform.account_totp,platform.recovery_codes,platform.account_tokens TO hich_runtime;
GRANT INSERT(id,account_id,company_id,hostname,binding_hash,account_version,membership_version,company_version,initial_enrollment),
    UPDATE(attempts,completed,verified_method,verified_at,account_version,membership_version,initial_enrollment,recovery_only)
    ON platform.mfa_transactions TO hich_runtime;
GRANT INSERT,UPDATE(signature_count,backup_state,uv_initialized,last_used) ON platform.webauthn_credentials TO hich_runtime;
GRANT INSERT(id,transaction_id,purpose,options_json),UPDATE(consumed_at) ON platform.mfa_challenges TO hich_runtime;
GRANT INSERT(account_id,company_id,pending_ciphertext,pending_transaction),
    UPDATE(secret_ciphertext,pending_ciphertext,pending_transaction,enabled,last_step) ON platform.account_totp TO hich_runtime;
GRANT INSERT(account_id,company_id,digest),UPDATE(consumed_at) ON platform.recovery_codes TO hich_runtime;
GRANT INSERT(digest,account_id,purpose,hostname,intended_email,account_version,company_id,company_version,membership_version)
    ON platform.account_tokens TO hich_runtime;
GRANT DELETE ON platform.webauthn_credentials,platform.account_totp,platform.recovery_codes TO hich_runtime;
GRANT INSERT (id,account_id,company_id,kind,encrypted_delivery,delivery_hostname,recipient_email) ON platform.security_notifications TO hich_runtime;
GRANT INSERT (assurance,mfa_transaction_id,privileged_session) ON platform.authentication_sessions TO hich_runtime;
