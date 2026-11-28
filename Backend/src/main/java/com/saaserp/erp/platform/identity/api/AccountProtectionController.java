package com.saaserp.erp.platform.identity.api;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Action;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Outcome;
import com.saaserp.erp.platform.audit.application.SecurityAuditEvent.Target;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountProtectionController {
    private static final String TRANSACTION=ProtectionSessionState.TRANSACTION;
    private static final String BINDING=ProtectionSessionState.BINDING;
    private final AccountProtectionService protection;private final FactorCryptography crypto;
    private final ProtectionRateLimit limiter;private final IdentityRepository identities;private final ObjectMapper json;
    private final SecurityAudit audit;
    public AccountProtectionController(AccountProtectionService protection,FactorCryptography crypto,
            ProtectionRateLimit limiter,IdentityRepository identities,ObjectMapper json,SecurityAudit audit) {
        this.protection=protection;this.crypto=crypto;this.limiter=limiter;this.identities=identities;
        this.json=json;this.audit=audit;
    }
    @PostMapping(value="/mfa/start",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void start(@Valid @ModelAttribute PasswordStart body,HttpServletRequest request,HttpServletResponse response) {
        budget(request,"start:"+body.email().strip().toLowerCase(java.util.Locale.ROOT));
        String binding=crypto.randomToken();UUID id=protection.start(body.email(),body.password(),host(request),binding);
        var previousSession=request.getSession(false);
        if(previousSession!=null && previousSession.getAttribute(TRANSACTION) instanceof UUID previous
                && previousSession.getAttribute(BINDING) instanceof String previousBinding)protection.cancel(previous,previousBinding,host(request));
        var current=SecurityContextHolder.getContext().getAuthentication();
        if(current!=null && current.getPrincipal() instanceof SessionGrant grant) {
            identities.revoke(grant.id());
            audit.record(new SecurityAuditEvent(grant.accountId(),grant.companyId(),Action.SESSION_REVOKED,
                Target.SESSION,grant.id().toString(),Outcome.SUCCEEDED));
        }
        var empty=SecurityContextHolder.createEmptyContext();SecurityContextHolder.setContext(empty);
        new HttpSessionSecurityContextRepository().saveContext(empty,request,response);
        request.getSession();request.changeSessionId();
        request.getSession().setAttribute(TRANSACTION,id);request.getSession().setAttribute(BINDING,binding);
    }
    @PostMapping("/mfa/webauthn/registration/options")
    public Options registrationOptions(HttpServletRequest request){return options(request,true);}
    @PostMapping("/mfa/webauthn/authentication/options")
    public Options authenticationOptions(HttpServletRequest request){return options(request,false);}
    private Options options(HttpServletRequest request,boolean registration) {
        budget(request,"options:"+transaction(request));
        var result=protection.options(transaction(request),binding(request),host(request),registration);
        return new Options(result.id(),json.readTree(result.publicKey()));
    }
    @PostMapping("/mfa/webauthn/registration/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody WebAuthnReply body,HttpServletRequest request){webauthn(body,request,true);}
    @PostMapping("/mfa/webauthn/authentication/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void authenticate(@Valid @RequestBody WebAuthnReply body,HttpServletRequest request){webauthn(body,request,false);}
    private void webauthn(WebAuthnReply body,HttpServletRequest request,boolean registration) {
        budget(request,"verify:"+transaction(request));
        protection.webauthn(transaction(request),binding(request),host(request),body.challengeId(),body.credential(),registration);
        request.changeSessionId();
    }
    @PostMapping("/mfa/totp/enroll")
    public Provisioning enroll(HttpServletRequest request) {
        budget(request,"enroll:"+transaction(request));
        return new Provisioning(protection.enrollTotp(transaction(request),binding(request),host(request)));
    }
    @PostMapping("/mfa/totp/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody Code body,HttpServletRequest request){totp(body,request,true);}
    @PostMapping("/mfa/totp/verify") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody Code body,HttpServletRequest request){totp(body,request,false);}
    private void totp(Code body,HttpServletRequest request,boolean enrollment) {
        budget(request,"verify:"+transaction(request));
        protection.totp(transaction(request),binding(request),host(request),body.code(),enrollment);request.changeSessionId();
    }
    @PostMapping("/mfa/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(HttpServletRequest request) {
        budget(request,"cancel:"+transaction(request));protection.cancel(transaction(request),binding(request),host(request));
        request.getSession().removeAttribute(TRANSACTION);request.getSession().removeAttribute(BINDING);request.changeSessionId();
    }
    @PostMapping("/mfa/finish") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(HttpServletRequest request,HttpServletResponse response) {
        budget(request,"finish:"+transaction(request));
        var grant=protection.finish(transaction(request),binding(request),host(request));
        var auth=UsernamePasswordAuthenticationToken.authenticated(grant,null,List.of(new SimpleGrantedAuthority("COMPANY_SESSION")));
        new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()))).onAuthentication(auth,request,response);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);
        request.getSession().setMaxInactiveInterval((int)grant.idleLimit().toSeconds());
        request.getSession().removeAttribute(TRANSACTION);request.getSession().removeAttribute(BINDING);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
    }
    @PostMapping("/mfa/recovery-codes")
    public RecoveryCodes codes(HttpServletRequest request){
        budget(request,"codes:"+transaction(request));return new RecoveryCodes(protection.recoveryCodes(transaction(request),binding(request),host(request)));
    }
    @PostMapping("/mfa/recover") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recover(@Valid @RequestBody Code body,HttpServletRequest request){
        budget(request,"recover:"+transaction(request));protection.recover(transaction(request),binding(request),host(request),body.code());request.changeSessionId();
    }
    @PostMapping("/mfa/factors/list")
    public List<AccountProtectionService.Factor> factors(HttpServletRequest request){
        budget(request,"factors:"+transaction(request));return protection.factors(transaction(request),binding(request),host(request));
    }
    @PostMapping("/mfa/factors/remove") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Valid @RequestBody CredentialRemoval body,HttpServletRequest request){
        budget(request,"remove:"+transaction(request));protection.remove(transaction(request),binding(request),host(request),body.credentialId());
    }
    @PostMapping("/account/password/change") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordChange body,HttpServletRequest request){
        budget(request,"password:"+transaction(request));protection.changePassword(transaction(request),binding(request),host(request),body.currentPassword(),body.newPassword());
    }
    @PostMapping("/account/sessions/revoke-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(HttpServletRequest request){budget(request,"revoke:"+transaction(request));protection.revokeAll(transaction(request),binding(request),host(request));}
    @PostMapping("/account/email/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmail(@Valid @RequestBody Email body,HttpServletRequest request){requestToken(body,request,"EMAIL_VERIFY");}
    @PostMapping("/account/password/request-reset") @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody Email body,HttpServletRequest request){requestToken(body,request,"PASSWORD_RESET");}
    @PostMapping("/account/mfa/request-enrollment") @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEnrollment(@Valid @RequestBody Email body,HttpServletRequest request){requestToken(body,request,"MFA_ENROLL");}
    @PostMapping("/account/mfa/approve-enrollment") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveEnrollment(@Valid @RequestBody Code body,HttpServletRequest request){budget(request,"token");protection.approveEnrollment(body.code(),host(request));}
    private void requestToken(Email body,HttpServletRequest request,String purpose){
        budget(request,"delivery:"+body.email().strip().toLowerCase(java.util.Locale.ROOT));protection.requestToken(body.email(),host(request),purpose);
    }
    @PostMapping("/account/email/verify") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void email(@Valid @RequestBody Code body,HttpServletRequest request){budget(request,"token");protection.verifyEmail(body.code(),host(request));}
    @PostMapping("/account/password/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody PasswordReset body,HttpServletRequest request){budget(request,"token");protection.resetPassword(body.token(),host(request),body.newPassword());}
    private void budget(HttpServletRequest request,String scope){if(!limiter.allow(request.getRemoteAddr(),scope))throw new ProtectionRateLimit.Exceeded();}
    private static String host(HttpServletRequest request){return request.getHeader("Host");} // Previously validated by the existing HostPolicy guard.
    private static UUID transaction(HttpServletRequest request){
        var session=request.getSession(false);if(session==null || !(session.getAttribute(TRANSACTION) instanceof UUID id))throw new ProtectionFailure();return id;
    }
    private static String binding(HttpServletRequest request){
        var session=request.getSession(false);if(session==null || !(session.getAttribute(BINDING) instanceof String value))throw new ProtectionFailure();return value;
    }
    public record PasswordStart(@NotBlank @Size(max=254) String email,@NotBlank @Size(max=128) String password){@Override public String toString(){return "PasswordStart[redacted]";}}
    public record WebAuthnReply(@NotNull UUID challengeId,@NotBlank @Size(max=65536) String credential){@Override public String toString(){return "WebAuthnReply[redacted]";}}
    public record Code(@NotBlank @Size(max=128) String code){@Override public String toString(){return "Code[redacted]";}}
    public record CredentialRemoval(@NotBlank @Size(max=2048) String credentialId){ }
    public record Email(@NotBlank @Size(max=254) String email){ }
    public record PasswordChange(@NotBlank @Size(max=128) String currentPassword,@NotBlank @Size(min=15,max=128) String newPassword){@Override public String toString(){return "PasswordChange[redacted]";}}
    public record PasswordReset(@NotBlank @Size(max=128) String token,@NotBlank @Size(min=15,max=128) String newPassword){@Override public String toString(){return "PasswordReset[redacted]";}}
    public record Options(UUID challengeId,JsonNode publicKey){ }
    public record Provisioning(String provisioningUri){@Override public String toString(){return "Provisioning[redacted]";}}
    public record RecoveryCodes(List<String> codes){@Override public String toString(){return "RecoveryCodes[redacted]";}}
}
