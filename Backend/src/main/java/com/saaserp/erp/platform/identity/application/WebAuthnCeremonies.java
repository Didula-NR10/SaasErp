package com.saaserp.erp.platform.identity.application;

/** Native framework protocol serialization and verification live behind this adapter port. */
public interface WebAuthnCeremonies {
    String options(ProtectionRepository.State state, boolean registration);
    void register(ProtectionRepository.State state, String options, String reply);
    void authenticate(ProtectionRepository.State state, String options, String reply);
}
