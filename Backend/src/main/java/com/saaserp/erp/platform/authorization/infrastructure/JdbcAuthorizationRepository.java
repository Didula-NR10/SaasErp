package com.saaserp.erp.platform.authorization.infrastructure;

import com.saaserp.erp.platform.authorization.application.AuthorizationRepository;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAuthorizationRepository implements AuthorizationRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuthorizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public boolean permitsCompany(SessionGrant grant, UUID company, String permission) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.authorize_company_session(?,?,?,?,?)
            """, Boolean.class, grant.id(), grant.accountId(), company, grant.hostname(), permission));
    }
}
