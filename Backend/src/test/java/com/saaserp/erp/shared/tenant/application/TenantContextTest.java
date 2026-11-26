package com.saaserp.erp.shared.tenant.application;

import com.saaserp.erp.shared.tenant.domain.TenantId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextTest {

    private static final TenantId TENANT = TenantId.parse("12345678-1234-abcd-9876-123456789abc");

    @Test
    void containsCompanyIdentity() {
        assertThat(new TenantContext(TENANT).tenantId()).isEqualTo(TENANT);
    }

    @Test
    void rejectsNullIdentity() {
        assertThrows(NullPointerException.class, () -> new TenantContext(null));
    }

    @Test
    void hasValueEqualityAndHashCode() {
        TenantContext first = new TenantContext(TENANT);
        TenantContext second = new TenantContext(TenantId.parse(TENANT.toString()));
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(new TenantContext(
                TenantId.parse("22345678-1234-abcd-9876-123456789abc")));
    }
}
