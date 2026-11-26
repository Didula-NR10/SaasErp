package com.saaserp.erp.shared.tenant.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantIdTest {

    private static final String CANONICAL = "12345678-1234-abcd-9876-123456789abc";

    @Test
    void acceptsUuid() {
        UUID uuid = UUID.fromString(CANONICAL);
        assertThat(new TenantId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void parsesCanonicalUuid() {
        assertThat(TenantId.parse(CANONICAL)).isEqualTo(new TenantId(UUID.fromString(CANONICAL)));
    }

    @Test
    void rejectsNullUuid() {
        assertThrows(NullPointerException.class, () -> new TenantId(null));
    }

    @Test
    void rejectsNullString() {
        assertThrows(NullPointerException.class, () -> TenantId.parse(null));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> TenantId.parse(""));
    }

    @Test
    void rejectsMalformedAndNonIdentityInputs() {
        for (String input : new String[] {"not-a-uuid", "Acme", "tenant_schema", "user@example.com",
                "12345678-1234-abcd-9876-123456789abg"}) {
            var failure = assertThrows(IllegalArgumentException.class, () -> TenantId.parse(input));
            assertThat(failure.getMessage()).isEqualTo("Tenant identity must be a canonical UUID");
        }
    }

    @Test
    void rejectsNoncanonicalRepresentations() {
        for (String input : new String[] {"1-1-1-1-1", CANONICAL.toUpperCase(java.util.Locale.ROOT),
                " " + CANONICAL, CANONICAL + " ", CANONICAL + "\n", CANONICAL.replace("-", ""),
                "{" + CANONICAL + "}", "urn:uuid:" + CANONICAL}) {
            assertThrows(IllegalArgumentException.class, () -> TenantId.parse(input));
        }
    }

    @Test
    void hasValueEqualityAndHashCode() {
        TenantId first = TenantId.parse(CANONICAL);
        TenantId second = new TenantId(UUID.fromString(CANONICAL));
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(TenantId.parse("22345678-1234-abcd-9876-123456789abc"));
    }

    @Test
    void rendersCanonicalString() {
        assertThat(new TenantId(UUID.fromString(CANONICAL.toUpperCase(java.util.Locale.ROOT))).toString())
                .isEqualTo(CANONICAL);
    }
}
