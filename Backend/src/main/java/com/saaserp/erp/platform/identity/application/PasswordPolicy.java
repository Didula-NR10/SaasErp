package com.saaserp.erp.platform.identity.application;

public interface PasswordPolicy {
    /** Reject weak/compromised passwords; unavailable required screening must fail closed. */
    void validate(String password);
}
