package com.saaserp.erp.platform.identity.domain;

import java.util.UUID;

public record Account(UUID id, String email, String passwordHash, String status,
                      boolean emailVerified, boolean mfaRequired, boolean privileged, long securityVersion) {
    public boolean canUsePasswordSession() {
        return "ACTIVE".equals(status) && emailVerified && !mfaRequired && !privileged;
    }
    @Override public String toString() { return "Account[id=" + id + "]"; }
}
