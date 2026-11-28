package com.saaserp.erp.config;

import com.saaserp.erp.platform.identity.application.IdentityRepository;
import com.saaserp.erp.platform.identity.domain.*;
import com.saaserp.erp.platform.identity.infrastructure.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyAuthenticationProviderTest {
    private final AuthenticationConfig config = new AuthenticationConfig();
    @Test void argon2idUsesDistinctSaltsAndVerifiesOnlyCorrectPasswords() {
        var encoder = config.passwordEncoder();
        String first = encoder.encode("a sufficiently long password");
        assertThat(first).startsWith("$argon2id$").isNotEqualTo(encoder.encode("a sufficiently long password"));
        assertThat(encoder.matches("a sufficiently long password", first)).isTrue();
        assertThat(encoder.matches("incorrect password", first)).isFalse();
        assertThat(encoder.upgradeEncoding(first)).isFalse();
    }
    @Test void correctPasswordProducesOnlyTheCompanySessionPrincipal() {
        var repository = mock(IdentityRepository.class);
        var encoder = config.passwordEncoder();
        var account = account(encoder.encode("a sufficiently long password"), "ACTIVE", false, false, true);
        var grant = new SessionGrant(UUID.randomUUID(), account.id(), UUID.randomUUID(), "a.test", 1, 1, 1, Instant.now(), false);
        when(repository.findAccount("alice@example.com")).thenReturn(Optional.of(account));
        when(repository.createGrant(account.id(), 1, "a.test")).thenReturn(Optional.of(grant));
        var result = config.authenticationManager(repository, encoder).authenticate(input("a sufficiently long password"));
        assertThat(result.getPrincipal()).isEqualTo(grant);
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("COMPANY_SESSION");
    }
    @Test void invalidCredentialsAndUnknownAccountsHaveTheSameFailureType() {
        var repository = mock(IdentityRepository.class);
        var encoder = config.passwordEncoder();
        when(repository.findAccount("alice@example.com")).thenReturn(Optional.empty());
        var manager = config.authenticationManager(repository, encoder);
        assertThatThrownBy(() -> manager.authenticate(input("wrong"))).isInstanceOf(BadCredentialsException.class);
        when(repository.findAccount("alice@example.com")).thenReturn(Optional.of(account(encoder.encode("correct"), "ACTIVE", false, false, true)));
        assertThatThrownBy(() -> manager.authenticate(input("wrong"))).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).createGrant(any(), anyLong(), anyString());
    }
    @Test void disabledPendingUnverifiedMfaAndPrivilegedAccountsCannotCreateSessions() {
        var encoder = config.passwordEncoder();
        String hash = encoder.encode("a sufficiently long password");
        for (var account : new Account[] {account(hash, "DISABLED", false, false, true), account(hash, "PENDING", false, false, true),
                account(hash, "ACTIVE", false, false, false), account(hash, "ACTIVE", true, false, true), account(hash, "ACTIVE", false, true, true)}) {
            var repository = mock(IdentityRepository.class);
            when(repository.findAccount("alice@example.com")).thenReturn(Optional.of(account));
            var manager = config.authenticationManager(repository, encoder);
            assertThatThrownBy(() -> manager.authenticate(input("a sufficiently long password"))).isInstanceOf(BadCredentialsException.class);
            verify(repository, never()).createGrant(any(), anyLong(), anyString());
        }
    }
    @Test void absentMembershipOrCompanyPolicyDeniesEvenAfterPasswordVerification() {
        var repository = mock(IdentityRepository.class);
        var encoder = config.passwordEncoder();
        when(repository.findAccount("alice@example.com")).thenReturn(Optional.of(account(encoder.encode("correct"), "ACTIVE", false, false, true)));
        when(repository.createGrant(any(), anyLong(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> config.authenticationManager(repository, encoder).authenticate(input("correct")))
            .isInstanceOf(BadCredentialsException.class);
    }
    @Test void obsoleteArgon2ParametersAreUpgradedByTheNativeProvider() {
        var repository = mock(IdentityRepository.class);
        var encoder = config.passwordEncoder();
        String oldHash = new Argon2PasswordEncoder(16,32,1,16384,2).encode("correct passphrase");
        var current = new java.util.concurrent.atomic.AtomicReference<>(account(oldHash,"ACTIVE",false,false,true));
        when(repository.findAccount("alice@example.com")).thenAnswer(invocation -> Optional.of(current.get()));
        when(repository.upgradePassword(any(),eq(oldHash),anyString())).thenAnswer(invocation -> {
            var a = current.get();
            current.set(new Account(a.id(),a.email(),invocation.getArgument(2),a.status(),a.emailVerified(),a.mfaRequired(),a.privileged(),a.securityVersion()));
            return true;
        });
        var grant = new SessionGrant(UUID.randomUUID(),current.get().id(),UUID.randomUUID(),"a.test",1,1,1,Instant.now(),false);
        when(repository.createGrant(any(),anyLong(),eq("a.test"))).thenReturn(Optional.of(grant));
        config.authenticationManager(repository,encoder).authenticate(input("correct passphrase"));
        assertThat(current.get().passwordHash()).isNotEqualTo(oldHash);
        assertThat(encoder.upgradeEncoding(current.get().passwordHash())).isFalse();
        assertThat(encoder.matches("correct passphrase",current.get().passwordHash())).isTrue();
        verify(repository).upgradePassword(eq(current.get().id()),eq(oldHash),anyString());
    }
    private static Account account(String hash, String status, boolean mfa, boolean privileged, boolean verified) {
        return new Account(UUID.randomUUID(), "alice@example.com", hash, status, verified, mfa, privileged, 1);
    }
    private static UsernamePasswordAuthenticationToken input(String password) {
        var input = UsernamePasswordAuthenticationToken.unauthenticated("alice@example.com", password);
        input.setDetails("a.test"); return input;
    }
}
