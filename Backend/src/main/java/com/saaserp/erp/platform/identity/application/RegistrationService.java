package com.saaserp.erp.platform.identity.application;

import com.saaserp.erp.platform.identity.domain.AccountIdentity;
import java.util.UUID;
import java.util.function.Function;

public final class RegistrationService {
    private final IdentityRepository identities;
    private final PasswordPolicy policy;
    private final Function<String, String> encode;
    public RegistrationService(IdentityRepository identities, PasswordPolicy policy, Function<String, String> encode) {
        this.identities = identities; this.policy = policy; this.encode = encode;
    }
    public void register(String email, String password) {
        AccountIdentity identity = new AccountIdentity(email);
        policy.validate(password);
        // Hash even on duplicate registration. No account/membership/session is activated here.
        identities.registerPending(UUID.randomUUID(), identity.value(), encode.apply(password));
    }
}
