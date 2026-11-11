package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationOutbox;
import com.saumyasolar.repo.NotificationOutboxRepository;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.security.TenantExecutionContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationOutboxDispatcher {
  private final NotificationOutboxRepository outbox;
  private final NotificationDeliveryRepository deliveries;
  private final NotificationService service;
  public NotificationOutboxDispatcher(NotificationOutboxRepository outbox, NotificationDeliveryRepository deliveries, NotificationService service) {
    this.outbox=outbox; this.deliveries=deliveries; this.service=service;
  }
  @Scheduled(fixedDelayString="${app.messaging.outbox-interval-ms:15000}")
  public void dispatch() {
    Instant now=Instant.now();
    for(UUID tenantId:outbox.findDueTenantIds(now)) {
      try(var scope=TenantExecutionContext.open(tenantId)) {
        List<NotificationOutbox> rows=outbox.findTop50ByTenantIdAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(tenantId,List.of("PENDING","RETRY"),now);
        for(NotificationOutbox row:rows) {
          if(outbox.claim(row.getId(),tenantId,Instant.now())!=1) continue;
          try { service.processPendingDelivery(row.getDeliveryId(),tenantId);
            var delivery=deliveries.findByIdAndTenantId(row.getDeliveryId(),tenantId).orElseThrow();
            row.setStatus("DELIVERED".equals(delivery.getStatus())?"SUCCEEDED":"DEAD_LETTER".equals(delivery.getStatus())?"DEAD_LETTER":"RETRY");
            row.setAttemptCount(delivery.getAttemptCount()); row.setNextAttemptAt(delivery.getNextAttemptAt()); row.setLastError(delivery.getLastError());
          } catch(RuntimeException ex) { row.setStatus(row.getAttemptCount()>=5?"DEAD_LETTER":"RETRY"); row.setLastError("Outbox dispatch failed"); row.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600,60L << Math.min(row.getAttemptCount(),5)))); }
          row.setUpdatedAt(Instant.now()); outbox.save(row);
        }
      }
    }
  }
}