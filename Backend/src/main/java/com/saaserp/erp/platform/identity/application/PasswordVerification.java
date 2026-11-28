package com.saaserp.erp.platform.identity.application;

import java.util.UUID;

public interface PasswordVerification {
    record Verified(UUID accountId, long securityVersion) { }
    Verified verify(String email, String password);
    String currentHash(UUID accountId);
    boolean matches(String password, String encoded);
    String encode(String password);
}
