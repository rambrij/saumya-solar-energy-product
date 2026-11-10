package com.saumyasolar.service;

import com.saumyasolar.domain.AuditEvent;
import com.saumyasolar.domain.TenantSubscription;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.repo.TenantSubscriptionRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.TenantExecutionContext;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Component
public class SubscriptionExpiryScheduler {
    private final TenantSubscriptionRepository subscriptions;
    private final AuditEventRepository audits;
    private final TenantRepository tenants;
    
    public SubscriptionExpiryScheduler(TenantSubscriptionRepository s, AuditEventRepository a, TenantRepository tenants) {
        subscriptions = s;
        audits = a;
        this.tenants = tenants;
    }
    
    @Scheduled(cron = "${app.subscription.expiry-cron:0 15 * * * *}")
    @Transactional
    public void expireSubscriptions() {
        Instant now = Instant.now();
        for (Tenant tenant : tenants.findAll()) {
            try (var scope = TenantExecutionContext.open(tenant.getId())) {
            List<TenantSubscription> expired = subscriptions.findByTenantIdAndStatusInAndEndsAtBefore(
                    TenantExecutionContext.requireTenantId(), List.of("TRIAL", "ACTIVE", "PAST_DUE"), now);
            for (TenantSubscription s : expired) {
            s.setStatus("EXPIRED");
            s.setUpdatedAt(now);
            subscriptions.save(s);
            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID());
            event.setTenantId(s.getTenantId());
            event.setActorType("SYSTEM");
            event.setAction("SUBSCRIPTION_EXPIRED");
            event.setEntityType("TENANT_SUBSCRIPTION");
            event.setEntityId(s.getId().toString());
            event.setOutcome("SUCCESS");
            event.setCreatedAt(now);
            audits.save(event);
            }
            }
        }
    }
}
