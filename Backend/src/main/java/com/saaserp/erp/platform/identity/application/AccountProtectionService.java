package com.saaserp.erp.platform.identity.application;

import com.saaserp.erp.platform.identity.domain.AccountIdentity;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountProtectionService {
    public record Challenge(UUID id, String publicKey) { }
    private final ProtectionRepository store;
    private final PasswordVerification passwords;
    private final PasswordPolicy policy;
    private final FactorCryptography crypto;
    private final WebAuthnCeremonies webauthn;
    private final SecurityNotifications notifications;
    private final Clock clock;
    public AccountProtectionService(ProtectionRepository store, PasswordVerification passwords, PasswordPolicy policy,
            FactorCryptography crypto, WebAuthnCeremonies webauthn, SecurityNotifications notifications, Clock clock) {
        this.store=store;this.passwords=passwords;this.policy=policy;this.crypto=crypto;
        this.webauthn=webauthn;this.notifications=notifications;this.clock=clock;
    }
    public UUID start(String email, String password, String host, String binding) {
        var verified=passwords.verify(new AccountIdentity(email).value(),password);
        return store.atomic(() -> {
            UUID id=store.begin(verified.accountId(),verified.securityVersion(),host,crypto.digest(binding));
            var state=store.state(id,crypto.digest(binding),host,true);
            if (state.policy().hardwareRequired()) throw new ProtectionFailure();
            return id;
        });
    }
    public void cancel(UUID transaction,String binding,String host) {
        store.cancel(transaction,crypto.digest(binding),host);
    }
    public Challenge options(UUID transaction,String binding,String host,boolean registration) {
        return store.atomic(() -> {
            var state=current(transaction,binding,host);
            if (registration) { requireManagement(state); if(store.factorCount(state)>=10) throw new ProtectionFailure(); }
            String options=webauthn.options(state,registration);
            UUID id=store.challenge(state,registration?"REGISTER":"AUTHENTICATE",options);
            return new Challenge(id,options);
        });
    }
    public void webauthn(UUID transaction,String binding,String host,UUID challenge,String reply,boolean registration) {
        // Commit an attempt and challenge burn before crypto verification. Invalid replies cannot replay.
        store.attempt(transaction,crypto.digest(binding),host);
        String options=store.takeChallenge(transaction,challenge,registration?"REGISTER":"AUTHENTICATE",crypto.digest(binding),host);
        store.atomic(() -> {
            var state=current(transaction,binding,host);
            if (registration) {
                requireManagement(state);if(store.factorCount(state)>=10)throw new ProtectionFailure();webauthn.register(state,options,reply);
                store.factorChanged(state,null);notifications.enqueue(state.accountId(),state.companyId(),"FACTOR_ADDED");
            } else {
                webauthn.authenticate(state,options,reply);store.verified(state,"WEBAUTHN");
            }
            return null;
        });
    }
    /** Provisioning is deliberately one-time and restricted; never persist/log the plaintext URI. */
    public String enrollTotp(UUID transaction,String binding,String host) {
        return store.atomic(() -> {
            var state=current(transaction,binding,host);requireManagement(state);requireTotp(state);
            String secret=crypto.newTotpSecret();
            store.pendingTotp(state,crypto.seal(context(state),secret));
            return crypto.provisioningUri(secret,state.hostname()+":"+state.accountId());
        });
    }
    public void totp(UUID transaction,String binding,String host,String code,boolean enrollment) {
        store.attempt(transaction,crypto.digest(binding),host);
        store.atomic(() -> {
            var state=current(transaction,binding,host);requireTotp(state);
            if (enrollment) requireManagement(state);
            String ciphertext=store.totp(state,enrollment);
            String secret=crypto.open(context(state),ciphertext);
            long step=crypto.matchingTotpStep(secret,code,clock.instant());
            if (!store.acceptTotp(state,step,enrollment)) throw new ProtectionFailure();
            if (enrollment) {
                store.factorChanged(state,"TOTP");notifications.enqueue(state.accountId(),state.companyId(),"FACTOR_ADDED");
            } else store.verified(state,"TOTP");
            return null;
        });
    }
    public SessionGrant finish(UUID transaction,String binding,String host) {
        return store.atomic(() -> {
            var state=current(transaction,binding,host);requireVerified(state);
            if (state.recovery()) throw new ProtectionFailure();
            return store.finish(state);
        });
    }
    public List<String> recoveryCodes(UUID transaction,String binding,String host) {
        return store.atomic(() -> {
            var state=current(transaction,binding,host);requireVerified(state);
            List<String> values=new ArrayList<>();for(int i=0;i<10;i++) values.add(crypto.randomToken());
            store.recoveryCodes(state,values.stream().map(crypto::digest).toList());
            store.factorChanged(state,state.verifiedMethod());
            notifications.enqueue(state.accountId(),state.companyId(),"RECOVERY_CODES_REPLACED");
            return List.copyOf(values);
        });
    }
    public void recover(UUID transaction,String binding,String host,String code) {
        store.attempt(transaction,crypto.digest(binding),host);
        store.atomic(() -> {
            var state=current(transaction,binding,host);
            if (state.policy().hardwareRequired()) throw new ProtectionFailure();
            store.recover(state,crypto.digest(code));
            notifications.enqueue(state.accountId(),state.companyId(),"FACTOR_RECOVERY_STARTED");return null;
        });
    }
    public record Factor(String credentialId,String type) { }
    public List<Factor> factors(UUID transaction,String binding,String host) {
        return store.atomic(() -> {
            var state=current(transaction,binding,host);requireVerified(state);
            var values=new ArrayList<Factor>();
            for(var credential:store.credentials(state))values.add(new Factor(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(credential.id()),"WEBAUTHN"));
            if(store.factorCount(state)>values.size())values.add(new Factor("TOTP","TOTP"));
            return List.copyOf(values);
        });
    }
    public void remove(UUID transaction,String binding,String host,String credentialId) {
        store.atomic(() -> {
            var state=current(transaction,binding,host);requireVerified(state);
            // Replacement is enroll-first; the last usable authenticator cannot be removed.
            if (store.factorCount(state)<=1) throw new ProtectionFailure();
            if ("TOTP".equals(credentialId)) store.deleteCredential(state,null);
            else {
                byte[] id;
                try { id=java.util.Base64.getUrlDecoder().decode(credentialId); }
                catch(IllegalArgumentException invalid) { throw new ProtectionFailure(); }
                store.deleteCredential(state,id);
            }
            store.factorChanged(state,null);
            notifications.enqueue(state.accountId(),state.companyId(),"FACTOR_REMOVED");return null;
        });
    }
    public void changePassword(UUID transaction,String binding,String host,String currentPassword,String newPassword) {
        policy.validate(newPassword);String encoded=passwords.encode(newPassword);
        store.atomic(() -> {
            var state=current(transaction,binding,host);requireVerified(state);
            String old=passwords.currentHash(state.accountId());
            if (!passwords.matches(currentPassword,old)) throw new ProtectionFailure();
            store.changePassword(state,old,encoded);
            notifications.enqueue(state.accountId(),state.companyId(),"PASSWORD_CHANGED");return null;
        });
    }
    public void revokeAll(UUID transaction,String binding,String host) {
        store.atomic(() -> {
            var state=current(transaction,binding,host);requireVerified(state);store.revokeAll(state);
            notifications.enqueue(state.accountId(),state.companyId(),"SESSIONS_REVOKED");return null;
        });
    }
    public void requestToken(String email,String host,String purpose) {
        if (!List.of("EMAIL_VERIFY","PASSWORD_RESET","MFA_ENROLL").contains(purpose)) throw new IllegalArgumentException();
        String normalized=new AccountIdentity(email).value();String token=crypto.randomToken();
        // All valid requests do the same crypto work. Raw tokens only enter encrypted delivery outbox.
        String sealed=crypto.seal("delivery:"+host+":"+purpose,token);
        store.atomic(() -> { store.requestToken(normalized,host,purpose,crypto.digest(token),sealed);return null; });
    }
    public void resetPassword(String token,String host,String newPassword) {
        policy.validate(newPassword);String hash=passwords.encode(newPassword);
        store.atomic(() -> {store.resetPassword(token,host,hash);return null;});
    }
    public void verifyEmail(String token,String host) {
        store.atomic(() -> {store.verifyEmail(token,host);return null;});
    }
    public void approveEnrollment(String token,String host) {
        store.atomic(() -> {store.approveEnrollment(token,host);return null;});
    }
    private ProtectionRepository.State current(UUID id,String binding,String host) {
        var state=store.state(id,crypto.digest(binding),host,true);
        if (state.policy().hardwareRequired()) throw new ProtectionFailure();
        return state;
    }
    private void requireManagement(ProtectionRepository.State state) {
        if (!state.enrollment() && !state.recovery()) requireVerified(state);
    }
    private void requireVerified(ProtectionRepository.State state) {
        if (!state.policy().accepts(state.verifiedMethod()) || state.verifiedAt()==null
                || !clock.instant().isBefore(state.verifiedAt().plus(Duration.ofMinutes(5)))) throw new ProtectionFailure();
    }
    private static void requireTotp(ProtectionRepository.State state) {
        if (!state.policy().accepts("TOTP")) throw new ProtectionFailure();
    }
    private static String context(ProtectionRepository.State state) {return "totp:"+state.accountId()+":"+state.companyId();}
}
