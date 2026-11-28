-- Separate platform-admin authorization and atomic, company-bound role assignment.
CREATE TABLE platform.platform_administrator_sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES platform.accounts(id),
    session_binding_digest char(64) NOT NULL CHECK (session_binding_digest ~ '^[0-9a-f]{64}$'),
    hostname varchar(253) NOT NULL CHECK (hostname='admin.hich.com'),
    account_version bigint NOT NULL CHECK (account_version > 0),
    hardware_assurance varchar(24) NOT NULL CHECK (hardware_assurance='HARDWARE_ATTESTED'),
    credential_id_digest char(64) NOT NULL CHECK (credential_id_digest ~ '^[0-9a-f]{64}$'),
    attestation_evidence_digest char(64) NOT NULL CHECK (attestation_evidence_digest ~ '^[0-9a-f]{64}$'),
    attestation_verified_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_access_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CHECK (attestation_verified_at <= created_at),
    CHECK (last_access_at >= created_at),
    CHECK (expires_at <= created_at + INTERVAL '8 hours')
);
CREATE INDEX platform_administrator_sessions_account_idx
    ON platform.platform_administrator_sessions(account_id);

CREATE FUNCTION platform.authorize_platform_session(
    p_session uuid, p_account uuid, p_session_binding_digest text, p_hostname text, p_permission text)
RETURNS boolean LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF p_session_binding_digest IS NULL OR p_session_binding_digest !~ '^[0-9a-f]{64}$'
       OR p_hostname IS DISTINCT FROM 'admin.hich.com'
       OR p_permission IS NULL
    THEN RETURN false; END IF;

    PERFORM 1
    FROM platform.platform_administrator_sessions s
    JOIN platform.accounts a ON a.id=s.account_id
    JOIN platform.platform_account_roles ar ON ar.account_id=a.id AND ar.active
    JOIN platform.authorization_role_permissions arp
      ON arp.scope='PLATFORM' AND arp.role_code=ar.role_code AND arp.permission_code=p_permission
    JOIN platform.authorization_permissions ap
      ON ap.scope='PLATFORM' AND ap.code=p_permission
    WHERE s.id=p_session AND s.account_id=p_account
      AND s.session_binding_digest=p_session_binding_digest
      AND s.hostname=p_hostname AND s.revoked_at IS NULL
      AND s.hardware_assurance='HARDWARE_ATTESTED'
      AND s.credential_id_digest ~ '^[0-9a-f]{64}$'
      AND s.attestation_evidence_digest ~ '^[0-9a-f]{64}$'
      AND s.attestation_verified_at <= s.created_at
      AND s.created_at + INTERVAL '8 hours' > clock_timestamp()
      AND s.last_access_at + INTERVAL '15 minutes' > clock_timestamp()
      AND s.expires_at > clock_timestamp()
      AND a.status='ACTIVE' AND a.email_verified AND a.privileged AND a.mfa_required
      AND a.security_version=s.account_version
    FOR UPDATE OF s,a,ar,arp;
    IF NOT FOUND THEN RETURN false; END IF;

    UPDATE platform.platform_administrator_sessions
       SET last_access_at=clock_timestamp()
     WHERE id=p_session AND revoked_at IS NULL;
    RETURN FOUND;
END;
$$;

CREATE FUNCTION platform.assign_company_role(
    p_session uuid, p_actor uuid, p_company uuid, p_hostname text,
    p_target uuid, p_expected_role text, p_new_role text, p_approval uuid, p_digest text,
    p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE
    v_approval platform.dual_approvals%ROWTYPE;
    v_current_role text;
BEGIN
    IF p_target IS NULL OR p_actor IS NULL OR p_target=p_actor
       OR p_expected_role IS NULL OR p_new_role IS NULL OR p_approval IS NULL OR p_digest IS NULL
       OR p_expected_role NOT IN ('OWNER','ADMIN','MANAGER','CASHIER','EMPLOYEE')
       OR p_new_role NOT IN ('OWNER','ADMIN','MANAGER','CASHIER','EMPLOYEE')
       OR p_correlation IS NULL
       OR NOT platform.authorize_company_session(
            p_session,p_actor,p_company,p_hostname,'company.roles.manage')
    THEN RETURN false; END IF;

    SELECT * INTO v_approval
      FROM platform.dual_approvals
     WHERE id=p_approval
     FOR UPDATE;
    IF NOT FOUND OR v_approval.requester_id<>p_actor
       OR v_approval.requester_session_id<>p_session
       OR v_approval.company_id<>p_company OR v_approval.hostname<>p_hostname
       OR v_approval.permission_code<>'company.roles.manage'
       OR v_approval.target_type<>'MEMBERSHIP'
       OR v_approval.target_id<>p_target::text
       OR v_approval.status<>'APPROVED'
       OR clock_timestamp()>=v_approval.expires_at
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
        'company.roles.manage','MEMBERSHIP',p_target::text,p_digest)
    THEN RETURN false; END IF;

    PERFORM pg_catalog.set_config('hich.security.actor_id',p_actor::text,true);
    UPDATE platform.company_memberships
       SET auth_role=p_new_role
     WHERE account_id=p_target AND company_id=p_company
       AND active AND auth_role=p_expected_role;
    IF NOT FOUND THEN RETURN false; END IF;

    PERFORM platform.record_security_audit(
        p_actor,p_company,'ROLE_CHANGED','MEMBERSHIP',p_target::text,'SUCCEEDED',p_correlation);
    RETURN true;
END;
$$;

REVOKE ALL ON platform.platform_administrator_sessions FROM PUBLIC,hich_runtime;
REVOKE INSERT,UPDATE,DELETE ON platform.platform_account_roles FROM hich_runtime;
REVOKE ALL ON FUNCTION platform.authorize_platform_session(uuid,uuid,text,text,text),
    platform.assign_company_role(uuid,uuid,uuid,text,uuid,text,text,uuid,text,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.authorize_platform_session(uuid,uuid,text,text,text),
    platform.assign_company_role(uuid,uuid,uuid,text,uuid,text,text,uuid,text,uuid) TO hich_runtime;
