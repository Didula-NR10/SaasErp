package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.IdentityRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

/** Delegates password verification/rehashing to Spring; adds the company authority contract. */
public final class CompanyAuthenticationProvider implements AuthenticationProvider {
    private final DaoAuthenticationProvider passwords;
    private final IdentityRepository identities;
    public CompanyAuthenticationProvider(DaoAuthenticationProvider passwords, IdentityRepository identities) {
        this.passwords = passwords; this.identities = identities;
    }
    @Override public Authentication authenticate(Authentication input) {
        Authentication verified = passwords.authenticate(input);
        AccountUserDetails user = (AccountUserDetails) verified.getPrincipal();
        if (!user.account().canUsePasswordSession() || !(input.getDetails() instanceof String hostname)) {
            throw new BadCredentialsException("Authentication failed");
        }
        var grant = identities.createGrant(user.account().id(), user.account().securityVersion(), hostname)
            .orElseThrow(() -> new BadCredentialsException("Authentication failed"));
        return UsernamePasswordAuthenticationToken.authenticated(grant, null,
            List.of(new SimpleGrantedAuthority("COMPANY_SESSION")));
    }
    @Override public boolean supports(Class<?> type) { return type == UsernamePasswordAuthenticationToken.class; }
}
