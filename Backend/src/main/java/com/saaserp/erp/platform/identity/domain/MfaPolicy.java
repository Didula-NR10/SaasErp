package com.saaserp.erp.platform.identity.domain;

/** Policy comes from current server membership/company records, never from client roles. */
public record MfaPolicy(Role role, boolean accountRequired, boolean companyRequired,
                        boolean membershipRequired, boolean privileged, boolean totpAllowed) {
    public enum Role { PLATFORM_ADMIN, OWNER, ADMIN, MANAGER, CASHIER, EMPLOYEE }
    public boolean hardwareRequired() { return privileged || role == Role.PLATFORM_ADMIN; }
    public boolean required() {
        return hardwareRequired() || role == Role.OWNER || role == Role.ADMIN || role == Role.MANAGER
            || accountRequired || companyRequired || membershipRequired;
    }
    public boolean accepts(String factor) {
        // Reviewed hardware attestation/metadata is not yet configured. UV is insufficient evidence.
        if (hardwareRequired()) return false;
        return "WEBAUTHN".equals(factor) || ("TOTP".equals(factor) && totpAllowed);
    }
}
