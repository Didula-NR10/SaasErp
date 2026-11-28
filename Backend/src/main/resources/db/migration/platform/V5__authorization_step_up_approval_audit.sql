-- Authorization and security workflow foundations. Applied only by the platform migrator.
ALTER TABLE platform.company_memberships DROP CONSTRAINT company_memberships_auth_role_check;
ALTER TABLE platform.company_memberships ADD CONSTRAINT company_memberships_auth_role_check
    CHECK (auth_role IN ('PLATFORM_ADMIN','OWNER','ADMIN','MANAGER','CASHIER','EMPLOYEE'));

CREATE TABLE platform.authorization_roles (
    scope varchar(16) NOT NULL CHECK (scope IN ('PLATFORM','COMPANY')),
    code varchar(32) NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    UNIQUE (code),
    PRIMARY KEY (scope, code)
);

CREATE TABLE platform.authorization_permissions (
    scope varchar(16) NOT NULL CHECK (scope IN ('PLATFORM','COMPANY')),
    code varchar(96) NOT NULL CHECK (code ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*){1,5}$'),
    UNIQUE (code),
    PRIMARY KEY (scope, code)
);

CREATE TABLE platform.authorization_role_permissions (
    scope varchar(16) NOT NULL,
    role_code varchar(32) NOT NULL,
    permission_code varchar(96) NOT NULL,
    PRIMARY KEY (scope, role_code, permission_code),
    FOREIGN KEY (scope, role_code) REFERENCES platform.authorization_roles(scope, code),
    FOREIGN KEY (scope, permission_code) REFERENCES platform.authorization_permissions(scope, code)
);

CREATE TABLE platform.platform_account_roles (
    account_id uuid NOT NULL REFERENCES platform.accounts(id),
    role_code varchar(32) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    grant_version bigint NOT NULL DEFAULT 1 CHECK (grant_version > 0),
    granted_by uuid NOT NULL REFERENCES platform.accounts(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_code),
    FOREIGN KEY (role_code) REFERENCES platform.authorization_roles(code)
);

INSERT INTO platform.authorization_roles(scope, code) VALUES
    ('PLATFORM','PLATFORM_SUPER_ADMIN'), ('PLATFORM','PLATFORM_ADMIN'),
    ('COMPANY','OWNER'), ('COMPANY','ADMIN'), ('COMPANY','MANAGER'),
    ('COMPANY','CASHIER'), ('COMPANY','EMPLOYEE');

INSERT INTO platform.authorization_permissions(scope, code) VALUES
    ('COMPANY','company.members.read'), ('COMPANY','company.members.manage'),
    ('COMPANY','company.roles.manage'), ('COMPANY','company.settings.manage'),
    ('COMPANY','erp.customers.read'), ('COMPANY','erp.customers.write'),
    ('COMPANY','erp.inventory.read'), ('COMPANY','erp.inventory.write'),
    ('COMPANY','erp.reports.read'), ('COMPANY','erp.sales.read'),
    ('COMPANY','erp.sales.create'), ('COMPANY','erp.sales.discount'),
    ('COMPANY','erp.sales.refund'), ('COMPANY','erp.staff.read'),
    ('COMPANY','erp.staff.manage'),
    ('PLATFORM','platform.accounts.read'), ('PLATFORM','platform.accounts.manage'),
    ('PLATFORM','platform.audit.read'), ('PLATFORM','platform.billing.read'),
    ('PLATFORM','platform.billing.manage'), ('PLATFORM','platform.companies.read'),
    ('PLATFORM','platform.companies.manage'), ('PLATFORM','platform.roles.manage'),
    ('PLATFORM','platform.security.manage');

INSERT INTO platform.authorization_role_permissions(scope, role_code, permission_code)
SELECT 'COMPANY', 'OWNER', code FROM platform.authorization_permissions WHERE scope='COMPANY';

