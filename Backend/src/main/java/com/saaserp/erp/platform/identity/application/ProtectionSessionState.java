package com.saaserp.erp.platform.identity.application;

/** Session attribute contract contains only a transaction reference and a random browser binding. */
public final class ProtectionSessionState {
    public static final String TRANSACTION="com.saaserp.erp.identity.pending.transaction";
    public static final String BINDING="com.saaserp.erp.identity.pending.binding";
    private ProtectionSessionState(){ }
}
