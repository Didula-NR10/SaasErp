package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.PasswordVerification;
import com.saaserp.erp.platform.identity.application.ProtectionFailure;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class NativePasswordVerification implements PasswordVerification {
    private final DaoAuthenticationProvider provider;private final JdbcTemplate jdbc;private final PasswordEncoder encoder;
    public NativePasswordVerification(DaoAuthenticationProvider provider,JdbcTemplate jdbc,PasswordEncoder encoder){this.provider=provider;this.jdbc=jdbc;this.encoder=encoder;}
    @Override public Verified verify(String email,String password) {
        var authenticated=provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email,password));
        var user=(AccountUserDetails)authenticated.getPrincipal();return new Verified(user.account().id(),user.account().securityVersion());
    }
    @Override public String currentHash(UUID account) {
        var values=jdbc.query("SELECT password_hash FROM platform.accounts WHERE id=?",(rs,n)->rs.getString(1),account);
        if(values.size()!=1)throw new ProtectionFailure();return values.getFirst();
    }
    @Override public boolean matches(String password,String encoded){return encoder.matches(password,encoded);}
    @Override public String encode(String password){return encoder.encode(password);}
}
