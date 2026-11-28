package com.saaserp.erp.platform.identity.application;

import com.saaserp.erp.platform.identity.domain.Account;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.Optional;
import java.util.UUID;

/** All authority reads use the PostgreSQL writer; implementations must not cache allows. */
public interface IdentityRepository {
    Optional<Account> findAccount(String normalizedEmail);
    boolean registerPending(UUID id, String normalizedEmail, String encodedPassword);
    boolean upgradePassword(UUID id, String previousHash, String encodedPassword);
    Optional<SessionGrant> createGrant(UUID accountId, long accountVersion, String hostname);
    boolean isCurrent(SessionGrant grant);
    void revoke(UUID grantId);
}
