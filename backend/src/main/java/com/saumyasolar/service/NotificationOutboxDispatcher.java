package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationOutbox;
import com.saumyasolar.repo.NotificationOutboxRepository;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.NotificationPreferenceRepository;
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
  private final NotificationPreferenceRepository preferences;
  public NotificationOutboxDispatcher(NotificationOutboxRepository outbox, NotificationDeliveryRepository deliveries, NotificationService service, NotificationPreferenceRepository preferences) {
    this.outbox=outbox; this.deliveries=deliveries; this.service=service; this.preferences=preferences;
  }
  @Scheduled(fixedDelayString="${app.messaging.outbox-interval-ms:15000}")
  public void dispatch() {
    Instant now=Instant.now();
    for(UUID tenantId:outbox.findDueTenantIds(now)) {
      try(var scope=TenantExecutionContext.open(tenantId)) {
        List<NotificationOutbox> rows=outbox.findDueByTenant(tenantId,List.of("PENDING","RETRY"),now);
        for(NotificationOutbox row:rows) {
          if(outbox.claim(row.getId(),tenantId,Instant.now())!=1) continue;
          try { var delivery=deliveries.findByIdAndTenantId(row.getDeliveryId(),tenantId).orElseThrow();
            var preference=delivery.getUserId()==null?java.util.Optional.<com.saumyasolar.domain.NotificationPreference>empty():preferences.findByTenantIdAndUserIdAndChannel(tenantId,delivery.getUserId(),delivery.getChannel());
            if(preference.isPresent() && (!preference.get().isEnabled() || (("SMS".equals(delivery.getChannel()) || "WHATSAPP".equals(delivery.getChannel())) && !preference.get().isConsented()))) { delivery.setStatus("SUPPRESSED"); deliveries.save(delivery); row.setStatus("SUCCEEDED"); row.setLastError(null); row.setUpdatedAt(Instant.now()); outbox.save(row); continue; }
            service.processOutboxDelivery(row.getDeliveryId(),tenantId);
            delivery=deliveries.findByIdAndTenantId(row.getDeliveryId(),tenantId).orElseThrow();
            row.setStatus("DELIVERED".equals(delivery.getStatus())?"SUCCEEDED":"DEAD_LETTER".equals(delivery.getStatus())?"DEAD_LETTER":"RETRY");
            row.setAttemptCount(delivery.getAttemptCount()); row.setNextAttemptAt(delivery.getNextAttemptAt()!=null?delivery.getNextAttemptAt():("PENDING".equals(delivery.getStatus())?Instant.now().plusSeconds(30):null)); row.setLastError(delivery.getLastError());
          } catch(RuntimeException ex) { row.setStatus(row.getAttemptCount()>=5?"DEAD_LETTER":"RETRY"); row.setLastError("Outbox dispatch failed"); row.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600,60L << Math.min(row.getAttemptCount(),5)))); }
          row.setUpdatedAt(Instant.now()); outbox.save(row);
        }
      }
    }
  }
}