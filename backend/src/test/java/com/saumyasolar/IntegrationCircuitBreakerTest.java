package com.saumyasolar;

import com.saumyasolar.service.IntegrationCircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationCircuitBreakerTest {
    @Test
    void opensAfterConfiguredConsecutiveFailuresAndResetsOnSuccess() {
        IntegrationCircuitBreaker breaker = new IntegrationCircuitBreaker(2, 60);
        assertTrue(breaker.allow("tenant/provider"));
        assertNull(breaker.recordFailure("tenant/provider"));
        Instant open = breaker.recordFailure("tenant/provider");
        assertNotNull(open);
        assertFalse(breaker.allow("tenant/provider"));
        breaker.recordSuccess("tenant/provider");
        assertTrue(breaker.allow("tenant/provider"));
    }
}
