package com.saaserp.erp.shared.tenant.domain;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantSchemaIdentifierTest {

    private static final String UUID_TEXT = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SCHEMA = "tenant_550e8400e29b41d4a716446655440000";
    private static final TenantId TENANT = TenantId.parse(UUID_TEXT);
    private static final String[] SAMPLE_UUIDS = {
            UUID_TEXT,
            "00000000-0000-0000-0000-000000000000",
            "00000001-0002-0003-0004-000000000005",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
            "80000000-0000-0000-8000-000000000000"
    };

    @Test
    void knownUuidProducesExactIdentifier() {
        assertThat(TenantSchemaIdentifier.from(TENANT).value()).isEqualTo(SCHEMA);
    }

    @Test
    void repeatedGenerationIsDeterministic() {
        TenantSchemaIdentifier expected = TenantSchemaIdentifier.from(TENANT);
        for (int iteration = 0; iteration < 100; iteration++) {
            assertThat(TenantSchemaIdentifier.from(TENANT)).isEqualTo(expected);
        }
    }

    @Test
    void differentUuidsProduceDifferentIdentifiers() {
        TenantId other = TenantId.parse("550e8400-e29b-41d4-a716-446655440001");
        assertThat(TenantSchemaIdentifier.from(TENANT))
                .isNotEqualTo(TenantSchemaIdentifier.from(other));
    }

    @Test
    void preservesLeadingZerosInEveryUuidGroup() {
        TenantId tenant = TenantId.parse("00000001-0002-0003-0004-000000000005");
        assertThat(TenantSchemaIdentifier.from(tenant).value())
                .isEqualTo("tenant_00000001000200030004000000000005");
    }

    @Test
    void supportsAllZeroUuidWithoutSubstitution() {
        assertThat(TenantSchemaIdentifier.from(new TenantId(new UUID(0L, 0L))).value())
                .isEqualTo("tenant_00000000000000000000000000000000");
    }

    @Test
    void supportsMaximumUuidBitPatternWithoutTruncation() {
        assertThat(TenantSchemaIdentifier.from(new TenantId(new UUID(-1L, -1L))).value())
                .isEqualTo("tenant_ffffffffffffffffffffffffffffffff");
    }

    @Test
    void generatedIdentifiersHaveExactly39AsciiCharacters() {
        for (String uuid : SAMPLE_UUIDS) {
            String identifier = TenantSchemaIdentifier.from(TenantId.parse(uuid)).value();
            assertThat(identifier).hasSize(39);
            assertThat(identifier.chars().allMatch(character -> character <= 0x7f)).isTrue();
        }
    }

    @Test
    void generatedIdentifiersAre39BytesWithinPostgresqlLimit() {
        for (String uuid : SAMPLE_UUIDS) {
            byte[] bytes = TenantSchemaIdentifier.from(TenantId.parse(uuid)).value()
                    .getBytes(StandardCharsets.UTF_8);
            assertThat(bytes).hasSize(39);
            assertThat(bytes.length).isLessThanOrEqualTo(63);
        }
    }

    @Test
    void generatedIdentifiersMatchStrictFullStringPattern() {
        Pattern canonical = Pattern.compile("\\Atenant_[0-9a-f]{32}\\z");
        for (String uuid : SAMPLE_UUIDS) {
            String identifier = TenantSchemaIdentifier.from(TenantId.parse(uuid)).value();
            assertThat(canonical.matcher(identifier).matches()).isTrue();
        }
    }

    @Test
    void equalValuesHaveEqualHashCodesAndWorkAsSetKeys() {
        TenantSchemaIdentifier first = TenantSchemaIdentifier.from(TENANT);
        TenantSchemaIdentifier second = TenantSchemaIdentifier.from(TenantId.parse(UUID_TEXT));
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(new HashSet<>(java.util.List.of(first, second))).hasSize(1);
    }

    @Test
    void equalityRejectsNullAndUnrelatedTypes() {
        TenantSchemaIdentifier identifier = TenantSchemaIdentifier.from(TENANT);
        assertThat(identifier.equals(identifier)).isTrue();
        assertThat(identifier.equals(null)).isFalse();
        assertThat(identifier.equals(SCHEMA)).isFalse();
    }

    @Test
    void stringRepresentationIsCanonicalValue() {
        TenantSchemaIdentifier identifier = TenantSchemaIdentifier.from(TENANT);
        assertThat(identifier.toString()).isEqualTo(SCHEMA).isEqualTo(identifier.value());
    }

    @Test
    void rejectsNullTenantWithoutFallback() {
        var failure = assertThrows(NullPointerException.class,
                () -> TenantSchemaIdentifier.from(null));
        assertThat(failure.getMessage()).isEqualTo("Tenant identity is required");
    }

    @Test
    void everyUuidBitIsPreservedAndChangesTheIdentifier() {
        Set<TenantSchemaIdentifier> identifiers = new HashSet<>();
        identifiers.add(TenantSchemaIdentifier.from(new TenantId(new UUID(0L, 0L))));
        for (int bit = 0; bit < 128; bit++) {
            UUID uuid = bit < 64 ? new UUID(1L << bit, 0L) : new UUID(0L, 1L << (bit - 64));
            TenantSchemaIdentifier identifier = TenantSchemaIdentifier.from(new TenantId(uuid));
            String hex = identifier.value().substring("tenant_".length());
            String reconstructed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            assertThat(UUID.fromString(reconstructed)).isEqualTo(uuid);
            assertThat(identifiers.add(identifier)).isTrue();
        }
        assertThat(identifiers).hasSize(129);
    }

    @Test
    void generationDoesNotMutateOtherValuesOrOriginalIdentity() {
        TenantSchemaIdentifier first = TenantSchemaIdentifier.from(TENANT);
        TenantSchemaIdentifier other = TenantSchemaIdentifier.from(new TenantId(new UUID(-1L, -1L)));
        assertThat(first.value()).isEqualTo(SCHEMA);
        assertThat(TENANT.value()).isEqualTo(UUID.fromString(UUID_TEXT));
        assertThat(other.value()).isEqualTo("tenant_ffffffffffffffffffffffffffffffff");
        assertThat(TenantSchemaIdentifier.from(TENANT)).isEqualTo(first);
    }

    @Test
    void acceptsExistingUuidBasedTenantIdRegardlessOfUuidInputCase() {
        TenantId tenant = new TenantId(UUID.fromString("550E8400-E29B-41D4-A716-446655440000"));
        assertThat(TenantSchemaIdentifier.from(tenant).value()).isEqualTo(SCHEMA);
    }

    @Test
    void constructionApiCannotAcceptRawSchemaStringsOrUnvalidatedValues() {
        assertThat(Modifier.isFinal(TenantSchemaIdentifier.class.getModifiers())).isTrue();
        assertThat(TenantSchemaIdentifier.class.getConstructors()).isEmpty();
        assertThat(TenantSchemaIdentifier.class.getDeclaredConstructors()).allSatisfy(constructor -> {
            assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
            assertThat(constructor.getParameterTypes()).containsExactly(TenantId.class);
        });
        assertThat(TenantSchemaIdentifier.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers()))
                .singleElement().satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("from");
                    assertThat(method.getParameterTypes()).containsExactly(TenantId.class);
                });
    }
}
