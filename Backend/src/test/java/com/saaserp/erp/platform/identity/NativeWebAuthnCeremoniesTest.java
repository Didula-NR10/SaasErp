package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.MfaPolicy;
import com.saaserp.erp.platform.identity.infrastructure.NativeWebAuthnCeremonies;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeWebAuthnCeremoniesTest {
    private final ProtectionRepository store=mock(ProtectionRepository.class);
    private final ProtectionRepository.State state=new ProtectionRepository.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"a.test",1,1,1,
        new MfaPolicy(MfaPolicy.Role.EMPLOYEE,true,true,true,false,false),true,false,null,null);
    private final AtomicReference<ProtectionRepository.Credential> credential=new AtomicReference<>();
    private final NativeWebAuthnCeremonies nativeRp=new NativeWebAuthnCeremonies(store,"https://a.test,https://b.test");
    @BeforeEach void repository(){
        when(store.credentials(state)).thenAnswer(i->credential.get()==null?List.of():List.of(credential.get()));
        doAnswer(i->{credential.set(i.getArgument(1));return null;}).when(store).saveCredential(eq(state),any());
    }
    @Test void realUvRegistrationAndSignedAssertionUseNativeVerifier() throws Exception {
        var device=new WebAuthnTestAuthenticator();String creation=nativeRp.options(state,true);
        assertThat(device.json.readTree(creation).get("rp").get("id").asText()).isEqualTo("a.test");
        assertThat(device.json.readTree(creation).get("authenticatorSelection").get("userVerification").asText()).isEqualTo("required");
        nativeRp.register(state,creation,device.registration(creation,"a.test","https://a.test",true));
        assertThat(credential.get()).isNotNull();
        String request=nativeRp.options(state,false);nativeRp.authenticate(state,request,device.assertion(request,"a.test","https://a.test",true,1));
        assertThat(credential.get().counter()).isEqualTo(1);
    }
    @Test void registrationRejectsSiblingOriginWrongRpAndMissingUv() throws Exception {
        for(String variant:List.of("origin","rp","uv")){
            var device=new WebAuthnTestAuthenticator();String options=nativeRp.options(state,true);
            String reply=device.registration(options,variant.equals("rp")?"b.test":"a.test",variant.equals("origin")?"https://b.test":"https://a.test",!variant.equals("uv"));
            assertThatThrownBy(()->nativeRp.register(state,options,reply)).isExactlyInstanceOf(ProtectionFailure.class);
            assertThat(credential.get()).isNull();
        }
    }
    @Test void assertionRejectsWrongChallengeOriginRpAndMissingUv() throws Exception {
        var device=new WebAuthnTestAuthenticator();String creation=nativeRp.options(state,true);
        nativeRp.register(state,creation,device.registration(creation,"a.test","https://a.test",true));
        for(String variant:List.of("challenge","origin","rp","uv")){
            String expected=nativeRp.options(state,false),used=variant.equals("challenge")?nativeRp.options(state,false):expected;
            String reply=device.assertion(used,variant.equals("rp")?"b.test":"a.test",variant.equals("origin")?"https://b.test":"https://a.test",!variant.equals("uv"),1);
            assertThatThrownBy(()->nativeRp.authenticate(state,expected,reply)).isExactlyInstanceOf(ProtectionFailure.class);
            assertThat(credential.get().counter()).isZero();
        }
    }
    @Test void wrongCredentialOwnerAndMalformedReplyCannotAuthenticate() throws Exception {
        var device=new WebAuthnTestAuthenticator();String options=nativeRp.options(state,false);
        assertThatThrownBy(()->nativeRp.authenticate(state,options,device.assertion(options,"a.test","https://a.test",true,1)))
            .isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->nativeRp.authenticate(state,options,"{}" )).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void aSignatureFromAnotherPrivateKeyCannotAuthenticateAnOwnedCredential() throws Exception {
        var owner=new WebAuthnTestAuthenticator();String creation=nativeRp.options(state,true);
        nativeRp.register(state,creation,owner.registration(creation,"a.test","https://a.test",true));
        var attacker=new WebAuthnTestAuthenticator();String options=nativeRp.options(state,false);
        var forged=(tools.jackson.databind.node.ObjectNode)attacker.json.readTree(attacker.assertion(options,"a.test","https://a.test",true,1));
        forged.put("id",owner.id.toBase64UrlString());forged.put("rawId",owner.id.toBase64UrlString());
        assertThatThrownBy(()->nativeRp.authenticate(state,options,forged.toString())).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(credential.get().counter()).isZero();
    }
    @Test void crossOriginEmbeddingCannotRegisterEvenAtTheRightRpAndOrigin() throws Exception {
        var device=new WebAuthnTestAuthenticator();String options=nativeRp.options(state,true);
        var reply=(tools.jackson.databind.node.ObjectNode)device.json.readTree(device.registration(options,"a.test","https://a.test",true));
        var response=(tools.jackson.databind.node.ObjectNode)reply.get("response");
        var client=(tools.jackson.databind.node.ObjectNode)device.json.readTree(java.util.Base64.getUrlDecoder().decode(response.get("clientDataJSON").asText()));
        client.put("crossOrigin",true);response.put("clientDataJSON",java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(device.json.writeValueAsBytes(client)));
        assertThatThrownBy(()->nativeRp.register(state,options,reply.toString())).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(credential.get()).isNull();
    }
    @Test void duplicateRegistrationAndUnconfiguredRpAreRejected() throws Exception {
        var device=new WebAuthnTestAuthenticator();String options=nativeRp.options(state,true);
        String reply=device.registration(options,"a.test","https://a.test",true);nativeRp.register(state,options,reply);
        assertThatThrownBy(()->nativeRp.register(state,options,reply)).isExactlyInstanceOf(ProtectionFailure.class);
        var other=new NativeWebAuthnCeremonies(store,"https://b.test");
        assertThatThrownBy(()->other.options(state,true)).isExactlyInstanceOf(ProtectionFailure.class);
    }
}
