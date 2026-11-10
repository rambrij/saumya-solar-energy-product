package com.saumyasolar;

import com.saumyasolar.domain.IntegrationSyncJob;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.IntegrationSyncJobRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.TenantExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.integrations.dispatch-interval-ms=3600000")
@ActiveProfiles("test")
class TenantSchedulerConcurrencyIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired IntegrationSyncJobRepository jobs;

    @Test
    void concurrentWorkersCanClaimOnlyOneTimeAndOnlyWithinTheOwningTenant() throws Exception {
        Tenant tenantA = tenant("worker-a");
        Tenant tenantB = tenant("worker-b");
        tenants.saveAllAndFlush(List.of(tenantA, tenantB));

        IntegrationSyncJob jobA = job(tenantA.getId());
        IntegrationSyncJob jobB = job(tenantB.getId());
        jobs.saveAllAndFlush(List.of(jobA, jobB));

        Instant now = Instant.now();
        Instant staleBefore = now.minusSeconds(600);
        assertEquals(0, jobs.claimIfDue(jobA.getId(), tenantB.getId(), now, staleBefore));
        assertEquals(0, jobs.claimIfDue(jobB.getId(), tenantA.getId(), now, staleBefore));

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> claim(jobA, tenantA, now, staleBefore, ready, start));
            var second = pool.submit(() -> claim(jobA, tenantA, now, staleBefore, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both worker threads should be ready");
            start.countDown();
            int claimed = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, claimed, "Atomic claim must allow exactly one concurrent worker");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals("PROCESSING", jobs.findByIdAndTenantId(jobA.getId(), tenantA.getId()).orElseThrow().getStatus());
        assertEquals("QUEUED", jobs.findByIdAndTenantId(jobB.getId(), tenantB.getId()).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId,
                "No tenant execution context may leak into the test thread");
    }

    private int claim(IntegrationSyncJob job, Tenant tenant, Instant now, Instant staleBefore,
                      CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent claim start timed out");
        return jobs.claimIfDue(job.getId(), tenant.getId(), now, staleBefore);
    }

    private Tenant tenant(String prefix) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(prefix + " " + UUID.randomUUID());
        tenant.setSlug(prefix + "-" + UUID.randomUUID().toString().replace("-", ""));
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        return tenant;
    }

    private IntegrationSyncJob job(UUID tenantId) {
        IntegrationSyncJob job = new IntegrationSyncJob();
        job.setId(UUID.randomUUID());
        job.setTenantId(tenantId);
        job.setConnectionId(UUID.randomUUID());
        job.setEntityType("ORDER");
        job.setEntityId(UUID.randomUUID().toString());
        job.setOperation("UPSERT");
        job.setIdempotencyKey(UUID.randomUUID().toString());
        job.setPayloadJson("{}");
        job.setStatus("QUEUED");
        job.setAttemptCount(0);
        job.setNextAttemptAt(Instant.now().minusSeconds(60));
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
