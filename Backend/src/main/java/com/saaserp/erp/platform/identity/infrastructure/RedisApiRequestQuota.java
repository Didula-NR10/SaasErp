package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.ApiRequestQuota;

public final class RedisApiRequestQuota implements ApiRequestQuota {
    private final AuthenticationThrottle throttle;

    public RedisApiRequestQuota(AuthenticationThrottle throttle) {
        this.throttle = throttle;
    }

    @Override public boolean allow(String serverObservedAddress, String authenticatedScope) {
        if (serverObservedAddress == null || serverObservedAddress.isBlank()) {
            throw new IllegalStateException("Request source unavailable");
        }
        return throttle.allowApiRequest(serverObservedAddress, authenticatedScope);
    }
}
