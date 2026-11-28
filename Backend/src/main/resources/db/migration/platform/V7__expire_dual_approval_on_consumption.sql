CREATE OR REPLACE FUNCTION platform.consume_dual_approval(
    p_id uuid, p_session uuid, p_account uuid, p_company uuid, p_hostname text,
    p_permission text, p_target_type text, p_target_id text, p_digest text, p_correlation uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE v_row platform.dual_approvals%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM platform.dual_approvals WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR v_row.requester_id<>p_account OR v_row.requester_session_id<>p_session
       OR v_row.company_id<>p_company OR v_row.hostname<>p_hostname
       OR v_row.permission_code<>p_permission OR v_row.target_type<>p_target_type
       OR v_row.target_id<>p_target_id OR v_row.status<>'APPROVED'
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
    IF v_row.details_digest<>p_digest THEN RETURN false; END IF;
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
