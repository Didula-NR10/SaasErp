package com.saaserp.erp.platform.identity.application;

public interface ApiRequestQuota {
    boolean allow(String serverObservedAddress, String authenticatedScope);
}
