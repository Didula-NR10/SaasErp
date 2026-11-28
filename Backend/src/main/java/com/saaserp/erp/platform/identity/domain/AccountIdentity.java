package com.saaserp.erp.platform.identity.domain;

import java.util.Locale;

/** Deliberately ASCII email identities; no provider-specific alias folding. */
public record AccountIdentity(String value) {
    public AccountIdentity {
        if (value == null) throw new IllegalArgumentException("Invalid identity");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || value.startsWith(".") || value.contains("..") || value.contains(".@") || !value.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) {
            throw new IllegalArgumentException("Invalid identity");
        }
    }
}
