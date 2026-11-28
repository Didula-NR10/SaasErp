package com.saaserp.erp.platform.identity.application;

import com.saaserp.erp.platform.identity.domain.MfaPolicy;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** PostgreSQL writer port. atomic holds current account/company/membership authority locks. */
public interface ProtectionRepository {
    record State(UUID id, UUID accountId, UUID companyId, String hostname, long accountVersion,
                 long membershipVersion, long companyVersion, MfaPolicy policy, boolean enrollment,
                 boolean recovery, String verifiedMethod, Instant verifiedAt) { }
    record Credential(byte[] id, byte[] publicKey, long counter, boolean uv, boolean backupEligible,
                      boolean backupState, byte[] attestation, byte[] clientData, Instant created, Instant lastUsed) { }
    <T> T atomic(Supplier<T> action);
    State currentState(SessionGrant grant);
    UUID begin(UUID account, long version, String host, String binding);
    State state(UUID transaction, String binding, String host, boolean lock);
    void cancel(UUID transaction,String binding,String host);
    void attempt(UUID transaction, String binding, String host);
    UUID challenge(State state, String kind, String options);
    String takeChallenge(UUID transaction, UUID challenge, String kind, String binding, String host);
    List<Credential> credentials(State state);
    void saveCredential(State state, Credential credential);
    void deleteCredential(State state, byte[] credentialId);
    int factorCount(State state);
    String totp(State state, boolean pending);
    void pendingTotp(State state, String ciphertext);
    boolean acceptTotp(State state, long step, boolean enrollment);
    void verified(State state, String method);
    void factorChanged(State state, String method);
    void recoveryCodes(State state, List<String> hashes);
    void recover(State state, String codeHash);
    SessionGrant finish(State state);
    void changePassword(State state, String previousHash, String newHash);
    void revokeAll(State state);
    void requestToken(String email, String host, String purpose, String digest, String encryptedDelivery);
    void resetPassword(String token, String host, String newHash);
    void verifyEmail(String token, String host);
    void approveEnrollment(String token,String host);
    void event(UUID account, UUID company, String kind);
}
