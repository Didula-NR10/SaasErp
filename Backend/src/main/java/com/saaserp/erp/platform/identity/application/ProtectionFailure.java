package com.saaserp.erp.platform.identity.application;

/** No account, credential or policy details may enter the client-facing error. */
public final class ProtectionFailure extends RuntimeException {
    public ProtectionFailure() { super("Authentication failed"); }
    public ProtectionFailure(Throwable cause) { super("Authentication failed", cause); }
}
