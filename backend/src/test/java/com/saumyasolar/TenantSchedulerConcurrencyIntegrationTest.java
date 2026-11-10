package com.saumyasolar;

import com.saumyasolar.domain.IntegrationSyncJob;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.domain.TenantSubscription;
import com.saumyasolar.domain.ServiceRequest;
import com.saumyasolar.domain.Warranty;
import com.saumyasolar.repo.ServiceRequestRepository;
import com.saumyasolar.repo.ServiceEventRepository;
import com.saumyasolar.repo.WarrantyRepository;
import com.saumyasolar.service.ServiceSlaScheduler;
import com.saumyasolar.service.WarrantyExpiryScheduler;
import com.saumyasolar.service.FinanceService;
import com.saumyasolar.repo.TenantSubscriptionRepository;
import com.saumyasolar.service.SubscriptionExpiryScheduler;
import com.saumyasolar.repo.IntegrationSyncJobRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.TenantExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
    @Autowired TenantSubscriptionRepository subscriptions;
    @Autowired SubscriptionExpiryScheduler expiryScheduler;
    @Autowired ServiceSlaScheduler slaScheduler;
    @Autowired WarrantyExpiryScheduler warrantyExpiryScheduler;
    @Autowired ServiceRequestRepository serviceRequests;
    @Autowired ServiceEventRepository serviceEvents;
    @Autowired WarrantyRepository warranties;

    @DynamicPropertySource
    static void optionalMySqlTestDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("PHASE2_MYSQL_IT_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PHASE2_MYSQL_IT_USER", "root"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PHASE2_MYSQL_IT_PASSWORD", "rootpass"));
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.flyway.enabled", () -> true);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.sql.init.mode", () -> "never");
            registry.add("app.seed.enabled", () -> false);
        }
    }

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

    @Test
    void expirySchedulerProcessesOnlyExpiredRowsForTheirOwningTenants() {
        Tenant expiredTenant = tenant("expiry-a");
        Tenant activeTenant = tenant("expiry-b");
        tenants.saveAllAndFlush(List.of(expiredTenant, activeTenant));

        TenantSubscription expired = subscription(expiredTenant.getId(), Instant.now().minusSeconds(60));
        TenantSubscription stillActive = subscription(activeTenant.getId(), Instant.now().plusSeconds(3600));
        subscriptions.saveAllAndFlush(List.of(expired, stillActive));

        expiryScheduler.expireSubscriptions();

        assertEquals("EXPIRED", subscriptions.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals("TRIAL", subscriptions.findById(stillActive.getId()).orElseThrow().getStatus());
    }

    @Test
    void slaSchedulerScopesConcurrentTenantEvaluationsAndDoesNotTouchFutureTenantWork() throws Exception {
        Tenant overdueTenant = tenant("sla-overdue");
        Tenant futureTenant = tenant("sla-future");
        tenants.saveAllAndFlush(List.of(overdueTenant, futureTenant));

        ServiceRequest overdue = serviceRequest(overdueTenant.getId(), Instant.now().minusSeconds(120));
        ServiceRequest future = serviceRequest(futureTenant.getId(), Instant.now().plusSeconds(3600));
        serviceRequests.saveAllAndFlush(List.of(overdue, future));

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> runScheduler(ready, start));
            var second = pool.submit(() -> runScheduler(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        ServiceRequest overdueAfter = serviceRequests.findByIdAndTenantId(overdue.getId(), overdueTenant.getId()).orElseThrow();
        ServiceRequest futureAfter = serviceRequests.findByIdAndTenantId(future.getId(), futureTenant.getId()).orElseThrow();
        assertNotNull(overdueAfter.getResponseBreachedAt());
        assertNull(futureAfter.getResponseBreachedAt());
        assertEquals(1, serviceEvents.countByTenantIdAndServiceRequestIdAndEventType(
                overdueTenant.getId(), overdue.getId(), "SLA_RESPONSE_BREACHED"));
        assertEquals(0, serviceEvents.countByTenantIdAndServiceRequestIdAndEventType(
                futureTenant.getId(), future.getId(), "SLA_RESPONSE_BREACHED"));
        assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId);
    }

    @Test
    void warrantyExpirySchedulerScopesConcurrentTenantEvaluations() throws Exception {
        Tenant expiredTenant = tenant("warranty-expired");
        Tenant validTenant = tenant("warranty-valid");
        tenants.saveAllAndFlush(List.of(expiredTenant, validTenant));

        Warranty expired = warranty(expiredTenant.getId(), java.time.LocalDate.now().minusDays(1));
        Warranty valid = warranty(validTenant.getId(), java.time.LocalDate.now().plusDays(90));
        warranties.saveAllAndFlush(List.of(expired, valid));

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> runWarrantyScheduler(ready, start));
            var second = pool.submit(() -> runWarrantyScheduler(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals("EXPIRED", warranties.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals("ACTIVE", warranties.findById(valid.getId()).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId);
    }

    private void runScheduler(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStart(start, "SLA scheduler");
        slaScheduler.evaluate();
    }

    private void runWarrantyScheduler(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStart(start, "Warranty scheduler");
        warrantyExpiryScheduler.evaluateWarrantyExpiry();
    }

    private void awaitStart(CountDownLatch start, String worker) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(worker + " start timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(worker + " was interrupted", interrupted);
        }
    }

    private ServiceRequest serviceRequest(UUID tenantId, Instant responseDueAt) {
        ServiceRequest request = new ServiceRequest();
        request.setId(UUID.randomUUID());
        request.setTenantId(tenantId);
        request.setTicketNumber("SLA-" + UUID.randomUUID().toString().substring(0, 8));
        request.setCategory("INSTALLATION");
        request.setPriority("HIGH");
        request.setDescription("Scheduler tenant-isolation test");
        request.setStatus("OPEN");
        request.setResponseDueAt(responseDueAt);
        request.setResolutionDueAt(Instant.now().plusSeconds(7200));
        request.setPausedSeconds(0);
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        return request;
    }

    private Warranty warranty(UUID tenantId, java.time.LocalDate endDate) {
        Warranty warranty = new Warranty();
        warranty.setId(UUID.randomUUID());
        warranty.setTenantId(tenantId);
        warranty.setStatus("ACTIVE");
        warranty.setStartDate(java.time.LocalDate.now().minusDays(30));
        warranty.setEndDate(endDate);
        warranty.setQuantity(1);
        warranty.setActivatedAt(Instant.now());
        return warranty;
    }

    private TenantSubscription subscription(UUID tenantId, Instant endsAt) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setTenantId(tenantId);
        subscription.setStatus("TRIAL");
        subscription.setStartsAt(Instant.now().minusSeconds(3600));
        subscription.setEndsAt(endsAt);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        return subscription;
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