INSERT INTO platform.authorization_role_permissions(scope, role_code, permission_code) VALUES
    ('COMPANY','ADMIN','company.members.read'),
    ('COMPANY','ADMIN','company.members.manage'),
    ('COMPANY','ADMIN','company.settings.manage'),
    ('COMPANY','ADMIN','erp.customers.read'),
    ('COMPANY','ADMIN','erp.customers.write'),
    ('COMPANY','ADMIN','erp.inventory.read'),
    ('COMPANY','ADMIN','erp.inventory.write'),
    ('COMPANY','ADMIN','erp.reports.read'),
    ('COMPANY','ADMIN','erp.sales.read'),
    ('COMPANY','ADMIN','erp.sales.create'),
    ('COMPANY','ADMIN','erp.sales.discount'),
    ('COMPANY','ADMIN','erp.sales.refund'),
    ('COMPANY','ADMIN','erp.staff.read'),
    ('COMPANY','ADMIN','erp.staff.manage'),
    ('COMPANY','MANAGER','company.members.read'),
    ('COMPANY','MANAGER','erp.customers.read'),
    ('COMPANY','MANAGER','erp.customers.write'),
    ('COMPANY','MANAGER','erp.inventory.read'),
    ('COMPANY','MANAGER','erp.inventory.write'),
    ('COMPANY','MANAGER','erp.reports.read'),
    ('COMPANY','MANAGER','erp.sales.read'),
    ('COMPANY','MANAGER','erp.sales.create'),
    ('COMPANY','MANAGER','erp.sales.discount'),
    ('COMPANY','MANAGER','erp.staff.read'),
    ('COMPANY','CASHIER','erp.customers.read'),
    ('COMPANY','CASHIER','erp.sales.read'),
    ('COMPANY','CASHIER','erp.sales.create'),
    ('COMPANY','EMPLOYEE','erp.customers.read'),
    ('COMPANY','EMPLOYEE','erp.inventory.read'),
    ('COMPANY','EMPLOYEE','erp.sales.read'),
    ('COMPANY','EMPLOYEE','erp.staff.read'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.accounts.read'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.accounts.manage'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.audit.read'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.billing.read'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.billing.manage'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.companies.read'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.companies.manage'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.roles.manage'),
    ('PLATFORM','PLATFORM_SUPER_ADMIN','platform.security.manage'),
    ('PLATFORM','PLATFORM_ADMIN','platform.accounts.read'),
    ('PLATFORM','PLATFORM_ADMIN','platform.audit.read'),
    ('PLATFORM','PLATFORM_ADMIN','platform.billing.read'),
    ('PLATFORM','PLATFORM_ADMIN','platform.companies.read');

