package com.saaserp.erp.platform.authorization.infrastructure;

import com.saaserp.erp.platform.authorization.application.StepUpRepository;
import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcStepUpRepository implements StepUpRepository {
    private final JdbcTemplate jdbc;

    public JdbcStepUpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public UUID create(SessionGrant grant, OperationIntent intent, String digest, String options) {
        UUID id = UUID.randomUUID();
        boolean created = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.create_step_up_authorization(?,?,?,?,?,?,?,?,?,?,?)
            """, Boolean.class, id, grant.id(), grant.accountId(), grant.companyId(), grant.hostname(),
            intent.permission().value(), intent.target().name(), intent.targetId(), digest, options,
            CorrelationIdContext.current()));
        if (!created) throw new IllegalStateException("Current authorization unavailable");
        return id;
    }

    @Override public String burnChallenge(SessionGrant grant, UUID id) {
        String options = jdbc.queryForObject("""
            SELECT platform.burn_step_up_challenge(?,?,?,?,?)
            """, String.class, id, grant.id(), grant.accountId(), grant.companyId(), grant.hostname());
        return options;
    }

    @Override public boolean complete(SessionGrant grant, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.complete_step_up(?,?,?,?,?)
            """, Boolean.class, id, grant.id(), grant.accountId(), grant.companyId(), grant.hostname()));
    }

    @Override public boolean consume(SessionGrant grant, UUID id, OperationIntent intent, String digest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.consume_step_up(?,?,?,?,?,?,?,?,?)
            """, Boolean.class, id, grant.id(), grant.accountId(), grant.companyId(), grant.hostname(),
            intent.permission().value(), intent.target().name(), intent.targetId(), digest));
    }
}
