package com.saaserp.erp.platform.identity.infrastructure;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Atomic bounded windows on security keys, independent of disposable tenant cache keys. */
public final class AuthenticationThrottle {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end
        return n
        """, Long.class);
    private final StringRedisTemplate redis;
    private final String namespace;
    public AuthenticationThrottle(StringRedisTemplate redis, String namespace) {
        this.redis = redis; this.namespace = namespace;
    }
    public boolean allow(String source, String identity) {
        return within("source", source, 30) && within("account", identity, 10);
    }
    public boolean allowApiRequest(String source, String accountScope) {
        return within("api-source", source, 300)
            && (accountScope == null || within("api-account", accountScope, 120));
    }
    private boolean within(String kind, String value, long maximum) {
        Long count = redis.execute(SCRIPT, List.of(namespace + ":limits:" + kind + ":" + CompromisedPasswordPolicy.digest(value)));
        if (count == null) throw new IllegalStateException("Security limiter unavailable");
        return count <= maximum;
    }
}
