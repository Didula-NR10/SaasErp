-- Bind the privileged mutation to OperationIntent.digest(), independently of its caller.
-- V1-V8 are immutable. SECURITY DEFINER entry points keep a pg_catalog-only search path.
-- Encoding: big-endian signed int32 UTF-8 byte length + bytes for each string;
-- three header strings, int32 detail count, then sorted key/value pairs.
CREATE FUNCTION platform.company_role_operation_digest(
    p_company uuid, p_target uuid, p_expected_role text, p_new_role text)
RETURNS text LANGUAGE plpgsql IMMUTABLE STRICT SET search_path=pg_catalog AS $$
DECLARE
    v_bytes bytea := ''::bytea;
    v_value text;
    v_encoded bytea;
BEGIN
    FOREACH v_value IN ARRAY ARRAY['company.roles.manage','MEMBERSHIP',p_target::text] LOOP
        v_encoded := pg_catalog.convert_to(v_value,'UTF8');
        v_bytes := v_bytes || pg_catalog.int4send(pg_catalog.octet_length(v_encoded)) || v_encoded;
    END LOOP;
    v_bytes := v_bytes || pg_catalog.int4send(3);
    FOREACH v_value IN ARRAY ARRAY[
        'companyId',p_company::text,'currentRole',p_expected_role,'requestedRole',p_new_role] LOOP
        v_encoded := pg_catalog.convert_to(v_value,'UTF8');
        v_bytes := v_bytes || pg_catalog.int4send(pg_catalog.octet_length(v_encoded)) || v_encoded;
    END LOOP;
    RETURN pg_catalog.encode(pg_catalog.sha256(v_bytes),'hex');
END;
$$;
-- Internal helper: called only through the definer, with no new runtime capability.
REVOKE ALL ON FUNCTION platform.company_role_operation_digest(uuid,uuid,text,text)
    FROM PUBLIC,hich_runtime;


