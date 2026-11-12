package com.saumyasolar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthenticationRateLimitConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void concurrentRequestsShareTheAtomicAccountBucketAcrossWorkerThreads() throws Exception {
        String account = "rate-limit-" + UUID.randomUUID() + "@example.test";
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                jdbc, mapper, new ClientAddressResolver(""),
                10, 100, 100, 100, 100, 100, 60
        );
        int requests = 24;
        var pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
                    request.setServletPath("/api/v1/auth/login");
                    request.setRemoteAddr("192.0.2." + (index + 1));
                    request.setContentType("application/json");
                    request.setContent(("{\"email\":\"" + account + "\",\"password\":\"not-a-real-password\"}")
                            .getBytes(StandardCharsets.UTF_8));
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    filter.doFilter(request, response, new MockFilterChain());
                    return response.getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not reach the start barrier");
            start.countDown();
            int accepted = 0;
            int limited = 0;
            for (var future : futures) {
                int status = future.get(20, TimeUnit.SECONDS);
                if (status == 200) accepted++;
                else if (status == 429) limited++;
                else fail("Unexpected rate-limit response status: " + status);
            }
            assertTrue(accepted <= 10, "The shared account bucket must never allow more than its configured limit");
            assertTrue(limited >= requests - 10, "Requests above the shared account limit must be rejected");
            assertEquals(requests, accepted + limited);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