CREATE TABLE platform.security_audit_events (
    id uuid PRIMARY KEY,
    actor_id uuid,
    company_id uuid,
    action varchar(40) NOT NULL CHECK (action IN (
        'LOGIN_SUCCEEDED','LOGIN_DENIED','AUTHORIZATION_DENIED','ROLE_CHANGED',
        'FACTOR_ADDED','FACTOR_REMOVED','FACTOR_RECOVERY_STARTED','RECOVERY_CODES_REPLACED',
        'PASSWORD_CHANGED','PASSWORD_RESET','EMAIL_VERIFIED','MFA_ENROLLMENT_APPROVED',
        'SESSION_REVOKED','SESSIONS_REVOKED','STEP_UP_CREATED','STEP_UP_SUCCEEDED',
        'STEP_UP_DENIED','STEP_UP_CONSUMED','DUAL_APPROVAL_REQUESTED',
        'DUAL_APPROVAL_GRANTED','DUAL_APPROVAL_CANCELLED','DUAL_APPROVAL_EXPIRED',
        'DUAL_APPROVAL_CONSUMED')),
    target_type varchar(16) NOT NULL CHECK (target_type IN (
        'ACCOUNT','COMPANY','MEMBERSHIP','SESSION','PERMISSION','CREDENTIAL','STEP_UP','APPROVAL','RESOURCE')),
    target_id varchar(128) CHECK (target_id IS NULL OR target_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    outcome varchar(16) NOT NULL CHECK (outcome IN ('SUCCEEDED','DENIED','FAILED','CANCELLED','EXPIRED','CONSUMED')),
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    correlation_id uuid NOT NULL,
    event_digest char(64) NOT NULL CHECK (event_digest ~ '^[0-9a-f]{64}$')
);
CREATE INDEX security_audit_events_company_time_idx
    ON platform.security_audit_events(company_id, occurred_at DESC);
CREATE INDEX security_audit_events_actor_time_idx
    ON platform.security_audit_events(actor_id, occurred_at DESC);

CREATE FUNCTION platform.reject_security_audit_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='Security audit events are append-only';
END;
$$;
CREATE TRIGGER security_audit_events_immutable
    BEFORE UPDATE OR DELETE ON platform.security_audit_events
    FOR EACH ROW EXECUTE FUNCTION platform.reject_security_audit_mutation();
CREATE FUNCTION platform.record_security_audit(
    p_actor uuid, p_company uuid, p_action text, p_target_type text,
    p_target_id text, p_outcome text, p_correlation uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_id uuid := pg_catalog.gen_random_uuid(); v_time timestamptz := pg_catalog.clock_timestamp();
BEGIN
    IF p_action NOT IN (
        'LOGIN_SUCCEEDED','LOGIN_DENIED','AUTHORIZATION_DENIED','ROLE_CHANGED',
        'FACTOR_ADDED','FACTOR_REMOVED','FACTOR_RECOVERY_STARTED','RECOVERY_CODES_REPLACED',
        'PASSWORD_CHANGED','PASSWORD_RESET','EMAIL_VERIFIED','MFA_ENROLLMENT_APPROVED',
        'SESSION_REVOKED','SESSIONS_REVOKED','STEP_UP_CREATED','STEP_UP_SUCCEEDED',
        'STEP_UP_DENIED','STEP_UP_CONSUMED','DUAL_APPROVAL_REQUESTED',
        'DUAL_APPROVAL_GRANTED','DUAL_APPROVAL_CANCELLED','DUAL_APPROVAL_EXPIRED',
        'DUAL_APPROVAL_CONSUMED')
        OR p_target_type NOT IN ('ACCOUNT','COMPANY','MEMBERSHIP','SESSION','PERMISSION','CREDENTIAL','STEP_UP','APPROVAL','RESOURCE')
        OR p_outcome NOT IN ('SUCCEEDED','DENIED','FAILED','CANCELLED','EXPIRED','CONSUMED')
        OR p_correlation IS NULL
        OR (p_target_id IS NOT NULL AND (pg_catalog.length(p_target_id)>128 OR p_target_id !~ '^[A-Za-z0-9._:-]{1,128}$'))
        OR (p_actor IS NOT NULL AND NOT EXISTS (SELECT 1 FROM platform.accounts WHERE id=p_actor))
        OR (p_company IS NOT NULL AND NOT EXISTS (SELECT 1 FROM platform.auth_companies WHERE id=p_company))
    THEN RAISE EXCEPTION USING ERRCODE='22023', MESSAGE='Invalid security audit event'; END IF;
    INSERT INTO platform.security_audit_events(id,actor_id,company_id,action,target_type,target_id,outcome,occurred_at,correlation_id,event_digest)
    VALUES (v_id,p_actor,p_company,p_action,p_target_type,p_target_id,p_outcome,v_time,p_correlation,
        pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(pg_catalog.jsonb_build_array(
            v_id,p_actor,p_company,p_action,p_target_type,p_target_id,p_outcome,
            v_time,p_correlation)::text,'UTF8')),'hex'));
    RETURN v_id;
END;
$$;

CREATE FUNCTION platform.audit_security_notification() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_action text; v_correlation text;
BEGIN
    v_action := CASE NEW.kind
        WHEN 'FACTOR_ADDED' THEN 'FACTOR_ADDED'
        WHEN 'FACTOR_REMOVED' THEN 'FACTOR_REMOVED'
        WHEN 'FACTOR_RECOVERY_STARTED' THEN 'FACTOR_RECOVERY_STARTED'
        WHEN 'RECOVERY_CODES_REPLACED' THEN 'RECOVERY_CODES_REPLACED'
        WHEN 'PASSWORD_CHANGED' THEN 'PASSWORD_CHANGED'
        WHEN 'SESSIONS_REVOKED' THEN 'SESSIONS_REVOKED'
        ELSE NULL
    END;
    IF v_action IS NOT NULL THEN
        v_correlation := current_setting('hich.security.correlation_id',true);
        PERFORM platform.record_security_audit(NEW.account_id,NEW.company_id,v_action,
            'ACCOUNT',NEW.account_id::text,'SUCCEEDED',
            CASE WHEN v_correlation ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                THEN v_correlation::uuid ELSE gen_random_uuid() END);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER security_notifications_audit
    AFTER INSERT ON platform.security_notifications
    FOR EACH ROW EXECUTE FUNCTION platform.audit_security_notification();

