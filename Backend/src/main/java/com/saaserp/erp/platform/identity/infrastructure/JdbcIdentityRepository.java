package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.IdentityRepository;
import com.saaserp.erp.platform.identity.domain.Account;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Central tables are fully qualified: never rely on or mutate tenant search_path. */
public final class JdbcIdentityRepository implements IdentityRepository {
    private final JdbcTemplate jdbc;
    public JdbcIdentityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Account> findAccount(String email) {
        return jdbc.query("SELECT * FROM platform.accounts WHERE normalized_email = ?",
            (rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("normalized_email"),
                rs.getString("password_hash"), rs.getString("status"), rs.getBoolean("email_verified"),
                rs.getBoolean("mfa_required"), rs.getBoolean("privileged"), rs.getLong("security_version")), email)
            .stream().findFirst();
    }
    @Override public boolean registerPending(UUID id, String email, String hash) {
        return jdbc.update("""
            INSERT INTO platform.accounts (id, normalized_email, password_hash)
            VALUES (?, ?, ?) ON CONFLICT (normalized_email) DO NOTHING
            """, id, email, hash) == 1;
    }
    @Override public boolean upgradePassword(UUID id, String previous, String encoded) {
        return jdbc.update("UPDATE platform.accounts SET password_hash = ? WHERE id = ? AND password_hash = ?",
            encoded, id, previous) == 1;
    }
    @Override public Optional<SessionGrant> createGrant(UUID accountId, long accountVersion, String hostname) {
        // A single writer statement creates authority only from current permitted rows.
        return jdbc.query("""
            INSERT INTO platform.authentication_sessions
                (id, account_id, company_id, hostname, account_version, membership_version, company_version)
            SELECT ?, a.id, c.id, c.hostname, a.security_version, m.security_version, c.security_version
            FROM platform.accounts a
            JOIN platform.company_memberships m ON m.account_id = a.id
            JOIN platform.auth_companies c ON c.id = m.company_id
            WHERE a.id = ? AND a.security_version = ? AND c.hostname = ?
                AND a.status = 'ACTIVE' AND a.email_verified AND NOT a.mfa_required AND NOT a.privileged
                AND m.active AND NOT m.mfa_required AND NOT m.mfa_enrolled AND NOT m.privileged AND m.auth_role='EMPLOYEE'
                AND c.status = 'ACTIVE' AND c.entitled AND NOT c.mfa_required
                AND NOT EXISTS(SELECT 1 FROM platform.webauthn_credentials w WHERE w.account_id=a.id AND w.company_id=c.id)
                AND NOT EXISTS(SELECT 1 FROM platform.account_totp o WHERE o.account_id=a.id AND o.company_id=c.id AND o.enabled)
            RETURNING *
            """, (rs, n) -> grant(rs), UUID.randomUUID(), accountId, accountVersion, hostname).stream().findFirst();
    }
    @Override public boolean isCurrent(SessionGrant g) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM platform.authentication_sessions s
                JOIN platform.accounts a ON a.id = s.account_id
                JOIN platform.auth_companies c ON c.id = s.company_id
                JOIN platform.company_memberships m ON m.account_id = a.id AND m.company_id = c.id
                WHERE s.id = ? AND s.account_id = ? AND s.company_id = ? AND s.hostname = ?
                    AND c.hostname = s.hostname AND s.revoked_at IS NULL
                    AND CURRENT_TIMESTAMP < s.created_at + CASE WHEN s.privileged_session THEN INTERVAL '8 hours' ELSE INTERVAL '12 hours' END
                    AND s.privileged_session=?
                    AND a.status = 'ACTIVE' AND a.email_verified AND NOT a.privileged
                    AND m.active AND NOT m.privileged AND m.auth_role<>'PLATFORM_ADMIN'
                    AND c.status = 'ACTIVE' AND c.entitled AND s.assurance=?
                    AND (
                        (s.assurance='PASSWORD' AND NOT a.mfa_required AND NOT m.mfa_required AND NOT m.mfa_enrolled AND NOT c.mfa_required
                            AND m.auth_role='EMPLOYEE'
                            AND NOT EXISTS(SELECT 1 FROM platform.webauthn_credentials w WHERE w.account_id=a.id AND w.company_id=c.id)
                            AND NOT EXISTS(SELECT 1 FROM platform.account_totp o WHERE o.account_id=a.id AND o.company_id=c.id AND o.enabled))
                        OR (s.assurance='WEBAUTHN' AND EXISTS(SELECT 1 FROM platform.webauthn_credentials w WHERE w.account_id=a.id AND w.company_id=c.id))
                        OR (s.assurance='TOTP' AND c.totp_allowed AND m.totp_allowed
                            AND EXISTS(SELECT 1 FROM platform.account_totp o WHERE o.account_id=a.id AND o.company_id=c.id AND o.enabled))
                    )
                    AND a.security_version = s.account_version AND a.security_version = ?
                    AND m.security_version = s.membership_version AND m.security_version = ?
                    AND c.security_version = s.company_version AND c.security_version = ?)
            """, Boolean.class, g.id(), g.accountId(), g.companyId(), g.hostname(), g.privileged(), g.assurance(),
                g.accountVersion(), g.membershipVersion(), g.companyVersion()));
    }
    @Override public void revoke(UUID id) {
        jdbc.update("UPDATE platform.authentication_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP) WHERE id = ?", id);
    }
    private static SessionGrant grant(ResultSet rs) throws SQLException {
        return new SessionGrant(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
            rs.getObject("company_id", UUID.class), rs.getString("hostname"), rs.getLong("account_version"),
            rs.getLong("membership_version"), rs.getLong("company_version"),
            rs.getTimestamp("created_at").toInstant(), rs.getBoolean("privileged_session"), rs.getString("assurance"));
    }
}
