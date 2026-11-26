package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantIdentifierResolverTest {

    private static final TenantId FIRST = TenantId.parse("550e8400-e29b-41d4-a716-446655440000");
    private static final TenantId SECOND = TenantId.parse("550e8400-e29b-41d4-a716-446655440001");
    private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

    @BeforeEach
    @AfterEach
    void contextIsEmpty() {
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    @Test
    void resolvesActiveIdentityAsCanonicalUuid() {
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(FIRST.toString());
        }
    }

    @Test
    void missingContextFailsWithoutCentralFallback() {
        assertThrows(IllegalStateException.class, resolver::resolveCurrentTenantIdentifier);
    }

    @Test
    void cleanedUpContextCannotBeReused() {
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(FIRST.toString());
        }
        assertThrows(IllegalStateException.class, resolver::resolveCurrentTenantIdentifier);
    }

    @Test
    void distinctScopesResolveDistinctIdentitiesWithoutStaleFallback() {
        try (var scope = TenantContextHolder.bind(new TenantContext(FIRST))) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(FIRST.toString());
        }
        try (var scope = TenantContextHolder.bind(new TenantContext(SECOND))) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(SECOND.toString());
        }
    }

    @Test
    void requestsHibernateValidationOfExistingCurrentSessions() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }

    @Test
    void neverDeclaresRootTenantsOrAdministratorBypass() {
        for (String input : new String[] {FIRST.toString(), "public", "platform", "billing", "root", null}) {
            assertThat(resolver.isRoot(input)).isFalse();
        }
    }
}
