package com.saumyasolar.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthenticationRateLimitCleanup {
    private final JdbcTemplate jdbc;

    public AuthenticationRateLimitCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${app.auth.rate-limit.cleanup-cron:0 17 3 * * *}")
    @Transactional
    public void removeExpiredBuckets() {
        jdbc.update("DELETE FROM auth_rate_limit_bucket WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL 2 DAY");
    }
}
