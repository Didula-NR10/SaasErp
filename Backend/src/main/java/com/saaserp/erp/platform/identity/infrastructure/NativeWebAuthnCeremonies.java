package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.*;
import org.springframework.security.web.webauthn.management.*;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Scope-bound native RP operations; no global mutable RP or parent-domain trust. */
public final class NativeWebAuthnCeremonies implements WebAuthnCeremonies {
    private final ProtectionRepository store;
    private final Set<String> trustedHosts;
    private final JsonMapper json=JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
    public NativeWebAuthnCeremonies(ProtectionRepository store,String approvedOrigins) {
        this.store=store;new HostPolicy(approvedOrigins);
        trustedHosts=java.util.Arrays.stream(approvedOrigins.split(",")).map(String::strip).filter(s->!s.isEmpty())
            .map(s->java.net.URI.create(s).getHost()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Override public String options(ProtectionRepository.State state,boolean registration) {
        var rp=operations(state);var principal=UsernamePasswordAuthenticationToken.authenticated(state.accountId().toString(),null,List.of());
        Object value=registration?rp.createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(principal)):
            rp.createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(principal));
        return json.writeValueAsString(value);
    }
    @Override public void register(ProtectionRepository.State state,String options,String reply) {
        try {
            var creation=creationOptions(options);
            if(!state.hostname().equals(creation.getRp().getId()) || creation.getAuthenticatorSelection()==null
                    || !UserVerificationRequirement.REQUIRED.equals(creation.getAuthenticatorSelection().getUserVerification()))throw new ProtectionFailure();
            var credential=json.readValue(reply,new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>(){});
            requireClientContext(credential);
            if(credential.getResponse().getAttestationObject()==null || credential.getResponse().getAttestationObject().getBytes().length>32768)throw new ProtectionFailure();
            var saved=operations(state).registerCredential(new ImmutableRelyingPartyRegistrationRequest(creation,new RelyingPartyPublicKey(credential,"Authenticator")));
            if(!saved.getUserEntityUserId().equals(user(state).getId()))throw new ProtectionFailure();
        } catch(com.webauthn4j.util.exception.WebAuthnException|IllegalArgumentException|tools.jackson.core.JacksonException invalid){throw new ProtectionFailure(invalid);}
    }
    @Override public void authenticate(ProtectionRepository.State state,String options,String reply) {
        try {
            var request=requestOptions(options);
            if(!state.hostname().equals(request.getRpId()) || !UserVerificationRequirement.REQUIRED.equals(request.getUserVerification()))throw new ProtectionFailure();
            var credential=json.readValue(reply,new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>(){});
            requireClientContext(credential);
            var response=credential.getResponse();
            if(response.getAuthenticatorData()==null || response.getSignature()==null
                    || response.getAuthenticatorData().getBytes().length>8192 || response.getSignature().getBytes().length>1024)throw new ProtectionFailure();
            var verified=operations(state).authenticate(new RelyingPartyAuthenticationRequest(request,credential));
            if(!verified.getId().equals(user(state).getId()))throw new ProtectionFailure();
        } catch(com.webauthn4j.util.exception.WebAuthnException|IllegalArgumentException|tools.jackson.core.JacksonException invalid){throw new ProtectionFailure(invalid);}
    }
    private void requireClientContext(PublicKeyCredential<?> credential) {
        if(credential==null || credential.getRawId()==null || credential.getId()==null || (credential.getRawId().getBytes().length==0 || credential.getRawId().getBytes().length>1023)
                || !credential.getId().equals(credential.getRawId().toBase64UrlString()) || !PublicKeyCredentialType.PUBLIC_KEY.equals(credential.getType()) || credential.getResponse()==null
                || credential.getResponse().getClientDataJSON()==null)throw new ProtectionFailure();
        byte[] data=credential.getResponse().getClientDataJSON().getBytes();
        if(data.length>16384)throw new ProtectionFailure();
        var client=json.readTree(data);
        if(client==null || !client.isObject() || client.get("topOrigin")!=null)throw new ProtectionFailure();
        var crossOrigin=client.get("crossOrigin");
        if(crossOrigin!=null && (!crossOrigin.isBoolean() || crossOrigin.asBoolean()))throw new ProtectionFailure();
    }
    private PublicKeyCredentialCreationOptions creationOptions(String source) {
        JsonNode root=optionsObject(source);
        JsonNode rp=required(root,"rp");
        JsonNode user=required(root,"user");
        var builder=PublicKeyCredentialCreationOptions.builder()
            .rp(PublicKeyCredentialRpEntity.builder().name(text(required(rp,"name"))).id(text(required(rp,"id"))).build())
            .user(ImmutablePublicKeyCredentialUserEntity.builder()
                .name(text(required(user,"name"))).id(bytes(required(user,"id")))
                .displayName(text(required(user,"displayName"))).build())
            .challenge(bytes(required(root,"challenge")))
            .pubKeyCredParams(credentialParameters(required(root,"pubKeyCredParams")))
            .excludeCredentials(descriptors(optional(root,"excludeCredentials")))
            .authenticatorSelection(selection(optional(root,"authenticatorSelection")))
            .attestation(attestation(optional(root,"attestation")))
            .extensions(extensions(optional(root,"extensions")));
        JsonNode timeout=optional(root,"timeout");
        if(timeout!=null)builder.timeout(timeout(timeout));
        return builder.build();
    }
    private PublicKeyCredentialRequestOptions requestOptions(String source) {
        JsonNode root=optionsObject(source);
        var builder=PublicKeyCredentialRequestOptions.builder()
            .challenge(bytes(required(root,"challenge")))
            .rpId(text(required(root,"rpId")))
            .allowCredentials(descriptors(optional(root,"allowCredentials")))
            .userVerification(userVerification(optional(root,"userVerification")))
            .extensions(extensions(optional(root,"extensions")));
        JsonNode timeout=optional(root,"timeout");
        if(timeout!=null)builder.timeout(timeout(timeout));
        return builder.build();
    }
    private JsonNode optionsObject(String source) {
        JsonNode root=json.readTree(source);
        if(root==null || !root.isObject())throw new IllegalArgumentException("Invalid WebAuthn options");
        return root;
    }
    private static JsonNode required(JsonNode object,String name) {
        JsonNode value=optional(object,name);
        if(value==null)throw new IllegalArgumentException("Missing WebAuthn option");
        return value;
    }
    private static JsonNode optional(JsonNode object,String name) {
        JsonNode value=object.get(name);
        return value==null || value.isNull()?null:value;
    }
    private static String text(JsonNode value) {
        if(!value.isTextual())throw new IllegalArgumentException("Invalid WebAuthn option");
        return value.asText();
    }
    private static Bytes bytes(JsonNode value) {
        return Bytes.fromBase64(text(value));
    }
    private static Duration timeout(JsonNode value) {
        if(!value.isIntegralNumber() || value.longValue()<=0)throw new IllegalArgumentException("Invalid WebAuthn timeout");
        return Duration.ofMillis(value.longValue());
    }
    private static List<PublicKeyCredentialParameters> credentialParameters(JsonNode value) {
        if(!value.isArray())throw new IllegalArgumentException("Invalid WebAuthn credential parameters");
        List<PublicKeyCredentialParameters> supported=List.of(PublicKeyCredentialParameters.EdDSA,
            PublicKeyCredentialParameters.ES256,PublicKeyCredentialParameters.ES384,PublicKeyCredentialParameters.ES512,
            PublicKeyCredentialParameters.RS256,PublicKeyCredentialParameters.RS384,PublicKeyCredentialParameters.RS512,
            PublicKeyCredentialParameters.RS1);
        List<PublicKeyCredentialParameters> result=new ArrayList<>();
        for(JsonNode parameter:value) {
            if(!PublicKeyCredentialType.PUBLIC_KEY.equals(PublicKeyCredentialType.valueOf(text(required(parameter,"type"))))) {
                throw new IllegalArgumentException("Unsupported WebAuthn credential type");
            }
            JsonNode algorithm=required(parameter,"alg");
            if(!algorithm.isIntegralNumber())throw new IllegalArgumentException("Invalid WebAuthn algorithm");
            result.add(supported.stream().filter(candidate->candidate.getAlg().getValue()==algorithm.longValue())
                .findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported WebAuthn algorithm")));
        }
        return List.copyOf(result);
    }
    private static List<PublicKeyCredentialDescriptor> descriptors(JsonNode value) {
        if(value==null)return null;
        if(!value.isArray())throw new IllegalArgumentException("Invalid WebAuthn credential descriptors");
        List<PublicKeyCredentialDescriptor> result=new ArrayList<>();
        for(JsonNode descriptor:value) {
            var builder=PublicKeyCredentialDescriptor.builder()
                .type(PublicKeyCredentialType.valueOf(text(required(descriptor,"type"))))
                .id(bytes(required(descriptor,"id")));
            JsonNode transports=optional(descriptor,"transports");
            if(transports!=null) {
                if(!transports.isArray())throw new IllegalArgumentException("Invalid WebAuthn transports");
                Set<AuthenticatorTransport> values=new LinkedHashSet<>();
                for(JsonNode transport:transports)values.add(AuthenticatorTransport.valueOf(text(transport)));
                builder.transports(values);
            }
            result.add(builder.build());
        }
        return List.copyOf(result);
    }
    private static AuthenticatorSelectionCriteria selection(JsonNode value) {
        if(value==null)return null;
        var builder=AuthenticatorSelectionCriteria.builder();
        JsonNode attachment=optional(value,"authenticatorAttachment");
        JsonNode residentKey=optional(value,"residentKey");
        JsonNode verification=optional(value,"userVerification");
        if(attachment!=null)builder.authenticatorAttachment(AuthenticatorAttachment.valueOf(text(attachment)));
        if(residentKey!=null)builder.residentKey(ResidentKeyRequirement.valueOf(text(residentKey)));
        if(verification!=null)builder.userVerification(userVerification(verification));
        return builder.build();
    }
    private static AttestationConveyancePreference attestation(JsonNode value) {
        return value==null?null:AttestationConveyancePreference.valueOf(text(value));
    }
    private AuthenticationExtensionsClientInputs extensions(JsonNode value) {
        if(value==null)return null;
        if(!value.isObject())throw new IllegalArgumentException("Invalid WebAuthn extensions");
        List<AuthenticationExtensionsClientInput> inputs=new ArrayList<>();
        for(var extension:value.properties()) {
            Object input=json.treeToValue(extension.getValue(),Object.class);
            inputs.add(new ImmutableAuthenticationExtensionsClientInput<>(extension.getKey(),input));
        }
        return new ImmutableAuthenticationExtensionsClientInputs(inputs);
    }
    private static UserVerificationRequirement userVerification(JsonNode value) {
        if(value==null)return null;
        return switch(text(value)) {
            case "discouraged" -> UserVerificationRequirement.DISCOURAGED;
            case "preferred" -> UserVerificationRequirement.PREFERRED;
            case "required" -> UserVerificationRequirement.REQUIRED;
            default -> throw new IllegalArgumentException("Invalid WebAuthn user verification requirement");
        };
    }
    private Webauthn4JRelyingPartyOperations operations(ProtectionRepository.State state) {
        if(!trustedHosts.contains(state.hostname()) || state.policy().hardwareRequired())throw new ProtectionFailure();
        var user=user(state);
        var users=new PublicKeyCredentialUserEntityRepository(){
            public PublicKeyCredentialUserEntity findById(Bytes id){return user.getId().equals(id)?user:null;}
            public PublicKeyCredentialUserEntity findByUsername(String name){return user.getName().equals(name)?user:null;}
            public void save(PublicKeyCredentialUserEntity entity){if(!user.getId().equals(entity.getId()))throw new ProtectionFailure();}
            public void delete(Bytes id){throw new UnsupportedOperationException("Use authorized factor removal");}
        };
        var credentials=new UserCredentialRepository(){
            public void delete(Bytes id){throw new UnsupportedOperationException("Use authorized factor removal");}
            public CredentialRecord findByCredentialId(Bytes id){return findByUserId(user.getId()).stream().filter(c->c.getCredentialId().equals(id)).findFirst().orElse(null);}
            public List<CredentialRecord> findByUserId(Bytes id){
                if(!user.getId().equals(id))return List.of();
                return store.credentials(state).stream().map(c->record(state,c)).toList();
            }
            public void save(CredentialRecord c){
                if(!user.getId().equals(c.getUserEntityUserId()))throw new ProtectionFailure();
                store.saveCredential(state,new ProtectionRepository.Credential(c.getCredentialId().getBytes(),c.getPublicKey().getBytes(),
                    c.getSignatureCount(),c.isUvInitialized(),c.isBackupEligible(),c.isBackupState(),
                    c.getAttestationObject().getBytes(),c.getAttestationClientDataJSON().getBytes(),
                    c.getCreated()==null?Instant.now():c.getCreated(),c.getLastUsed()));
            }
        };
        var rp=new Webauthn4JRelyingPartyOperations(users,credentials,
            PublicKeyCredentialRpEntity.builder().id(state.hostname()).name("Hich ERP").build(),Set.of("https://"+state.hostname()));
        rp.setCustomizeCreationOptions(c->c.timeout(Duration.ofMinutes(2)).attestation(AttestationConveyancePreference.NONE)
            .authenticatorSelection(AuthenticatorSelectionCriteria.builder().residentKey(ResidentKeyRequirement.PREFERRED)
                .userVerification(UserVerificationRequirement.REQUIRED).build()));
        rp.setCustomizeRequestOptions(c->c.timeout(Duration.ofMinutes(2)).userVerification(UserVerificationRequirement.REQUIRED));
        return rp;
    }
    private static PublicKeyCredentialUserEntity user(ProtectionRepository.State state){
        var id=state.accountId();byte[] bytes=ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
        return ImmutablePublicKeyCredentialUserEntity.builder().id(new Bytes(bytes)).name(id.toString()).displayName("Hich account").build();
    }
    private static CredentialRecord record(ProtectionRepository.State s,ProtectionRepository.Credential c){
        return ImmutableCredentialRecord.builder().credentialType(PublicKeyCredentialType.PUBLIC_KEY).credentialId(new Bytes(c.id()))
            .userEntityUserId(user(s).getId()).publicKey(new ImmutablePublicKeyCose(c.publicKey())).signatureCount(c.counter())
            .uvInitialized(c.uv()).backupEligible(c.backupEligible()).backupState(c.backupState()).transports(Set.of())
            .attestationObject(new Bytes(c.attestation())).attestationClientDataJSON(new Bytes(c.clientData()))
            .created(c.created()).lastUsed(c.lastUsed()).label("Authenticator").build();
    }
}
