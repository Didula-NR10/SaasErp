package com.saaserp.erp.platform.identity.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Contains identifiers and security snapshots, never password hashes or credentials. */
public record SessionGrant(UUID id, UUID accountId, UUID companyId, String hostname,
                           long accountVersion, long membershipVersion, long companyVersion,
                           Instant issuedAt, boolean privileged, String assurance) implements Serializable {
    public SessionGrant(UUID id,UUID accountId,UUID companyId,String hostname,long accountVersion,long membershipVersion,
            long companyVersion,Instant issuedAt,boolean privileged) {
        this(id,accountId,companyId,hostname,accountVersion,membershipVersion,companyVersion,issuedAt,privileged,"PASSWORD");
    }
    public SessionGrant {
        assurance=assurance==null?"PASSWORD":assurance;
        if(!java.util.Set.of("PASSWORD","WEBAUTHN","TOTP").contains(assurance))throw new IllegalArgumentException("Invalid assurance");
    }
    public Duration idleLimit() { return Duration.ofMinutes(privileged ? 15 : 30); }
    public Duration absoluteLimit() { return Duration.ofHours(privileged ? 8 : 12); }
    public boolean expired(Instant now, Instant lastAccess) {
        return !now.isBefore(issuedAt.plus(absoluteLimit())) || !now.isBefore(lastAccess.plus(idleLimit()));
    }
}
