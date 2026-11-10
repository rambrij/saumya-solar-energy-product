package com.saumyasolar.service;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
@Component
public class IntegrationSyncScheduler {
    private final IntegrationSyncJobRepository jobs;
    private final IntegrationConnectionRepository connections;
    private final List<IntegrationAdapter> adapters;
    private final IntegrationCircuitBreaker breaker;
    private final AccountingAdapterRegistry accountingAdapters;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final com.saumyasolar.repo.TenantRepository tenants;
    
    public IntegrationSyncScheduler(
            IntegrationSyncJobRepository j,
            IntegrationConnectionRepository c,
            List<IntegrationAdapter> a,
            IntegrationCircuitBreaker breaker,
            AccountingAdapterRegistry accountingAdapters,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            com.saumyasolar.repo.TenantRepository tenants
    ) {
        jobs = j;
        connections = c;
        adapters = a;
        this.breaker = breaker;
        this.accountingAdapters = accountingAdapters;
        this.mapper = mapper;
        this.tenants = tenants;
    }
    
    @Scheduled(fixedDelayString = "${app.integrations.dispatch-interval-ms:30000}")
    public void dispatch() {
        Instant now = Instant.now(), staleBefore = now.minusSeconds(600);
        for (com.saumyasolar.domain.Tenant tenant : tenants.findAll()) {
            List<IntegrationSyncJob> batch = new ArrayList<>(jobs.findTop50ByTenantIdAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(tenant.getId(), List.of("QUEUED", "RETRY"), now));
            batch.addAll(jobs.findTop50ByTenantIdAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(tenant.getId(), "PROCESSING", staleBefore));
            for (IntegrationSyncJob candidate : batch) {
            Instant claimedAt = Instant.now();
            if (jobs.claimIfDue(candidate.getId(), tenant.getId(), claimedAt, staleBefore) != 1) continue;
            IntegrationSyncJob job = jobs.findByIdAndTenantId(candidate.getId(), tenant.getId()).orElse(null);
            if (job == null) continue;
            Optional<IntegrationConnection> c = connections.findByIdAndTenantId(job.getConnectionId(), job.getTenantId());
            if (c.isEmpty() || !"ACTIVE".equals(c.get().getStatus())) {
                job.setStatus("DEAD_LETTER");
                job.setLastError("Integration connection is unavailable");
                job.setUpdatedAt(Instant.now());
                jobs.save(job);
                continue;
            }
            boolean accounting = "ACCOUNTING".equals(c.get().getIntegrationType());
            Optional<IntegrationAdapter> generic = accounting ? Optional.empty() : adapters.stream()
                    .filter(a -> a.providerCode().equalsIgnoreCase(c.get().getProviderCode())).findFirst();
            Optional<AccountingAdapter> accountingAdapter = accounting ? accountingAdapters.find(c.get().getProviderCode()) : Optional.empty();
            if (( accounting && accountingAdapter.isEmpty() ) || ( !accounting && generic.isEmpty() )) {
                job.setStatus("WAITING_FOR_ADAPTER");
                job.setLastError("No compatible provider adapter is installed for " + c.get().getProviderCode());
                job.setUpdatedAt(Instant.now());
                jobs.save(job);
                continue;
            }
            String circuitKey = job.getTenantId() + ":" + c.get().getProviderCode();
            if (!breaker.allow(circuitKey)) {
                job.setStatus("RETRY");
                job.setLastError("Provider circuit is open; retry deferred");
                Instant openUntil = breaker.openUntil(circuitKey);
                job.setNextAttemptAt(openUntil == null ? Instant.now().plusSeconds(60) : openUntil);
                job.setUpdatedAt(Instant.now());
                jobs.save(job);
                continue;
            }
            try {
                if (accounting) {
                    AccountingAdapter adapter = accountingAdapter.orElseThrow();
                    switch (job.getEntityType()) {
                        case "INVOICE" -> {
                            var payload = mapper.readValue(
                                    job.getPayloadJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                    }
                            );
                            var result = adapter.synchronizeInvoice(c.get(), job, payload);
                            if (result == null || !"SUCCEEDED".equals(result.status()))
                                throw new IllegalStateException(result == null ? "Accounting adapter returned no result" : result.message());
                            job.setExternalReference(result.externalReference());
                        }
                        case "PAYMENT" -> {
                            var payload = mapper.readValue(
                                    job.getPayloadJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                    }
                            );
                            var result = adapter.synchronizePayment(c.get(), job, payload);
                            if (result == null || !"SUCCEEDED".equals(result.status()))
                                throw new IllegalStateException(result == null ? "Accounting adapter returned no result" : result.message());
                            job.setExternalReference(result.externalReference());
                        }
                        case "REFUND" -> {
                            var payload = mapper.readValue(
                                    job.getPayloadJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                    }
                            );
                            var result = adapter.synchronizeRefund(c.get(), job, payload);
                            if (result == null || !"SUCCEEDED".equals(result.status()))
                                throw new IllegalStateException(result == null ? "Accounting adapter returned no result" : result.message());
                            job.setExternalReference(result.externalReference());
                        }
                        case "ORDER" -> adapter.synchronize(c.get(), job);
                        default ->
                                throw new IllegalArgumentException("Unsupported accounting entity type: " + job.getEntityType());
                    }
                } else generic.orElseThrow().synchronize(c.get(), job);
                breaker.recordSuccess(circuitKey);
                job.setStatus("SUCCEEDED");
                job.setLastError(null);
            } catch (Exception ex) {
                breaker.recordFailure(circuitKey);
                int n = job.getAttemptCount();
                String msg = ex.getMessage() == null ? "Provider synchronization failed" : ex.getMessage();
                job.setLastError(msg.substring(0, Math.min(1000, msg.length())));
                job.setStatus(n >= 8 ? "DEAD_LETTER" : "RETRY");
                job.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 30L * ( 1L << Math.min(n, 6) ))));
            }
            job.setUpdatedAt(Instant.now());
            jobs.save(job);
            }
        }
    }
}
