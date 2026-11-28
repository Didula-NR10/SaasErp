package com.saaserp.erp.platform.authorization.infrastructure;

import com.saaserp.erp.platform.authorization.application.PlatformAuthorizationRepository;
import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.authorization.domain.PlatformSessionContext;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcPlatformAuthorizationRepository implements PlatformAuthorizationRepository {
    private final JdbcTemplate jdbc;

    public JdbcPlatformAuthorizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public boolean permitsPlatform(PlatformSessionContext session, Permission permission) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.authorize_platform_session(?,?,?,?,?)
            """, Boolean.class, session.sessionId(), session.accountId(), session.sessionBindingDigest(),
            session.hostname(), permission.value()));
    }
}