CREATE OR REPLACE FUNCTION platform.create_step_up_authorization(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text,
    p_permission text, p_target_type text, p_target_id text, p_digest text,
    p_options text, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF p_id IS NULL OR p_session IS NULL OR p_account IS NULL OR p_company IS NULL OR p_hostname IS NULL OR p_permission IS NULL OR p_target_type IS NULL OR p_target_id IS NULL OR p_digest IS NULL OR p_options IS NULL OR p_correlation IS NULL
    THEN RETURN false; END IF;
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

CREATE OR REPLACE FUNCTION platform.create_dual_approval(
    p_id uuid, p_company uuid, p_hostname text, p_requester uuid, p_session uuid,
    p_permission text, p_target_type text, p_target_id text, p_digest text,
    p_idempotency_digest text, p_step_up uuid, p_correlation uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    IF p_id IS NULL OR p_company IS NULL OR p_hostname IS NULL OR p_requester IS NULL OR p_session IS NULL OR p_permission IS NULL OR p_target_type IS NULL OR p_target_id IS NULL OR p_digest IS NULL OR p_idempotency_digest IS NULL OR p_step_up IS NULL OR p_correlation IS NULL
    THEN RETURN NULL; END IF;
    IF NOT platform.authorize_company_session(p_session,p_requester,p_company,p_hostname,p_permission)
    THEN RETURN NULL; END IF;
    SELECT * INTO v_row FROM platform.dual_approvals
    WHERE company_id=p_company AND requester_id=p_requester AND idempotency_digest=p_idempotency_digest
    FOR UPDATE;
    IF FOUND THEN
        IF v_row.permission_code IS DISTINCT FROM p_permission OR v_row.target_type IS DISTINCT FROM p_target_type
           OR v_row.target_id IS DISTINCT FROM p_target_id OR v_row.details_digest IS DISTINCT FROM p_digest
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
    IF NOT FOUND OR v_row.permission_code IS DISTINCT FROM p_permission OR v_row.target_type IS DISTINCT FROM p_target_type
       OR v_row.target_id IS DISTINCT FROM p_target_id OR v_row.details_digest IS DISTINCT FROM p_digest
    THEN RETURN NULL; END IF;
    IF v_row.id=p_id THEN
        PERFORM platform.record_security_audit(p_requester,p_company,'DUAL_APPROVAL_REQUESTED',
            'APPROVAL',p_id::text,'SUCCEEDED',p_correlation);
    END IF;
    RETURN v_row.id;
END;
$$;

CREATE OR REPLACE FUNCTION platform.approve_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text, p_digest text,
    p_step_up uuid, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    IF p_id IS NULL OR p_session IS NULL OR p_account IS NULL OR p_company IS NULL OR p_hostname IS NULL OR p_digest IS NULL OR p_step_up IS NULL OR p_correlation IS NULL
    THEN RETURN false; END IF;
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.company_id IS DISTINCT FROM p_company OR v_row.hostname IS DISTINCT FROM p_hostname
       OR v_row.details_digest IS DISTINCT FROM p_digest OR v_row.requester_id=p_account
       OR NOT platform.authorize_company_session(v_row.requester_session_id,v_row.requester_id,
            v_row.company_id,v_row.hostname,v_row.permission_code)
       OR NOT platform.authorize_company_session(p_session,p_account,p_company,p_hostname,v_row.permission_code)
    THEN RETURN false; END IF;
    IF v_row.status NOT IN ('REQUESTED','APPROVED') THEN RETURN false; END IF;
    IF clock_timestamp()>=v_row.expires_at THEN
        UPDATE platform.dual_approvals SET status='EXPIRED' WHERE id=p_id;
        PERFORM platform.record_security_audit(v_row.requester_id,v_row.company_id,'DUAL_APPROVAL_EXPIRED',
            'APPROVAL',p_id::text,'EXPIRED',p_correlation);
        RETURN false;
    END IF;
    IF v_row.status='APPROVED' THEN
        RETURN v_row.approver_id=p_account AND v_row.approver_session_id=p_session;
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

CREATE OR REPLACE FUNCTION platform.cancel_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    IF p_id IS NULL OR p_session IS NULL OR p_account IS NULL OR p_company IS NULL OR p_hostname IS NULL OR p_correlation IS NULL
    THEN RETURN false; END IF;
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.requester_id IS DISTINCT FROM p_account OR v_row.requester_session_id IS DISTINCT FROM p_session
       OR v_row.company_id IS DISTINCT FROM p_company OR v_row.hostname IS DISTINCT FROM p_hostname
       OR NOT platform.authorize_company_session(p_session,p_account,p_company,p_hostname,v_row.permission_code)
    THEN RETURN false; END IF;
    IF v_row.status NOT IN ('REQUESTED','APPROVED') THEN RETURN false; END IF;
    IF clock_timestamp()>=v_row.expires_at THEN
        UPDATE platform.dual_approvals SET status='EXPIRED' WHERE id=p_id;
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_EXPIRED',
            'APPROVAL',p_id::text,'EXPIRED',p_correlation);
        RETURN false;
    END IF;
    UPDATE platform.dual_approvals SET status='CANCELLED' WHERE id=p_id;
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_CANCELLED',
            'APPROVAL',p_id::text,'CANCELLED',p_correlation);
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION platform.expire_dual_approval(p_id uuid, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF p_id IS NULL OR p_correlation IS NULL
    THEN RETURN false; END IF;
    UPDATE platform.dual_approvals SET status='EXPIRED'
    WHERE id=p_id AND status IN ('REQUESTED','APPROVED') AND clock_timestamp()>=expires_at;
    IF FOUND THEN
        PERFORM platform.record_security_audit(NULL,NULL,'DUAL_APPROVAL_EXPIRED',
            'APPROVAL',p_id::text,'EXPIRED',p_correlation);
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION platform.consume_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text,
    p_permission text, p_target_type text, p_target_id text, p_digest text, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    IF p_id IS NULL OR p_session IS NULL OR p_account IS NULL OR p_company IS NULL OR p_hostname IS NULL OR p_permission IS NULL OR p_target_type IS NULL OR p_target_id IS NULL OR p_digest IS NULL OR p_correlation IS NULL
    THEN RETURN false; END IF;
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.requester_id IS DISTINCT FROM p_account OR v_row.requester_session_id IS DISTINCT FROM p_session
       OR v_row.company_id IS DISTINCT FROM p_company OR v_row.hostname IS DISTINCT FROM p_hostname
       OR v_row.permission_code IS DISTINCT FROM p_permission OR v_row.target_type IS DISTINCT FROM p_target_type
       OR v_row.target_id IS DISTINCT FROM p_target_id OR v_row.status<>'APPROVED'
       OR v_row.approver_id IS NULL OR v_row.approver_session_id IS NULL
       OR v_row.approver_id=p_account
       OR NOT platform.authorize_company_session(v_row.requester_session_id,v_row.requester_id,
            v_row.company_id,v_row.hostname,v_row.permission_code)
       OR NOT platform.authorize_company_session(v_row.approver_session_id,v_row.approver_id,
            v_row.company_id,v_row.hostname,v_row.permission_code)
    THEN RETURN false; END IF;
    IF clock_timestamp()>=v_row.expires_at THEN
        UPDATE platform.dual_approvals SET status='EXPIRED' WHERE id=p_id AND status='APPROVED';
        IF FOUND THEN
            PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_EXPIRED',
                'APPROVAL',p_id::text,'EXPIRED',p_correlation);
        END IF;
        RETURN false;
    END IF;
    IF v_row.details_digest IS DISTINCT FROM p_digest THEN RETURN false; END IF;
    UPDATE platform.dual_approvals SET status='CONSUMED',consumed_at=clock_timestamp()
    WHERE id=p_id AND status='APPROVED';
    IF FOUND THEN
        PERFORM platform.record_security_audit(p_account,p_company,'DUAL_APPROVAL_CONSUMED',
            'APPROVAL',p_id::text,'CONSUMED',p_correlation);
        RETURN true;
    END IF;
    RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION platform.assign_company_role(
    p_session uuid, p_actor uuid, p_company uuid, p_hostname text,
    p_target uuid, p_expected_role text, p_new_role text, p_approval uuid, p_digest text,
    p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE
    v_approval platform.dual_approvals%ROWTYPE;
    v_current_role text;
    v_digest text;
BEGIN
    IF p_session IS NULL OR p_actor IS NULL OR p_company IS NULL OR p_hostname IS NULL OR p_target IS NULL OR p_expected_role IS NULL OR p_new_role IS NULL OR p_approval IS NULL OR p_digest IS NULL OR p_correlation IS NULL
    THEN RETURN false; END IF;
    IF p_target IS NULL OR p_actor IS NULL OR p_target=p_actor
       OR p_expected_role IS NULL OR p_new_role IS NULL OR p_approval IS NULL OR p_digest IS NULL
       OR p_expected_role NOT IN ('OWNER','ADMIN','MANAGER','CASHIER','EMPLOYEE')
       OR p_new_role NOT IN ('OWNER','ADMIN','MANAGER','CASHIER','EMPLOYEE')
       OR p_correlation IS NULL
       OR NOT platform.authorize_company_session(
            p_session,p_actor,p_company,p_hostname,'company.roles.manage')
    THEN RETURN false; END IF;

    IF p_expected_role=p_new_role THEN RETURN false; END IF;
    v_digest := platform.company_role_operation_digest(p_company,p_target,p_expected_role,p_new_role);
    IF p_digest IS DISTINCT FROM v_digest THEN RETURN false; END IF;

    SELECT * INTO v_approval
      FROM platform.dual_approvals
     WHERE id=p_approval
     FOR UPDATE;
    IF NOT FOUND OR v_approval.requester_id IS DISTINCT FROM p_actor
       OR v_approval.requester_session_id IS DISTINCT FROM p_session
       OR v_approval.company_id IS DISTINCT FROM p_company OR v_approval.hostname IS DISTINCT FROM p_hostname
       OR v_approval.permission_code<>'company.roles.manage'
       OR v_approval.target_type<>'MEMBERSHIP'
       OR v_approval.target_id IS DISTINCT FROM p_target::text
       OR v_approval.details_digest IS DISTINCT FROM v_digest
       OR v_approval.status<>'APPROVED'
       OR v_approval.approver_id IS NULL
       OR v_approval.approver_id=p_actor OR v_approval.approver_id=p_target
    THEN RETURN false; END IF;

    PERFORM 1 FROM platform.auth_companies c
     WHERE c.id=p_company AND c.hostname=p_hostname AND c.status='ACTIVE' AND c.entitled
     FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;

    SELECT m.auth_role INTO v_current_role
      FROM platform.company_memberships m
      JOIN platform.accounts a ON a.id=m.account_id
     WHERE m.account_id=p_target AND m.company_id=p_company
       AND m.active AND NOT m.privileged AND m.auth_role<>'PLATFORM_ADMIN'
       AND a.status='ACTIVE' AND a.email_verified AND NOT a.privileged
       AND m.auth_role=p_expected_role
     FOR UPDATE OF m,a;
    IF NOT FOUND THEN RETURN false; END IF;

    IF v_current_role='OWNER' AND p_new_role<>'OWNER'
       AND (SELECT count(*) FROM platform.company_memberships
             WHERE company_id=p_company AND active AND auth_role='OWNER')<=1
    THEN RETURN false; END IF;

    IF NOT platform.consume_dual_approval(
        p_approval,p_session,p_actor,p_company,p_hostname,
        'company.roles.manage','MEMBERSHIP',p_target::text,v_digest,p_correlation)
    THEN RETURN false; END IF;

    PERFORM pg_catalog.set_config('hich.security.actor_id',p_actor::text,true);
    UPDATE platform.company_memberships
       SET auth_role=p_new_role
     WHERE account_id=p_target AND company_id=p_company
       AND active AND auth_role=p_expected_role;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='40001', MESSAGE='Role assignment state changed';
    END IF;

    PERFORM platform.record_security_audit(
        p_actor,p_company,'ROLE_CHANGED','MEMBERSHIP',p_target::text,'SUCCEEDED',p_correlation);
    RETURN true;
END;
$$;

-- All application consumers use the correlated signatures. V8's last definer
-- dependency has now been replaced. No CASCADE: fail migration on any tracked dependency.
-- PL/pgSQL string bodies were also reviewed because their calls are not pg_depend entries.
DROP FUNCTION platform.consume_dual_approval(uuid,uuid,uuid,uuid,text,text,text,text,text);
DROP FUNCTION platform.cancel_dual_approval(uuid,uuid,uuid,uuid,text);
DROP FUNCTION platform.expire_dual_approval(uuid);
-- CREATE OR REPLACE preserves the existing owner and restricted ACLs.
