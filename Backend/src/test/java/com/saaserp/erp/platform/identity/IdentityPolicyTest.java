package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.*;
import com.saaserp.erp.platform.identity.infrastructure.CompromisedPasswordPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityPolicyTest {
    @TempDir Path temp;
    @Test void normalizedIdentityIsStableAndDoesNotFoldProviderAliases() {
        assertThat(new AccountIdentity("  Alice+erp@EXAMPLE.COM ").value()).isEqualTo("alice+erp@example.com");
        for (String bad : new String[] {"", "alice", "a@localhost", "a@bad..com", "a\n@example.com", "a@éxample.com"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AccountIdentity(bad));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountIdentity(null));
    }
    @Test void registrationAlwaysHashesAndNeverGrantsAuthorityEvenForDuplicates() {
        var repository = mock(IdentityRepository.class);
        var service = new RegistrationService(repository, p -> {}, p -> "encoded");
        service.register(" Alice@Example.com ", "correct long passphrase");
        service.register("alice@example.com", "correct long passphrase");
        verify(repository, times(2)).registerPending(any(UUID.class), eq("alice@example.com"), eq("encoded"));
        verifyNoMoreInteractions(repository);
    }
    @Test void failedValidationDoesNotPersistAnAccount() {
        var repository = mock(IdentityRepository.class);
        var service = new RegistrationService(repository, p -> { throw new IllegalArgumentException(); }, p -> "hash");
        assertThatIllegalArgumentException().isThrownBy(() -> service.register("a@example.com", "short"));
        verifyNoInteractions(repository);
    }
    @Test void screeningIsRequiredAndRejectsCompromisedPasswords() throws Exception {
        assertThatIllegalStateException().isThrownBy(() -> new CompromisedPasswordPolicy("").validate("long enough password"));
        String bad = "known compromised phrase";
        Path file = temp.resolve("blocklist");
        Files.writeString(file, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var policy = new CompromisedPasswordPolicy(file.toString());
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(bad));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate("too short"));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate("x".repeat(129)));
        policy.validate("a sufficiently long unique phrase");
    }
    @Test void ordinaryAndPrivilegedDeadlinesRejectAtExactBoundaries() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (boolean privileged : new boolean[] {false, true}) {
            var grant = new SessionGrant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a.test", 1, 1, 1, start, privileged);
            assertThat(grant.expired(start.plusSeconds(1), start)).isFalse();
            assertThat(grant.expired(start.plus(grant.idleLimit()), start)).isTrue();
            assertThat(grant.expired(start.plus(grant.absoluteLimit()), start.plus(grant.absoluteLimit()))).isTrue();
            assertThat(grant.idleLimit().toMinutes()).isEqualTo(privileged ? 15 : 30);
            assertThat(grant.absoluteLimit().toHours()).isEqualTo(privileged ? 8 : 12);
        }
    }
}
