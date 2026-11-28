package com.saaserp.erp.platform.identity.application;

public interface ProtectionRateLimit {
    final class Exceeded extends RuntimeException { public Exceeded(){super("Try again later");} }
    boolean allow(String source, String scope);
}