CREATE FUNCTION platform.audit_account_token_consumption() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_action text; v_correlation text;
BEGIN
    IF OLD.consumed_at IS NOT NULL OR NEW.consumed_at IS NULL THEN RETURN NEW; END IF;
    v_action := CASE NEW.purpose
        WHEN 'PASSWORD_RESET' THEN 'PASSWORD_RESET'
        WHEN 'EMAIL_VERIFY' THEN 'EMAIL_VERIFIED'
        WHEN 'MFA_ENROLL' THEN 'MFA_ENROLLMENT_APPROVED'
        ELSE NULL
    END;
    IF v_action IS NOT NULL THEN
        v_correlation := current_setting('hich.security.correlation_id',true);
        PERFORM platform.record_security_audit(NEW.account_id,NEW.company_id,v_action,
            'ACCOUNT',NEW.account_id::text,'SUCCEEDED',
            CASE WHEN v_correlation ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                THEN v_correlation::uuid ELSE gen_random_uuid() END);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER account_token_consumption_audit
    AFTER UPDATE OF consumed_at ON platform.account_tokens
    FOR EACH ROW EXECUTE FUNCTION platform.audit_account_token_consumption();

CREATE TABLE platform.step_up_authorizations (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES platform.authentication_sessions(id),
    account_id uuid NOT NULL REFERENCES platform.accounts(id),
    company_id uuid NOT NULL REFERENCES platform.auth_companies(id),
    hostname varchar(253) NOT NULL,
    permission_code varchar(96) NOT NULL,
    correlation_id uuid NOT NULL,
    target_type varchar(16) NOT NULL CHECK (target_type IN ('ACCOUNT','COMPANY','MEMBERSHIP','SESSION','PERMISSION','CREDENTIAL','RESOURCE')),
    target_id varchar(128) NOT NULL CHECK (target_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    details_digest char(64) NOT NULL CHECK (details_digest ~ '^[0-9a-f]{64}$'),
    challenge_options text NOT NULL CHECK (octet_length(challenge_options)<=65536),
    challenge_expires_at timestamptz NOT NULL DEFAULT (clock_timestamp()+INTERVAL '2 minutes'),
    expires_at timestamptz NOT NULL DEFAULT (clock_timestamp()+INTERVAL '5 minutes'),
    challenge_consumed_at timestamptz,
    verified_at timestamptz,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (permission_code) REFERENCES platform.authorization_permissions(code)
);

CREATE TABLE platform.dual_approvals (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES platform.auth_companies(id),
    hostname varchar(253) NOT NULL,
    requester_id uuid NOT NULL REFERENCES platform.accounts(id),
    requester_session_id uuid NOT NULL REFERENCES platform.authentication_sessions(id),
    requester_step_up_id uuid NOT NULL UNIQUE REFERENCES platform.step_up_authorizations(id),
    approver_id uuid REFERENCES platform.accounts(id),
    approver_session_id uuid REFERENCES platform.authentication_sessions(id),
    approver_step_up_id uuid UNIQUE REFERENCES platform.step_up_authorizations(id),
    permission_code varchar(96) NOT NULL,
    target_type varchar(16) NOT NULL CHECK (target_type IN ('ACCOUNT','COMPANY','MEMBERSHIP','SESSION','PERMISSION','CREDENTIAL','RESOURCE')),
    target_id varchar(128) NOT NULL CHECK (target_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    details_digest char(64) NOT NULL CHECK (details_digest ~ '^[0-9a-f]{64}$'),
    idempotency_digest char(64) NOT NULL CHECK (idempotency_digest ~ '^[0-9a-f]{64}$'),
    status varchar(16) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','APPROVED','CANCELLED','EXPIRED','CONSUMED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at timestamptz NOT NULL DEFAULT (clock_timestamp()+INTERVAL '15 minutes'),
    approved_at timestamptz,
    consumed_at timestamptz,
    UNIQUE(company_id, requester_id, idempotency_digest),
    FOREIGN KEY (permission_code) REFERENCES platform.authorization_permissions(code),
    CHECK ((approver_id IS NULL) = (approver_session_id IS NULL)),
    CHECK ((approver_id IS NULL) = (approver_step_up_id IS NULL)),
    CHECK (status<>'REQUESTED' OR approver_id IS NULL),
    CHECK (status NOT IN ('APPROVED','CONSUMED') OR approver_id IS NOT NULL),
    CHECK ((status='CONSUMED') = (consumed_at IS NOT NULL))
);

CREATE FUNCTION platform.authorize_company_session(
    p_session uuid, p_account uuid, p_company uuid, p_hostname text, p_permission text)
RETURNS boolean LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM 1
    FROM platform.authentication_sessions s
    JOIN platform.accounts a ON a.id=s.account_id
    JOIN platform.company_memberships m ON m.account_id=a.id AND m.company_id=s.company_id
    JOIN platform.auth_companies c ON c.id=m.company_id
    JOIN platform.authorization_role_permissions rp
      ON rp.scope='COMPANY' AND rp.role_code=m.auth_role AND rp.permission_code=p_permission
    WHERE s.id=p_session AND s.account_id=p_account AND s.company_id=p_company
      AND s.hostname=p_hostname AND s.revoked_at IS NULL
      AND s.created_at + CASE WHEN s.privileged_session THEN INTERVAL '8 hours' ELSE INTERVAL '12 hours' END > clock_timestamp()
      AND a.status='ACTIVE' AND a.email_verified AND NOT a.privileged
      AND a.security_version=s.account_version
      AND m.active AND NOT m.privileged AND m.auth_role<>'PLATFORM_ADMIN'
      AND m.security_version=s.membership_version
      AND c.status='ACTIVE' AND c.entitled AND c.hostname=s.hostname
      AND c.security_version=s.company_version
      AND (s.assurance='WEBAUTHN' AND EXISTS (
            SELECT 1 FROM platform.webauthn_credentials w
            WHERE w.account_id=a.id AND w.company_id=c.id)
        OR s.assurance='TOTP' AND c.totp_allowed AND m.totp_allowed AND EXISTS (
            SELECT 1 FROM platform.account_totp t
            WHERE t.account_id=a.id AND t.company_id=c.id AND t.enabled)
        OR s.assurance='PASSWORD' AND m.auth_role='EMPLOYEE'
            AND NOT a.mfa_required AND NOT m.mfa_required AND NOT m.mfa_enrolled AND NOT c.mfa_required
            AND NOT EXISTS (SELECT 1 FROM platform.webauthn_credentials w
                WHERE w.account_id=a.id AND w.company_id=c.id)
            AND NOT EXISTS (SELECT 1 FROM platform.account_totp t
                WHERE t.account_id=a.id AND t.company_id=c.id AND t.enabled))
    FOR UPDATE OF s,a,m,c,rp;
    RETURN FOUND;
END;
$$;

CREATE FUNCTION platform.audit_step_up_creation() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM platform.record_security_audit(NEW.account_id,NEW.company_id,'STEP_UP_CREATED',
        'STEP_UP',NEW.id::text,'SUCCEEDED',NEW.correlation_id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER step_up_authorizations_audit
    AFTER INSERT ON platform.step_up_authorizations
    FOR EACH ROW EXECUTE FUNCTION platform.audit_step_up_creation();

CREATE FUNCTION platform.create_step_up_authorization(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text,
    p_permission text, p_target_type text, p_target_id text, p_digest text,
    p_options text, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF octet_length(p_options)>65536 OR p_options IS NULL OR p_correlation IS NULL
       OR NOT platform.authorize_company_session(p_session,p_account,p_company,p_hostname,p_permission)
    THEN RETURN false; END IF;
    INSERT INTO platform.step_up_authorizations(id,session_id,account_id,company_id,hostname,
        permission_code,target_type,target_id,details_digest,challenge_options,correlation_id)
    VALUES (p_id,p_session,p_account,p_company,p_hostname,p_permission,p_target_type,p_target_id,
        p_digest,p_options,p_correlation);
    RETURN true;
END;
$$;

CREATE FUNCTION platform.create_dual_approval(
    p_id uuid, p_company uuid, p_hostname text, p_requester uuid, p_session uuid,
    p_permission text, p_target_type text, p_target_id text, p_digest text,
    p_idempotency_digest text, p_step_up uuid, p_correlation uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    IF NOT platform.authorize_company_session(p_session,p_requester,p_company,p_hostname,p_permission)
    THEN RETURN NULL; END IF;
    SELECT * INTO v_row FROM platform.dual_approvals
    WHERE company_id=p_company AND requester_id=p_requester AND idempotency_digest=p_idempotency_digest
    FOR UPDATE;
    IF FOUND THEN
        IF v_row.permission_code<>p_permission OR v_row.target_type<>p_target_type
           OR v_row.target_id<>p_target_id OR v_row.details_digest<>p_digest
        THEN RETURN NULL; END IF;
        RETURN v_row.id;
    END IF;
    PERFORM 1 FROM platform.step_up_authorizations s
        WHERE s.id=p_step_up AND s.session_id=p_session AND s.account_id=p_requester
          AND s.company_id=p_company AND s.hostname=p_hostname AND s.permission_code=p_permission
          AND s.target_type=p_target_type AND s.target_id=p_target_id AND s.details_digest=p_digest
          AND s.verified_at IS NOT NULL AND s.consumed_at IS NULL
          AND clock_timestamp()<s.expires_at
        FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    UPDATE platform.step_up_authorizations SET consumed_at=clock_timestamp()
    WHERE id=p_step_up AND consumed_at IS NULL;
    IF NOT FOUND THEN RETURN NULL; END IF;
    PERFORM platform.record_security_audit(p_requester,p_company,'STEP_UP_CONSUMED',
        'STEP_UP',p_step_up::text,'CONSUMED',p_correlation);
    INSERT INTO platform.dual_approvals(id,company_id,hostname,requester_id,requester_session_id,
        requester_step_up_id,permission_code,target_type,target_id,details_digest,idempotency_digest)
    VALUES (p_id,p_company,p_hostname,p_requester,p_session,p_step_up,p_permission,p_target_type,
        p_target_id,p_digest,p_idempotency_digest)
    ON CONFLICT(company_id,requester_id,idempotency_digest) DO NOTHING;
    SELECT * INTO v_row FROM platform.dual_approvals
    WHERE company_id=p_company AND requester_id=p_requester AND idempotency_digest=p_idempotency_digest
    FOR UPDATE;
    IF NOT FOUND OR v_row.permission_code<>p_permission OR v_row.target_type<>p_target_type
       OR v_row.target_id<>p_target_id OR v_row.details_digest<>p_digest
    THEN RETURN NULL; END IF;
    IF v_row.id=p_id THEN
        PERFORM platform.record_security_audit(p_requester,p_company,'DUAL_APPROVAL_REQUESTED',
            'APPROVAL',p_id::text,'SUCCEEDED',p_correlation);
    END IF;
    RETURN v_row.id;
END;
$$;

CREATE FUNCTION platform.burn_step_up_challenge(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_result text;
BEGIN
    UPDATE platform.step_up_authorizations s SET challenge_consumed_at=clock_timestamp()
    WHERE s.id=p_id AND s.session_id=p_session AND s.account_id=p_account AND s.company_id=p_company
      AND s.hostname=p_hostname AND s.challenge_consumed_at IS NULL
      AND clock_timestamp()<s.challenge_expires_at AND clock_timestamp()<s.expires_at
      AND platform.authorize_company_session(p_session,p_account,p_company,p_hostname,s.permission_code)
    RETURNING s.challenge_options INTO v_result;
    RETURN v_result;
END;
$$;

CREATE FUNCTION platform.complete_step_up(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    UPDATE platform.step_up_authorizations s SET verified_at=clock_timestamp(),challenge_options=''
    WHERE s.id=p_id AND s.session_id=p_session AND s.account_id=p_account AND s.company_id=p_company
      AND s.hostname=p_hostname AND s.challenge_consumed_at IS NOT NULL AND s.verified_at IS NULL
      AND clock_timestamp()<s.expires_at
      AND platform.authorize_company_session(p_session,p_account,p_company,p_hostname,s.permission_code);
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'STEP_UP_SUCCEEDED',
            'STEP_UP',p_id::text,'SUCCEEDED',(
                SELECT correlation_id FROM platform.step_up_authorizations WHERE id=p_id));
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION platform.consume_step_up(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text,
    p_permission text, p_target_type text, p_target_id text, p_digest text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    UPDATE platform.step_up_authorizations s SET consumed_at=clock_timestamp()
    WHERE s.id=p_id AND s.session_id=p_session AND s.account_id=p_account AND s.company_id=p_company
      AND s.hostname=p_hostname AND s.permission_code=p_permission AND s.target_type=p_target_type
      AND s.target_id=p_target_id AND s.details_digest=p_digest AND s.verified_at IS NOT NULL
      AND s.consumed_at IS NULL AND clock_timestamp()<s.expires_at
      AND platform.authorize_company_session(p_session,p_account,p_company,p_hostname,s.permission_code);
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'STEP_UP_CONSUMED',
            'STEP_UP',p_id::text,'CONSUMED',(
                SELECT correlation_id FROM platform.step_up_authorizations WHERE id=p_id));
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION platform.approve_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text, p_digest text,
    p_step_up uuid, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.company_id<>p_company OR v_row.hostname<>p_hostname
       OR v_row.details_digest<>p_digest OR v_row.requester_id=p_account
       OR NOT platform.authorize_company_session(v_row.requester_session_id,v_row.requester_id,
            v_row.company_id,v_row.hostname,v_row.permission_code)
       OR NOT platform.authorize_company_session(p_session,p_account,p_company,p_hostname,v_row.permission_code)
    THEN RETURN false; END IF;
    IF v_row.status='APPROVED' THEN
        RETURN v_row.approver_id=p_account AND v_row.approver_session_id=p_session;
    END IF;
    IF v_row.status<>'REQUESTED' THEN RETURN false; END IF;
    IF clock_timestamp()>=v_row.expires_at THEN
        UPDATE platform.dual_approvals SET status='EXPIRED' WHERE id=p_id;
        PERFORM platform.record_security_audit(v_row.requester_id,v_row.company_id,'DUAL_APPROVAL_EXPIRED',
            'APPROVAL',p_id::text,'EXPIRED',p_correlation);
        RETURN false;
    END IF;
    PERFORM 1 FROM platform.step_up_authorizations s
        WHERE s.id=p_step_up AND s.session_id=p_session AND s.account_id=p_account
          AND s.company_id=p_company AND s.hostname=p_hostname AND s.permission_code=v_row.permission_code
          AND s.target_type=v_row.target_type AND s.target_id=v_row.target_id
          AND s.details_digest=v_row.details_digest AND s.verified_at IS NOT NULL
          AND s.consumed_at IS NULL AND clock_timestamp()<s.expires_at
        FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    UPDATE platform.step_up_authorizations SET consumed_at=clock_timestamp()
    WHERE id=p_step_up AND consumed_at IS NULL;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM platform.record_security_audit(p_account,p_company,'STEP_UP_CONSUMED',
        'STEP_UP',p_step_up::text,'CONSUMED',p_correlation);
    UPDATE platform.dual_approvals SET status='APPROVED',approver_id=p_account,
        approver_session_id=p_session,approver_step_up_id=p_step_up,approved_at=clock_timestamp()
    WHERE id=p_id AND status='REQUESTED';
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_GRANTED',
            'APPROVAL',p_id::text,'SUCCEEDED',p_correlation);
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION platform.cancel_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.requester_id<>p_account OR v_row.requester_session_id<>p_session
       OR v_row.company_id<>p_company OR v_row.hostname<>p_hostname
       OR NOT platform.authorize_company_session(p_session,p_account,p_company,p_hostname,v_row.permission_code)
    THEN RETURN false; END IF;
    IF v_row.status NOT IN ('REQUESTED','APPROVED') THEN RETURN false; END IF;
    IF clock_timestamp()>=v_row.expires_at THEN
        UPDATE platform.dual_approvals SET status='EXPIRED' WHERE id=p_id;
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_EXPIRED',
            'APPROVAL',p_id::text,'EXPIRED',pg_catalog.gen_random_uuid());
        RETURN false;
    END IF;
    UPDATE platform.dual_approvals SET status='CANCELLED' WHERE id=p_id;
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_CANCELLED',
            'APPROVAL',p_id::text,'CANCELLED',pg_catalog.gen_random_uuid());
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION platform.expire_dual_approval(p_id uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    UPDATE platform.dual_approvals SET status='EXPIRED'
    WHERE id=p_id AND status IN ('REQUESTED','APPROVED') AND clock_timestamp()>=expires_at;
    IF FOUND THEN
        PERFORM platform.record_security_audit(NULL,NULL,'DUAL_APPROVAL_EXPIRED',
            'APPROVAL',p_id::text,'EXPIRED',pg_catalog.gen_random_uuid());
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION platform.consume_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text,
    p_permission text, p_target_type text, p_target_id text, p_digest text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.requester_id<>p_account OR v_row.requester_session_id<>p_session
       OR v_row.company_id<>p_company OR v_row.hostname<>p_hostname
       OR v_row.permission_code<>p_permission OR v_row.target_type<>p_target_type
       OR v_row.target_id<>p_target_id OR v_row.details_digest<>p_digest
       OR v_row.status<>'APPROVED' OR clock_timestamp()>=v_row.expires_at
       OR v_row.approver_id=p_account
       OR NOT platform.authorize_company_session(v_row.requester_session_id,v_row.requester_id,
            v_row.company_id,v_row.hostname,v_row.permission_code)
       OR NOT platform.authorize_company_session(v_row.approver_session_id,v_row.approver_id,
            v_row.company_id,v_row.hostname,v_row.permission_code)
    THEN RETURN false; END IF;
    UPDATE platform.dual_approvals SET status='CONSUMED',consumed_at=clock_timestamp()
    WHERE id=p_id AND status='APPROVED';
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_CONSUMED',
            'APPROVAL',p_id::text,'CONSUMED',pg_catalog.gen_random_uuid());
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION platform.audit_authorization_change() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_actor uuid; v_scope text; v_role text; v_permission text;
BEGIN
    v_actor := NULLIF(current_setting('hich.security.actor_id',true),'')::uuid;
    IF v_actor IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='Role changes require an attributed actor';
    END IF;
    IF TG_TABLE_NAME='platform_account_roles' THEN
        v_scope := 'PLATFORM';
        v_role := COALESCE(NEW.role_code,OLD.role_code);
        PERFORM platform.record_security_audit(v_actor,NULL,'ROLE_CHANGED','ACCOUNT',
            COALESCE(NEW.account_id,OLD.account_id)::text,'SUCCEEDED',pg_catalog.gen_random_uuid());
    ELSE
        v_scope := COALESCE(NEW.scope,OLD.scope);
        v_role := COALESCE(NEW.role_code,OLD.role_code);
        v_permission := COALESCE(NEW.permission_code,OLD.permission_code);
        PERFORM platform.record_security_audit(v_actor,NULL,'ROLE_CHANGED','PERMISSION',
            v_scope||':'||v_role||':'||v_permission,'SUCCEEDED',pg_catalog.gen_random_uuid());
    END IF;
    RETURN COALESCE(NEW,OLD);
END;
$$;
CREATE TRIGGER platform_account_roles_audit
    AFTER INSERT OR UPDATE OR DELETE ON platform.platform_account_roles
    FOR EACH ROW EXECUTE FUNCTION platform.audit_authorization_change();
CREATE TRIGGER authorization_role_permissions_audit
    AFTER INSERT OR UPDATE OR DELETE ON platform.authorization_role_permissions
    FOR EACH ROW EXECUTE FUNCTION platform.audit_authorization_change();

REVOKE ALL ON platform.authorization_roles,platform.authorization_permissions,
    platform.authorization_role_permissions,platform.platform_account_roles,
    platform.security_audit_events,platform.step_up_authorizations,platform.dual_approvals FROM PUBLIC;
GRANT SELECT ON platform.authorization_roles,platform.authorization_permissions,
    platform.authorization_role_permissions,platform.platform_account_roles TO hich_runtime;
GRANT EXECUTE ON FUNCTION platform.record_security_audit(uuid,uuid,text,text,text,text,uuid),
    platform.authorize_company_session(uuid,uuid,uuid,text,text),
platform.create_step_up_authorization(uuid,uuid,uuid,uuid,text,text,text,text,text,text,uuid),
    platform.create_dual_approval(uuid,uuid,text,uuid,uuid,text,text,text,text,text,uuid,uuid),
    platform.burn_step_up_challenge(uuid,uuid,uuid,uuid,text),
    platform.complete_step_up(uuid,uuid,uuid,uuid,text),
    platform.consume_step_up(uuid,uuid,uuid,uuid,text,text,text,text,text),
    platform.approve_dual_approval(uuid,uuid,uuid,uuid,text,text,uuid,uuid),
    platform.cancel_dual_approval(uuid,uuid,uuid,uuid,text),
    platform.expire_dual_approval(uuid),
    platform.consume_dual_approval(uuid,uuid,uuid,uuid,text,text,text,text,text)
    TO hich_runtime;
REVOKE ALL ON FUNCTION platform.record_security_audit(uuid,uuid,text,text,text,text,uuid),
    platform.authorize_company_session(uuid,uuid,uuid,text,text),
    platform.create_step_up_authorization(uuid,uuid,uuid,uuid,text,text,text,text,text,text,uuid),
    platform.create_dual_approval(uuid,uuid,text,uuid,uuid,text,text,text,text,text,uuid,uuid),
    platform.burn_step_up_challenge(uuid,uuid,uuid,uuid,text),
    platform.complete_step_up(uuid,uuid,uuid,uuid,text),
    platform.consume_step_up(uuid,uuid,uuid,uuid,text,text,text,text,text),
    platform.approve_dual_approval(uuid,uuid,uuid,uuid,text,text,uuid,uuid),
    platform.cancel_dual_approval(uuid,uuid,uuid,uuid,text),
    platform.expire_dual_approval(uuid),
    platform.consume_dual_approval(uuid,uuid,uuid,uuid,text,text,text,text,text)
    FROM PUBLIC;
REVOKE ALL ON FUNCTION platform.audit_step_up_creation(),platform.audit_authorization_change(),
    platform.reject_security_audit_mutation(),platform.audit_security_notification(),
    platform.audit_account_token_consumption() FROM PUBLIC;
GRANT SELECT ON platform.dual_approvals TO hich_runtime;
