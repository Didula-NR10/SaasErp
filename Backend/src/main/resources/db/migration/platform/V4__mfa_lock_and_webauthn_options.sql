CREATE FUNCTION platform.lock_mfa_transaction_state(p_id uuid, p_binding_hash text, p_hostname text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
BEGIN
    PERFORM 1
    FROM platform.mfa_transactions t
    JOIN platform.accounts a ON a.id=t.account_id
    JOIN platform.company_memberships m ON m.account_id=a.id AND m.company_id=t.company_id
    JOIN platform.auth_companies c ON c.id=m.company_id
    WHERE t.id=p_id AND t.binding_hash=p_binding_hash AND t.hostname=p_hostname AND c.hostname=t.hostname
        AND NOT t.completed AND pg_catalog.clock_timestamp()<t.expires_at AND t.attempts<=10
        AND a.status='ACTIVE' AND a.email_verified AND m.active AND c.status='ACTIVE' AND c.entitled
        AND a.security_version=t.account_version AND m.security_version=t.membership_version
        AND c.security_version=t.company_version
        AND (NOT t.initial_enrollment OR t.recovery_only OR pg_catalog.clock_timestamp()<m.mfa_enrollment_until)
    FOR UPDATE OF a,c,m,t;

    RETURN FOUND;
END;
$$;

REVOKE ALL ON FUNCTION platform.lock_mfa_transaction_state(uuid,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.lock_mfa_transaction_state(uuid,text,text) TO hich_runtime;
