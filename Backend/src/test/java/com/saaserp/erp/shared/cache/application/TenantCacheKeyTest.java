package com.saaserp.erp.shared.cache.application;

import java.lang.reflect.Modifier;
import java.util.UUID;

import com.saaserp.erp.shared.tenant.application.TenantContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantCacheKeyTest {

    private final TenantContext first = new TenantContext(
            TenantId.parse("12345678-1234-abcd-9876-123456789abc"));
    private final TenantContext second = new TenantContext(new TenantId(UUID.randomUUID()));

    @Test
    void deterministicCanonicalKeyContainsTheActiveInternalUuid() {
        try (var scope = TenantContextHolder.bind(first)) {
            assertThat(TenantCacheKey.current("inventory", "item_123"))
                    .isEqualTo("hich:tenant:12345678-1234-abcd-9876-123456789abc:inventory:item_123")
                    .isEqualTo(TenantCacheKey.current("inventory", "item_123"));
        }
    }

    @Test
    void identicalLogicalKeysOfDifferentTenantsCannotCollide() {
        String key;
        try (var scope = TenantContextHolder.bind(first)) {
            key = TenantCacheKey.current("catalog", "item");
        }
        try (var scope = TenantContextHolder.bind(second)) {
            assertThat(TenantCacheKey.current("catalog", "item")).isNotEqualTo(key);
        }
    }

    @Test
    void namespacesAndLogicalKeysRemainDistinct() {
        try (var scope = TenantContextHolder.bind(first)) {
            assertThat(TenantCacheKey.current("ab", "c"))
                    .isNotEqualTo(TenantCacheKey.current("a", "bc"));
            assertThat(TenantCacheKey.current("a", "item"))
                    .isNotEqualTo(TenantCacheKey.current("b", "item"));
        }
    }

    @Test
    void missingContextNeverFallsBack() {
        assertThrows(IllegalStateException.class, () -> TenantCacheKey.current("catalog", "item"));
    }

    @Test
    void closingScopeRemovesAuthorityToGenerateKeys() {
        try (var scope = TenantContextHolder.bind(first)) {
            TenantCacheKey.current("catalog", "item");
        }
        assertThat(TenantContextHolder.current()).isEmpty();
        assertThrows(IllegalStateException.class, () -> TenantCacheKey.current("catalog", "item"));
    }

    @Test
    void nullComponentsAreRejectedWithoutEchoingInput() {
        try (var scope = TenantContextHolder.bind(first)) {
            assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current(null, "item"));
            assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current("catalog", null));
        }
    }

    @Test
    void malformedNamespacesAreRejectedRatherThanNormalized() {
        try (var scope = TenantContextHolder.bind(first)) {
            for (String value : new String[] {"", "Catalog", "1catalog", " catalog", "catalog ", "café", "a.b"}) {
                assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current(value, "item"));
            }
        }
    }

    @Test
    void malformedLogicalKeysAreRejectedRatherThanNormalized() {
        try (var scope = TenantContextHolder.bind(first)) {
            for (String value : new String[] {"", " item", "item ", "item name", "é", "-item", "a/b", "a.b"}) {
                assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current("catalog", value));
            }
        }
    }

    @Test
    void delimitersAndRedisHashTagsCannotEscapeEitherComponent() {
        try (var scope = TenantContextHolder.bind(first)) {
            for (String value : new String[] {"a:b", "hich:platform", "a{b}", "{tenant}", "a\\b"}) {
                assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current(value, "item"));
                assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current("catalog", value));
            }
        }
    }

    @Test
    void wildcardsAndControlCharactersAreRejected() {
        try (var scope = TenantContextHolder.bind(first)) {
            for (String value : new String[] {"a*", "a?", "a[b]", "a\n", "a\r", "a\t", "a" + (char) 0, "a" + (char) 127}) {
                assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current(value, "item"));
                assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current("catalog", value));
            }
        }
    }

    @Test
    void minimumAndMaximumComponentLengthsAreAccepted() {
        try (var scope = TenantContextHolder.bind(first)) {
            assertThat(TenantCacheKey.current("a", "0")).endsWith(":a:0");
            assertThat(TenantCacheKey.current("a".repeat(64), "K".repeat(128)))
                    .hasSize(242).endsWith(":" + "a".repeat(64) + ":" + "K".repeat(128));
        }
    }

    @Test
    void componentsBeyondTheirLimitsAreRejected() {
        try (var scope = TenantContextHolder.bind(first)) {
            assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current("a".repeat(65), "item"));
            assertThrows(IllegalArgumentException.class, () -> TenantCacheKey.current("catalog", "k".repeat(129)));
        }
    }

    @Test
    void logicalKeyCaseIsPreservedAndNoArbitraryTenantOverloadExists() {
        try (var scope = TenantContextHolder.bind(first)) {
            assertThat(TenantCacheKey.current("catalog", "Item"))
                    .isNotEqualTo(TenantCacheKey.current("catalog", "item"));
        }
        assertThat(TenantCacheKey.class.getConstructors()).isEmpty();
        var publicMethods = java.util.Arrays.stream(TenantCacheKey.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertThat(publicMethods).hasSize(1);
        assertThat(publicMethods.getFirst().getParameterTypes()).containsExactly(String.class, String.class);
    }
}
