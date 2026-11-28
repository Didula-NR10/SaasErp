package com.saaserp.erp.platform.identity;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.saaserp.erp.platform.identity.application.ProtectionFailure;
import com.saaserp.erp.platform.identity.domain.MfaPolicy;
import com.saaserp.erp.platform.identity.infrastructure.ProtectedFactorCryptography;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MfaPolicyAndCryptographyTest {
    private ProtectedFactorCryptography crypto(){byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);return new ProtectedFactorCryptography("current","current:"+Base64.getEncoder().encodeToString(key));}
    @Test void allPrivilegedAndPlatformPoliciesRequireUnresolvedHardwareEvidence(){
        for(var role:MfaPolicy.Role.values()){
            var privileged=new MfaPolicy(role,false,false,false,true,true);
            assertThat(privileged.required()).isTrue();assertThat(privileged.accepts("WEBAUTHN")).isFalse();assertThat(privileged.accepts("TOTP")).isFalse();
        }
        var platform=new MfaPolicy(MfaPolicy.Role.PLATFORM_ADMIN,false,false,false,false,true);
        assertThat(platform.hardwareRequired()).isTrue();assertThat(platform.accepts("WEBAUTHN")).isFalse();
    }
    @Test void companyOwnersAdminsManagersRequireMfaAndTotpIsExplicitlyOptIn(){
        for(var role:new MfaPolicy.Role[]{MfaPolicy.Role.OWNER,MfaPolicy.Role.ADMIN,MfaPolicy.Role.MANAGER}){
            var policy=new MfaPolicy(role,false,false,false,false,false);
            assertThat(policy.required()).isTrue();assertThat(policy.accepts("TOTP")).isFalse();assertThat(policy.accepts("WEBAUTHN")).isTrue();
        }
        assertThat(new MfaPolicy(MfaPolicy.Role.EMPLOYEE,false,false,false,false,false).required()).isFalse();
        assertThat(new MfaPolicy(MfaPolicy.Role.EMPLOYEE,true,false,false,false,true).accepts("TOTP")).isTrue();
    }
    @Test void aesGcmBindsCiphertextToAccountCompanyAndPurpose(){
        var crypto=crypto();String encrypted=crypto.seal("account:a:company:a","secret-value");
        assertThat(encrypted).doesNotContain("secret-value");assertThat(crypto.open("account:a:company:a",encrypted)).isEqualTo("secret-value");
        assertThatThrownBy(()->crypto.open("account:a:company:b",encrypted)).isExactlyInstanceOf(ProtectionFailure.class);
        String[] pieces=encrypted.split("\\.",2);byte[] tampered=Base64.getUrlDecoder().decode(pieces[1]);tampered[tampered.length-1]^=1;
        assertThatThrownBy(()->crypto.open("account:a:company:a",pieces[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(tampered))).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void missingAndMalformedKeysFailClosed(){
        assertThatThrownBy(()->new ProtectedFactorCryptography("","").seal("context","secret"))
            .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Account protection encryption unavailable");
        assertThatThrownBy(()->new ProtectedFactorCryptography("missing","key:invalid"))
            .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Invalid account-protection key configuration");
    }
    @Test void maintainedLibraryMatchesRfc6238Sha1TestVector() throws Exception {
        var key=new SecretKeySpec("12345678901234567890".getBytes(StandardCharsets.US_ASCII),"HmacSHA1");
        var generator=new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),8);
        assertThat(generator.generateOneTimePasswordString(key,Instant.ofEpochSecond(59),java.util.Locale.ROOT)).isEqualTo("94287082");
    }
    @Test void totpAllowsAtMostOneAdjacentStepAndRejectsInvalidInput() throws Exception {
        var crypto=crypto();String secret=Base32.toBase32String("12345678901234567890".getBytes(StandardCharsets.US_ASCII));var key=new SecretKeySpec(Base32.decode(secret),"HmacSHA1");
        var generator=new TimeBasedOneTimePasswordGenerator();Instant now=Instant.ofEpochSecond(6000);
        for(int delta=-1;delta<=1;delta++){
            String code=generator.generateOneTimePasswordString(key,now.plusSeconds(delta*30),java.util.Locale.ROOT);
            assertThat(crypto.matchingTotpStep(secret,code,now)).isEqualTo(200+delta);
        }
        assertThatThrownBy(()->crypto.matchingTotpStep(secret,"invalid",now)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->crypto.matchingTotpStep(secret,generator.generateOneTimePasswordString(key,now.plusSeconds(90),java.util.Locale.ROOT),now))
            .isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void recoverySecretsHaveIndependent256BitRandomnessAndOnlyDigestsPersist(){
        var crypto=crypto();String first=crypto.randomToken(),second=crypto.randomToken();
        assertThat(first).isNotEqualTo(second);assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
        assertThat(crypto.digest(first)).hasSize(64).isNotEqualTo(first).isNotEqualTo(crypto.digest(second));
    }
}
