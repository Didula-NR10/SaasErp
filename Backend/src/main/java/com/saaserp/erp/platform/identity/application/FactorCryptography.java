package com.saaserp.erp.platform.identity.application;

import java.time.Instant;

public interface FactorCryptography {
    String randomToken();
    String digest(String value);
    String seal(String context, String plaintext);
    String open(String context, String ciphertext);
    String newTotpSecret();
    String provisioningUri(String secret, String label);
    long matchingTotpStep(String secret, String code, Instant now);
}
