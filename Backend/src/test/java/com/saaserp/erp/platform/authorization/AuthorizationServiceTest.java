package com.saaserp.erp.platform.authorization;

import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.authorization.application.AuthorizationRepository;
import com.saaserp.erp.platform.authorization.application.AuthorizationService;
import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationServiceTest {
    private final AuthorizationRepository repository = mock(AuthorizationRepository.class);
    private final SecurityAudit audit = mock(SecurityAudit.class);
    private final AuthorizationService service = new AuthorizationService(repository, audit);
    private final UUID company = UUID.randomUUID();
    private final SessionGrant grant = new SessionGrant(UUID.randomUUID(), UUID.randomUUID(), company,
        "tenant.test", 1, 1, 1, Instant.now(), false, "WEBAUTHN");

    @Test void companyScopeCannotBeOverriddenByARequestedTenantIdentifier() {
        assertThatThrownBy(() -> service.requireCompany(grant, UUID.randomUUID(),
            new Permission("erp.sales.read"))).isInstanceOf(AuthorizationService.AuthorizationDenied.class);

        verifyNoInteractions(repository);
        verify(audit).record(argThat(event -> event.actorId().equals(grant.accountId())
            && event.companyId().equals(company)
            && event.action().name().equals("AUTHORIZATION_DENIED")));
    }

    @Test void currentPermissionDecisionIsReadFromRepositoryAndFieldsAreAllowlisted() {
        when(repository.permitsCompany(grant, company, "erp.customer.name.read")).thenReturn(true);
        when(repository.permitsCompany(grant, company, "erp.customer.secret.read")).thenReturn(false);
        var filtered = service.filterReadableFields(grant, company, "erp.customer",
            Map.of("name", "Customer", "secret", "private"));

        assertThat(filtered.get("name")).isEqualTo("Customer");
        assertThat(filtered).doesNotContainKey("secret");
        verify(repository).permitsCompany(grant, company, "erp.customer.name.read");
        verify(repository).permitsCompany(grant, company, "erp.customer.secret.read");
    }
}
