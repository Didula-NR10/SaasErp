package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.domain.Account;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Used only during password verification and never saved in the authenticated session. */
public record AccountUserDetails(Account account) implements UserDetails {
    @Override public String getUsername() { return account.email(); }
    @Override public String getPassword() { return account.passwordHash(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return java.util.List.of(); }
    // Account/MFA status is checked after password verification to avoid distinguishing timing.
    @Override public String toString() { return "AccountUserDetails[redacted]"; }
}
