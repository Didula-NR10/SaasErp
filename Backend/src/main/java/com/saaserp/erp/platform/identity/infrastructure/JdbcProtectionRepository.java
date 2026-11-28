package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.MfaPolicy;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcProtectionRepository implements ProtectionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate independent;
    public JdbcProtectionRepository(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);this.transaction.setTimeout(10);
        this.independent=new TransactionTemplate(manager);
        this.independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independent.setTimeout(5);
    }
    @Override public <T> T atomic(Supplier<T> action) {return transaction.execute(status -> action.get());}
    @Override public State currentState(SessionGrant grant) {
        var states=jdbc.query("""
            SELECT s.id,s.account_id,s.company_id,s.hostname,s.account_version,s.membership_version,s.company_version,
                m.auth_role,m.mfa_required OR m.mfa_enrolled AS member_mfa,c.mfa_required AS company_mfa,
                a.mfa_required AS account_mfa,(a.privileged OR m.privileged) AS hardware,
                m.totp_allowed AND c.totp_allowed AS allow_totp,
                EXISTS(SELECT 1 FROM platform.webauthn_credentials w
                    WHERE w.account_id=a.id AND w.company_id=c.id) AS has_factor
            FROM platform.authentication_sessions s
            JOIN platform.accounts a ON a.id=s.account_id
            JOIN platform.company_memberships m ON m.account_id=a.id AND m.company_id=s.company_id
            JOIN platform.auth_companies c ON c.id=m.company_id
            WHERE s.id=? AND s.account_id=? AND s.company_id=? AND s.hostname=?
                AND s.revoked_at IS NULL AND s.account_version=a.security_version
                AND s.membership_version=m.security_version AND s.company_version=c.security_version
                AND a.status='ACTIVE' AND a.email_verified AND m.active AND c.status='ACTIVE' AND c.entitled
                AND c.hostname=s.hostname AND a.security_version=? AND m.security_version=? AND c.security_version=?
                AND s.created_at + CASE WHEN s.privileged_session THEN INTERVAL '8 hours' ELSE INTERVAL '12 hours' END > CURRENT_TIMESTAMP
            """,(rs,n)->{
                var policy=new MfaPolicy(MfaPolicy.Role.valueOf(rs.getString("auth_role")),rs.getBoolean("account_mfa"),
                    rs.getBoolean("company_mfa"),rs.getBoolean("member_mfa"),rs.getBoolean("hardware"),rs.getBoolean("allow_totp"));
                return new State(rs.getObject("id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("company_id",UUID.class),
                    rs.getString("hostname"),rs.getLong("account_version"),rs.getLong("membership_version"),rs.getLong("company_version"),
                    policy,false,false,null,null);
            },grant.id(),grant.accountId(),grant.companyId(),grant.hostname(),grant.accountVersion(),grant.membershipVersion(),grant.companyVersion());
        if(states.size()!=1)throw new ProtectionFailure();
        return states.getFirst();
    }
    @Override public UUID begin(UUID account,long version,String host,String binding) {
        UUID id=UUID.randomUUID();
        var ids=jdbc.query("""
            INSERT INTO platform.mfa_transactions(id,account_id,company_id,hostname,binding_hash,
                account_version,membership_version,company_version,initial_enrollment)
            SELECT ?,a.id,c.id,c.hostname,?,a.security_version,m.security_version,c.security_version,
                NOT EXISTS(SELECT 1 FROM platform.webauthn_credentials w WHERE w.account_id=a.id AND w.company_id=c.id)
                AND NOT EXISTS(SELECT 1 FROM platform.account_totp o WHERE o.account_id=a.id AND o.company_id=c.id AND o.enabled)
                AND NOT m.mfa_enrolled AND COALESCE(pg_catalog.clock_timestamp()<m.mfa_enrollment_until,false)
            FROM platform.accounts a JOIN platform.company_memberships m ON m.account_id=a.id
            JOIN platform.auth_companies c ON c.id=m.company_id
            WHERE a.id=? AND a.security_version=? AND c.hostname=? AND a.status='ACTIVE'
                AND a.email_verified AND m.active AND c.status='ACTIVE' AND c.entitled RETURNING id
            """,(rs,n)->rs.getObject(1,UUID.class),id,binding,account,version,host);
        if(ids.size()!=1)throw new ProtectionFailure();return ids.getFirst();
    }
    @Override public State state(UUID id,String binding,String host,boolean lock) {
        if(lock && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT platform.lock_mfa_transaction_state(?,?,?)",Boolean.class,id,binding,host))) {
            throw new ProtectionFailure();
        }
        var states=jdbc.query("""
            SELECT t.*,m.auth_role,(m.mfa_required OR m.mfa_enrolled) AS member_mfa,c.mfa_required AS company_mfa,
                a.mfa_required AS account_mfa,(a.privileged OR m.privileged) AS hardware,
                (m.totp_allowed AND c.totp_allowed) AS allow_totp
            FROM platform.mfa_transactions t JOIN platform.accounts a ON a.id=t.account_id
            JOIN platform.company_memberships m ON m.account_id=a.id AND m.company_id=t.company_id
            JOIN platform.auth_companies c ON c.id=m.company_id
            WHERE t.id=? AND t.binding_hash=? AND t.hostname=? AND c.hostname=t.hostname
                AND NOT t.completed AND pg_catalog.clock_timestamp()<t.expires_at AND t.attempts<=10
                AND a.status='ACTIVE' AND a.email_verified AND m.active AND c.status='ACTIVE' AND c.entitled
                AND a.security_version=t.account_version AND m.security_version=t.membership_version
                AND c.security_version=t.company_version
                AND (NOT t.initial_enrollment OR t.recovery_only OR pg_catalog.clock_timestamp()<m.mfa_enrollment_until)
            """,(rs,n)->state(rs),id,binding,host);
        if(states.size()!=1)throw new ProtectionFailure();
        if(lock && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT completed AND pg_catalog.clock_timestamp()<expires_at FROM platform.mfa_transactions WHERE id=?",Boolean.class,id)))throw new ProtectionFailure();
        return states.getFirst();
    }
    @Override public void cancel(UUID id,String binding,String host) {
        atomic(() -> {
            var canceled=jdbc.query("UPDATE platform.mfa_transactions SET completed=true WHERE id=? AND binding_hash=? AND hostname=? RETURNING id",(rs,n)->rs.getObject(1,UUID.class),id,binding,host);
            if(!canceled.isEmpty())jdbc.update("UPDATE platform.authentication_sessions SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP) WHERE mfa_transaction_id=?",id);
            return null;
        });
    }
    @Override public void attempt(UUID id,String binding,String host) {
        independent.execute(status -> {
        int updated=jdbc.update("""
            UPDATE platform.mfa_transactions SET attempts=attempts+1
            WHERE id=? AND binding_hash=? AND hostname=? AND NOT completed
                AND pg_catalog.clock_timestamp()<expires_at AND attempts<10
            """,id,binding,host);
        if(updated!=1)throw new ProtectionFailure();
        return null;
        });
    }
    @Override public UUID challenge(State state,String kind,String options) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO platform.mfa_challenges(id,transaction_id,purpose,options_json) VALUES(?,?,?,?)",id,state.id(),kind,options);
        return id;
    }
    @Override public String takeChallenge(UUID id,UUID challenge,String kind,String binding,String host) {
        return independent.execute(status -> {
        var values=jdbc.query("""
            UPDATE platform.mfa_challenges q SET consumed_at=CURRENT_TIMESTAMP
            FROM platform.mfa_transactions t
            WHERE q.id=? AND q.transaction_id=? AND q.purpose=? AND q.consumed_at IS NULL
                AND pg_catalog.clock_timestamp()<q.expires_at AND t.id=q.transaction_id AND t.binding_hash=?
                AND t.hostname=? AND NOT t.completed AND pg_catalog.clock_timestamp()<t.expires_at
            RETURNING q.options_json
            """,(rs,n)->rs.getString(1),challenge,id,kind,binding,host);
        if(values.size()!=1)throw new ProtectionFailure();return values.getFirst();
        });
    }
    @Override public List<Credential> credentials(State state) {
        return jdbc.query("SELECT * FROM platform.webauthn_credentials WHERE account_id=? AND company_id=?",
            (rs,n)->new Credential(rs.getBytes("credential_id"),rs.getBytes("public_key"),rs.getLong("signature_count"),
                rs.getBoolean("uv_initialized"),rs.getBoolean("backup_eligible"),rs.getBoolean("backup_state"),
                rs.getBytes("attestation_object"),rs.getBytes("client_data"),rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_used")==null?null:rs.getTimestamp("last_used").toInstant()),state.accountId(),state.companyId());
    }
    @Override public void saveCredential(State state,Credential c) {
        int updated=jdbc.update("""
            INSERT INTO platform.webauthn_credentials(company_id,account_id,credential_id,public_key,signature_count,
                uv_initialized,backup_eligible,backup_state,attestation_object,client_data,created_at,last_used)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(company_id,credential_id) DO UPDATE SET
                signature_count=EXCLUDED.signature_count,backup_state=EXCLUDED.backup_state,
                uv_initialized=EXCLUDED.uv_initialized,last_used=EXCLUDED.last_used
            WHERE platform.webauthn_credentials.account_id=EXCLUDED.account_id
                AND platform.webauthn_credentials.public_key=EXCLUDED.public_key
            """,state.companyId(),state.accountId(),c.id(),c.publicKey(),c.counter(),c.uv(),c.backupEligible(),
                c.backupState(),c.attestation(),c.clientData(),Timestamp.from(c.created()),c.lastUsed()==null?null:Timestamp.from(c.lastUsed()));
        if(updated!=1)throw new ProtectionFailure();
    }
    @Override public int factorCount(State s) {
        Integer count=jdbc.queryForObject("""
            SELECT (SELECT count(*) FROM platform.webauthn_credentials WHERE account_id=? AND company_id=?)
                 + (SELECT count(*) FROM platform.account_totp WHERE account_id=? AND company_id=? AND enabled AND ?)
            """,Integer.class,s.accountId(),s.companyId(),s.accountId(),s.companyId(),s.policy().totpAllowed());return count==null?0:count;
    }
    @Override public void deleteCredential(State s,byte[] id) {
        int count=id==null?jdbc.update("DELETE FROM platform.account_totp WHERE account_id=? AND company_id=? AND enabled",s.accountId(),s.companyId()):
            jdbc.update("DELETE FROM platform.webauthn_credentials WHERE account_id=? AND company_id=? AND credential_id=?",s.accountId(),s.companyId(),id);
        if(count!=1)throw new ProtectionFailure();
    }
    @Override public String totp(State s,boolean pending) {
        var values=jdbc.query("SELECT "+(pending?"pending_ciphertext":"secret_ciphertext")+" FROM platform.account_totp WHERE account_id=? AND company_id=? AND "+
            (pending?"pending_transaction=?":"enabled") ,(rs,n)->rs.getString(1),pending?new Object[]{s.accountId(),s.companyId(),s.id()}:new Object[]{s.accountId(),s.companyId()});
        if(values.size()!=1||values.getFirst()==null)throw new ProtectionFailure();return values.getFirst();
    }
    @Override public void pendingTotp(State s,String encrypted) {
        jdbc.update("""
            INSERT INTO platform.account_totp(account_id,company_id,pending_ciphertext,pending_transaction) VALUES(?,?,?,?)
            ON CONFLICT(account_id,company_id) DO UPDATE SET pending_ciphertext=EXCLUDED.pending_ciphertext,pending_transaction=EXCLUDED.pending_transaction
            """,s.accountId(),s.companyId(),encrypted,s.id());
    }
    @Override public boolean acceptTotp(State s,long step,boolean enrollment) {
        if(enrollment)return jdbc.update("""
            UPDATE platform.account_totp SET secret_ciphertext=pending_ciphertext,pending_ciphertext=NULL,
                pending_transaction=NULL,enabled=true,last_step=?
            WHERE account_id=? AND company_id=? AND pending_transaction=? AND pending_ciphertext IS NOT NULL AND last_step<?
            """,step,s.accountId(),s.companyId(),s.id(),step)==1;
        return jdbc.update("UPDATE platform.account_totp SET last_step=? WHERE account_id=? AND company_id=? AND enabled AND last_step<?",
            step,s.accountId(),s.companyId(),step)==1;
    }
    @Override public void verified(State s,String method) {
        if(!s.policy().accepts(method))throw new ProtectionFailure();
        jdbc.update("UPDATE platform.mfa_transactions SET verified_method=?,verified_at=CURRENT_TIMESTAMP WHERE id=?",method,s.id());
    }
    @Override public void factorChanged(State s,String method) {
        long version=advance(s);long membershipVersion=s.membershipVersion();
        if(s.enrollment() && !s.recovery()){
            Long updated=jdbc.queryForObject("SELECT platform.consume_mfa_enrollment(?,?)",Long.class,s.accountId(),s.companyId());
            if(updated==null)throw new ProtectionFailure();membershipVersion=updated;
        }
        jdbc.update("UPDATE platform.mfa_transactions SET account_version=?,membership_version=?,initial_enrollment=false,recovery_only=false,verified_method=?,verified_at=? WHERE id=?",
            version,membershipVersion,method,method==null?null:Timestamp.from(Instant.now()),s.id());
    }
    @Override public void recoveryCodes(State s,List<String> hashes) {
        jdbc.update("DELETE FROM platform.recovery_codes WHERE account_id=? AND company_id=?",s.accountId(),s.companyId());
        for(String hash:hashes)jdbc.update("INSERT INTO platform.recovery_codes(account_id,company_id,digest) VALUES(?,?,?)",s.accountId(),s.companyId(),hash);
    }
    @Override public void recover(State s,String hash) {
        if(jdbc.update("UPDATE platform.recovery_codes SET consumed_at=CURRENT_TIMESTAMP WHERE account_id=? AND company_id=? AND digest=? AND consumed_at IS NULL AND pg_catalog.clock_timestamp()<expires_at",s.accountId(),s.companyId(),hash)!=1)throw new ProtectionFailure();
        jdbc.update("DELETE FROM platform.webauthn_credentials WHERE account_id=? AND company_id=?",s.accountId(),s.companyId());
        jdbc.update("DELETE FROM platform.account_totp WHERE account_id=? AND company_id=?",s.accountId(),s.companyId());
        jdbc.update("DELETE FROM platform.recovery_codes WHERE account_id=? AND company_id=?",s.accountId(),s.companyId());
        long version=advance(s);
        jdbc.update("UPDATE platform.mfa_transactions SET account_version=?,initial_enrollment=true,recovery_only=true,verified_method=NULL,verified_at=NULL WHERE id=?",version,s.id());
    }
    @Override public SessionGrant finish(State s) {
        if(!s.policy().accepts(s.verifiedMethod())||s.recovery())throw new ProtectionFailure();
        UUID id=UUID.randomUUID();
        var grants=jdbc.query("""
            INSERT INTO platform.authentication_sessions(id,account_id,company_id,hostname,account_version,
                membership_version,company_version,assurance,mfa_transaction_id,privileged_session)
            VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING created_at
            """,(rs,n)->rs.getTimestamp(1).toInstant(),id,s.accountId(),s.companyId(),s.hostname(),s.accountVersion(),
                s.membershipVersion(),s.companyVersion(),s.verifiedMethod(),s.id(),s.policy().role()!=MfaPolicy.Role.EMPLOYEE);
        jdbc.update("UPDATE platform.mfa_transactions SET completed=true WHERE id=?",s.id());
        return new SessionGrant(id,s.accountId(),s.companyId(),s.hostname(),s.accountVersion(),s.membershipVersion(),s.companyVersion(),grants.getFirst(),s.policy().role()!=MfaPolicy.Role.EMPLOYEE,s.verifiedMethod());
    }
    @Override public void changePassword(State s,String previous,String next) {
        if(jdbc.update("""
            UPDATE platform.accounts a SET password_hash=? WHERE a.id=? AND a.password_hash=? AND NOT a.privileged
                AND NOT EXISTS(SELECT 1 FROM platform.company_memberships m
                    WHERE m.account_id=a.id AND (m.privileged OR m.auth_role='PLATFORM_ADMIN'))
            """,next,s.accountId(),previous)!=1)throw new ProtectionFailure();
        advance(s);
    }
    @Override public void revokeAll(State s) {advance(s);}
    @Override public void requestToken(String email,String host,String purpose,String hash,String delivery) {
        var ids=jdbc.query("""
            INSERT INTO platform.account_tokens(digest,account_id,purpose,hostname,intended_email,account_version,company_id,company_version,membership_version)
            SELECT ?,a.id,?,?,a.normalized_email,a.security_version,c.id,c.security_version,m.security_version
            FROM platform.accounts a LEFT JOIN platform.auth_companies c ON c.hostname=?
            LEFT JOIN platform.company_memberships m ON m.account_id=a.id AND m.company_id=c.id
            WHERE a.normalized_email=? AND a.status<>'DISABLED'
                AND (?='EMAIL_VERIFY' AND NOT a.email_verified OR ?='PASSWORD_RESET' AND a.email_verified OR ?='MFA_ENROLL' AND a.email_verified)
            RETURNING account_id
            """,(rs,n)->rs.getObject(1,UUID.class),hash,purpose,host,host,email,purpose,purpose,purpose);
        if(!ids.isEmpty())jdbc.update("""
            INSERT INTO platform.security_notifications(id,account_id,company_id,kind,encrypted_delivery,delivery_hostname,recipient_email)
            SELECT ?,account_id,company_id,purpose,?,hostname,intended_email FROM platform.account_tokens WHERE digest=?
            """,UUID.randomUUID(),delivery,hash);
    }
    @Override public void resetPassword(String token,String host,String encoded) {consumeToken(token,host,"PASSWORD_RESET",encoded);}
    @Override public void verifyEmail(String token,String host) {consumeToken(token,host,"EMAIL_VERIFY",null);}
    @Override public void approveEnrollment(String token,String host){consumeToken(token,host,"MFA_ENROLL",null);}
    private void consumeToken(String token,String host,String purpose,String encoded) {
        bindCorrelation();
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT platform.consume_account_token(?,?,?,?)",Boolean.class,token,host,purpose,encoded)))throw new ProtectionFailure();
    }
    @Override public void event(UUID account,UUID company,String kind) {
        bindCorrelation();
        jdbc.update("INSERT INTO platform.security_notifications(id,account_id,company_id,kind) VALUES(?,?,?,?)",UUID.randomUUID(),account,company,kind);
    }
    private void bindCorrelation() {
        jdbc.queryForObject("SELECT pg_catalog.set_config('hich.security.correlation_id',?,true)",
            String.class, CorrelationIdContext.current().toString());
    }
    private long advance(State s) {
        Long version=jdbc.queryForObject("SELECT platform.advance_account_security(?,?)",Long.class,s.accountId(),s.accountVersion());
        if(version==null)throw new ProtectionFailure();return version;
    }
    private static State state(ResultSet rs)throws SQLException {
        var policy=new MfaPolicy(MfaPolicy.Role.valueOf(rs.getString("auth_role")),rs.getBoolean("account_mfa"),
            rs.getBoolean("company_mfa"),rs.getBoolean("member_mfa"),rs.getBoolean("hardware"),rs.getBoolean("allow_totp"));
        return new State(rs.getObject("id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("company_id",UUID.class),
            rs.getString("hostname"),rs.getLong("account_version"),rs.getLong("membership_version"),rs.getLong("company_version"),policy,
            rs.getBoolean("initial_enrollment"),rs.getBoolean("recovery_only"),rs.getString("verified_method"),
            rs.getTimestamp("verified_at")==null?null:rs.getTimestamp("verified_at").toInstant());
    }
}
