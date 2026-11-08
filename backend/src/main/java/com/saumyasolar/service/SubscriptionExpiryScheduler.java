package com.saumyasolar.service;
import com.saumyasolar.domain.AuditEvent;import com.saumyasolar.domain.TenantSubscription;import com.saumyasolar.repo.AuditEventRepository;import com.saumyasolar.repo.TenantSubscriptionRepository;
import jakarta.transaction.Transactional;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
import java.time.Instant;import java.util.List;import java.util.UUID;
@Component
public class SubscriptionExpiryScheduler {
 private final TenantSubscriptionRepository subscriptions;private final AuditEventRepository audits;
 public SubscriptionExpiryScheduler(TenantSubscriptionRepository s,AuditEventRepository a){subscriptions=s;audits=a;}
 @Scheduled(cron="${app.subscription.expiry-cron:0 15 * * * *}")
 @Transactional public void expireSubscriptions(){
  Instant now=Instant.now();List<TenantSubscription> expired=subscriptions.findByStatusInAndEndsAtBefore(List.of("TRIAL","ACTIVE","PAST_DUE"),now);
  for(TenantSubscription s:expired){s.setStatus("EXPIRED");s.setUpdatedAt(now);subscriptions.save(s);
   AuditEvent event=new AuditEvent();event.setId(UUID.randomUUID());event.setTenantId(s.getTenantId());event.setActorType("SYSTEM");event.setAction("SUBSCRIPTION_EXPIRED");
   event.setEntityType("TENANT_SUBSCRIPTION");event.setEntityId(s.getId().toString());event.setOutcome("SUCCESS");event.setCreatedAt(now);audits.save(event);}
 }
}
