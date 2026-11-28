package com.saaserp.erp.platform.identity;

import com.webauthn4j.converter.AuthenticatorDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.*;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Map;
import org.springframework.security.web.webauthn.api.*;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import tools.jackson.databind.json.JsonMapper;

/** Test-only virtual authenticator: real JCA EC signatures and native WebAuthn4J CBOR models. */
final class WebAuthnTestAuthenticator {
    final JsonMapper json=JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
    final KeyPair pair;
    final Bytes id=Bytes.random();
    private long counter;
    long nextCounter(){return ++counter;}
    private final ObjectConverter converter=new ObjectConverter();
    WebAuthnTestAuthenticator() throws GeneralSecurityException {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));pair=generator.generateKeyPair();
    }
    String registration(String options,String rp,String origin,boolean uv) throws GeneralSecurityException {
        byte[] client=client("webauthn.create",challenge(options),origin);
        var attested=new AttestedCredentialData(AAGUID.ZERO,id.getBytes(),
            EC2COSEKey.create((ECPublicKey)pair.getPublic(),COSEAlgorithmIdentifier.ES256));
        var auth=new AuthenticatorData<RegistrationExtensionAuthenticatorOutput>(sha(rp.getBytes(StandardCharsets.UTF_8)),(byte)(uv?0x45:0x41),0,attested);
        byte[] attestation=converter.getCborConverter().writeValueAsBytes(new AttestationObject(auth,new NoneAttestationStatement()));
        var response=AuthenticatorAttestationResponse.builder().clientDataJSON(new Bytes(client)).attestationObject(new Bytes(attestation)).build();
        var credential=PublicKeyCredential.<AuthenticatorAttestationResponse>builder().id(id.toBase64UrlString()).rawId(id)
            .type(PublicKeyCredentialType.PUBLIC_KEY).response(response).clientExtensionResults(new ImmutableAuthenticationExtensionsClientOutputs()).build();
        return json.writeValueAsString(credential);
    }
    String assertion(String options,String rp,String origin,boolean uv,long counter) throws GeneralSecurityException {
        byte[] client=client("webauthn.get",challenge(options),origin);
        byte[] auth=new AuthenticatorDataConverter(converter).convert(new AuthenticatorData<>(sha(rp.getBytes(StandardCharsets.UTF_8)),(byte)(uv?0x05:0x01),counter));
        byte[] signed=ByteBuffer.allocate(auth.length+32).put(auth).put(sha(client)).array();
        Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(pair.getPrivate());signer.update(signed);
        var response=AuthenticatorAssertionResponse.builder().clientDataJSON(new Bytes(client)).authenticatorData(new Bytes(auth)).signature(new Bytes(signer.sign())).build();
        var credential=PublicKeyCredential.<AuthenticatorAssertionResponse>builder().id(id.toBase64UrlString()).rawId(id)
            .type(PublicKeyCredentialType.PUBLIC_KEY).response(response).clientExtensionResults(new ImmutableAuthenticationExtensionsClientOutputs()).build();
        return json.writeValueAsString(credential);
    }
    private Bytes challenge(String options) {
        var challenge=json.readTree(options).get("challenge");
        if(challenge==null || !challenge.isString())throw new IllegalArgumentException("Missing WebAuthn challenge");
        return Bytes.fromBase64(challenge.asText());
    }
    private byte[] client(String type,Bytes challenge,String origin){return json.writeValueAsBytes(Map.of("type",type,"challenge",challenge.toBase64UrlString(),"origin",origin,"crossOrigin",false));}
    private static byte[] sha(byte[] value)throws GeneralSecurityException{return MessageDigest.getInstance("SHA-256").digest(value);}
}
