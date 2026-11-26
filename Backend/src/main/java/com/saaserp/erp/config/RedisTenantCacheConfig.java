package com.saaserp.erp.config;

import com.saaserp.erp.shared.cache.application.TenantCache;
import com.saaserp.erp.shared.cache.infrastructure.redis.RedisTenantCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Infrastructure wiring only; this does not enable Spring caching or cache business data. */
@Configuration(proxyBeanMethods = false)
public class RedisTenantCacheConfig {

    @Bean
    TenantCache tenantCache(RedisConnectionFactory connectionFactory) {
        // Dedicated template: all serializers are UTF-8 strings; transaction participation is disabled.
        // Do not inject a generic RedisTemplate with Java object serialization or external mutable settings.
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setEnableTransactionSupport(false);
        return new RedisTenantCache(template);
    }
}
