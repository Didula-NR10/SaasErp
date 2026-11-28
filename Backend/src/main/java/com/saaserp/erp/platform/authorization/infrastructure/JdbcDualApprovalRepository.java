package com.saaserp.erp.platform.authorization.infrastructure;

import com.saaserp.erp.platform.audit.application.CorrelationIdContext;
import com.saaserp.erp.platform.authorization.application.DualApprovalRepository;
import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcDualApprovalRepository implements DualApprovalRepository {
    private final JdbcTemplate jdbc;

    public JdbcDualApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public Optional<Approval> findByIdempotency(SessionGrant requester, String digest) {
        return jdbc.query("""
            SELECT * FROM platform.dual_approvals
            WHERE company_id=? AND requester_id=? AND idempotency_digest=?
            """, JdbcDualApprovalRepository::approval, requester.companyId(), requester.accountId(), digest)
            .stream().findFirst();
    }

    @Override public Optional<Approval> find(UUID id) {
        return jdbc.query("SELECT * FROM platform.dual_approvals WHERE id=?",
            JdbcDualApprovalRepository::approval, id).stream().findFirst();
    }

    @Override public UUID request(SessionGrant requester, OperationIntent intent, String detailsDigest,
            String idempotencyDigest, UUID stepUpId) {
        UUID id = UUID.randomUUID();
        UUID created = jdbc.queryForObject("""
            SELECT platform.create_dual_approval(?,?,?,?,?,?,?,?,?,?,?,?)
            """, UUID.class, id, requester.companyId(), requester.hostname(), requester.accountId(),
            requester.id(), intent.permission().value(), intent.target().name(), intent.targetId(),
            detailsDigest, idempotencyDigest, stepUpId, CorrelationIdContext.current());
        if (created == null) throw new IllegalStateException("Approval request unavailable");
        return created;
    }

    @Override public boolean approve(SessionGrant approver, UUID id, String digest, UUID stepUpId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.approve_dual_approval(?,?,?,?,?,?,?,?)
            """, Boolean.class, id, approver.id(), approver.accountId(), approver.companyId(),
            approver.hostname(), digest, stepUpId, CorrelationIdContext.current()));
    }

    @Override public boolean cancel(SessionGrant requester, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.cancel_dual_approval(?,?,?,?,?,?)
            """, Boolean.class, id, requester.id(), requester.accountId(),
            requester.companyId(), requester.hostname(), CorrelationIdContext.current()));
    }

    @Override public boolean expire(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.expire_dual_approval(?,?)
            """, Boolean.class, id, CorrelationIdContext.current()));
    }

    @Override public boolean consume(SessionGrant requester, UUID id, OperationIntent intent, String digest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT platform.consume_dual_approval(?,?,?,?,?,?,?,?,?,?)
            """, Boolean.class, id, requester.id(), requester.accountId(), requester.companyId(),
            requester.hostname(), intent.permission().value(), intent.target().name(), intent.targetId(), digest,
            CorrelationIdContext.current()));
    }

    private static Approval approval(ResultSet rs, int row) throws SQLException {
        return new Approval(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class),
            rs.getString("hostname"), rs.getObject("requester_id", UUID.class),
            rs.getObject("approver_id", UUID.class), rs.getString("permission_code"),
            rs.getString("target_type"), rs.getString("target_id"), rs.getString("details_digest"),
            Status.valueOf(rs.getString("status")), rs.getTimestamp("expires_at").toInstant());
    }
}
