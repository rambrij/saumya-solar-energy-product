package com.saumyasolar.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;

class AuthenticationRateLimitCleanupTest {
    @Test
    void removesRateLimitBucketsOlderThanTheRetentionWindow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthenticationRateLimitCleanup cleanup = new AuthenticationRateLimitCleanup(jdbc);
        cleanup.removeExpiredBuckets();
        verify(jdbc).update("DELETE FROM auth_rate_limit_bucket WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL 2 DAY");
    }
}
