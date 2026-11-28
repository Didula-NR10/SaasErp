package com.saaserp.erp.platform.authorization;

import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.authorization.application.*;
import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.authorization.domain.OperationIntent.Target;
import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DualApprovalServiceTest {
    private final AuthorizationRepository authorizations = mock(AuthorizationRepository.class);
    private final DualApprovalRepository approvals = mock(DualApprovalRepository.class);
    private final SecurityAudit audit = mock(SecurityAudit.class);
    private final AuthorizationService authorization = new AuthorizationService(authorizations, audit);
    private final DualApprovalService service = new DualApprovalService(authorization, approvals, audit);
    private final UUID company = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final SessionGrant approver = new SessionGrant(UUID.randomUUID(), UUID.randomUUID(), company,
        "tenant.test", 1, 1, 1, Instant.now(), false, "WEBAUTHN");
    private final OperationIntent intent = new OperationIntent(new Permission("erp.sales.refund"),
        Target.RESOURCE, "refund:42", Map.of("amount", "25.00", "currency", "USD"));

    @Test void selfApprovalAndModifiedTransactionDetailsAreRejectedBeforeApproval() {
        var self = approval(requesterId, intent);
        when(authorizations.permitsCompany(any(), eq(company), eq(intent.permission().value()))).thenReturn(true);
        when(approvals.find(self.id())).thenReturn(Optional.of(self));
        assertThatThrownBy(() -> service.approve(withAccount(requesterId), self.id(), intent, UUID.randomUUID()))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        verify(approvals, never()).approve(any(), any(), any(), any());

        var other = approval(UUID.randomUUID(), intent);
        var modified = new OperationIntent(intent.permission(), intent.target(), intent.targetId(),
            Map.of("amount", "25.01", "currency", "USD"));
        when(approvals.find(other.id())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.approve(approver, other.id(), modified, UUID.randomUUID()))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        verify(approvals, never()).approve(any(), any(), any(), any());
    }

    @Test void idempotencyKeyCannotBeReusedForDifferentTransactionDetails() {
        var original = approval(requesterId, intent);
        when(authorizations.permitsCompany(any(), eq(company), eq(intent.permission().value()))).thenReturn(true);
        when(approvals.findByIdempotency(eq(approver), anyString())).thenReturn(Optional.of(original));
        var changed = new OperationIntent(intent.permission(), intent.target(), intent.targetId(),
            Map.of("amount", "26.00", "currency", "USD"));

        assertThatThrownBy(() -> service.request(approver, changed, "a-valid-idempotency-key", UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);
        verify(approvals, never()).request(any(), any(), anyString(), anyString(), any());
    }

    private DualApprovalRepository.Approval approval(UUID requester, OperationIntent value) {
        return new DualApprovalRepository.Approval(UUID.randomUUID(), company, "tenant.test", requester, null,
            value.permission().value(), value.target().name(), value.targetId(), value.digest(),
            DualApprovalRepository.Status.REQUESTED, Instant.now().plusSeconds(60));
    }

    private SessionGrant withAccount(UUID account) {
        return new SessionGrant(approver.id(), account, company, approver.hostname(), 1, 1, 1,
            approver.issuedAt(), false, "WEBAUTHN");
    }
}
