package com.saaserp.erp.platform.audit.infrastructure;

import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcSecurityAudit implements SecurityAudit {
    private final JdbcTemplate jdbc;

    public JdbcSecurityAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public void record(SecurityAuditEvent event) {
        jdbc.queryForObject("""
            SELECT platform.record_security_audit(?,?,?,?,?,?,?)
            """, java.util.UUID.class, event.actorId(), event.companyId(), event.action().name(),
            event.target().name(), event.targetId(), event.outcome().name(), CorrelationIdContext.current());
    }
}
