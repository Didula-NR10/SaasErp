package com.saaserp.erp.platform.authorization.domain;

import java.util.Objects;
import java.util.UUID;

public record PlatformSessionContext(UUID sessionId, UUID accountId, String sessionBindingDigest, String hostname) {
    public PlatformSessionContext {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(accountId);
        if (sessionBindingDigest == null || !sessionBindingDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid platform session binding");
        }
        if (hostname == null || hostname.isBlank() || hostname.length() > 253) {
            throw new IllegalArgumentException("Invalid platform hostname");
        }
    }
}
