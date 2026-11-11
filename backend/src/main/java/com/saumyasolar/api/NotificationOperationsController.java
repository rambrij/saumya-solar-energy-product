package com.saumyasolar.api;
import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.NotificationOutboxRepository;
import com.saumyasolar.repo.IntegrationSyncJobRepository;
import com.saumyasolar.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController @RequestMapping("/api/v1/notifications/operations")
public class NotificationOperationsController {
  private final NotificationDeliveryRepository deliveries; private final NotificationOutboxRepository outbox; private final IntegrationSyncJobRepository jobs;
  public NotificationOperationsController(NotificationDeliveryRepository d,NotificationOutboxRepository o,IntegrationSyncJobRepository j){deliveries=d;outbox=o;jobs=j;}
  @GetMapping("/deliveries") public List<Map<String,Object>> deliveries(Authentication auth){Principal p=(Principal)auth.getPrincipal();require(p);return deliveries.findTop100ByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream().filter(x->x.getUserId().equals(p.userId())||p.roles().contains("VENDOR_ADMIN")).map(x->Map.<String,Object>of("id",x.getId(),"channel",x.getChannel(),"eventType",x.getEventType(),"status",x.getStatus(),"attemptCount",x.getAttemptCount(),"nextAttemptAt",x.getNextAttemptAt()==null?"":x.getNextAttemptAt(),"lastError",x.getLastError()==null?"":x.getLastError(),"createdAt",x.getCreatedAt())).toList();}
  @GetMapping("/outbox") public List<Map<String,Object>> outbox(Authentication auth){Principal p=(Principal)auth.getPrincipal();require(p);return outbox.findTop100ByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream().map(x->Map.<String,Object>of("id",x.getId(),"deliveryId",x.getDeliveryId(),"status",x.getStatus(),"attemptCount",x.getAttemptCount(),"nextAttemptAt",x.getNextAttemptAt()==null?"":x.getNextAttemptAt(),"lastError",x.getLastError()==null?"":x.getLastError())).toList();}
  @GetMapping("/integration-jobs") public List<Map<String,Object>> integrationJobs(Authentication auth){Principal p=(Principal)auth.getPrincipal();require(p);return jobs.findAll().stream().filter(j->p.tenantId().equals(j.getTenantId())).sorted((a,b)->b.getCreatedAt().compareTo(a.getCreatedAt())).limit(100).map(j->Map.<String,Object>of("id",j.getId(),"entityType",j.getEntityType()==null?"":j.getEntityType(),"operation",j.getOperation()==null?"":j.getOperation(),"status",j.getStatus()==null?"":j.getStatus(),"attemptCount",j.getAttemptCount(),"nextAttemptAt",j.getNextAttemptAt()==null?"":j.getNextAttemptAt(),"lastError",j.getLastError()==null?"":j.getLastError())).toList();}
  private void require(Principal p){if(p==null||p.tenantId()==null||!Set.of("VENDOR","PLATFORM").contains(p.accountType()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if("VENDOR".equals(p.accountType())&&(p.permissions()==null||!p.permissions().contains("NOTIFICATION_VIEW")))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
}