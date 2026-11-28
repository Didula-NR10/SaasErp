package com.saaserp.erp.config;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.infrastructure.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
public class AccountProtectionConfig {
    @Bean ProtectionRepository protectionRepository(JdbcTemplate jdbc,PlatformTransactionManager transactions){return new JdbcProtectionRepository(jdbc,transactions);}
    @Bean FactorCryptography factorCryptography(@Value("${app.security.protection.active-key:}") String active,
            @Value("${app.security.protection.keys:}") String keys){return new ProtectedFactorCryptography(active,keys);}
    @Bean PasswordVerification passwordVerification(IdentityRepository identities,PasswordEncoder encoder,JdbcTemplate jdbc){
        return new NativePasswordVerification(AuthenticationConfig.passwordProvider(identities,encoder),jdbc,encoder);
    }
    @Bean WebAuthnCeremonies webAuthnCeremonies(ProtectionRepository store,@Value("${app.security.origins:}")String origins){return new NativeWebAuthnCeremonies(store,origins);}
    @Bean SecurityNotifications securityNotifications(ProtectionRepository store){return store::event;}
    @Bean ProtectionRateLimit protectionRateLimit(StringRedisTemplate redis,@Value("${app.security.session-namespace:hich:auth:sessions}")String namespace){
        var limiter=new AuthenticationThrottle(redis,namespace+":protection");return limiter::allow;
    }
    @Bean AccountProtectionService accountProtectionService(ProtectionRepository store,PasswordVerification passwords,PasswordPolicy policy,
            FactorCryptography crypto,WebAuthnCeremonies webauthn,SecurityNotifications notifications,Clock securityClock){
        return new AccountProtectionService(store,passwords,policy,crypto,webauthn,notifications,securityClock);
    }
}
