package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationStorageFailureTest {
    @Test void redisLimiterFailureDoesNotPermitAnAttempt() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
            .thenThrow(new RedisConnectionFailureException("Untrusted connection detail"));
        assertThatThrownBy(() -> new AuthenticationThrottle(redis,"test:auth").allow("source","account"))
            .isInstanceOf(RedisConnectionFailureException.class);
    }
    @Test void redisLoadAndCommitFailuresAndPostgresRevocationFailureCannotAcknowledgeSuccess() throws Exception {
        for (var failure : new org.springframework.dao.DataAccessException[] {
                new RedisConnectionFailureException("redis load"), new RedisConnectionFailureException("redis save"),
                new DataAccessResourceFailureException("postgres revocation")}) {
            var response = new MockHttpServletResponse();
            new SecurityFailureBoundary().doFilter(new MockHttpServletRequest(),response,(req,res) -> {
                response.setStatus(204); throw failure;
            });
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"security_unavailable\"}");
        }
    }
}
