package com.saumyasolar.repo;

import com.saumyasolar.domain.NotificationOutbox;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    @Query("select distinct o.tenantId from NotificationOutbox o where o.status in ('PENDING','RETRY') and (o.nextAttemptAt is null or o.nextAttemptAt<=:now)")
    List<UUID> findDueTenantIds(@Param("now") Instant now);
    List<NotificationOutbox> findTop50ByTenantIdAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(UUID tenantId,List<String> statuses,Instant now);
    Optional<NotificationOutbox> findByDeliveryIdAndTenantId(UUID deliveryId,UUID tenantId);
    @Modifying @Query("update NotificationOutbox o set o.status='PROCESSING',o.attemptCount=o.attemptCount+1,o.updatedAt=:now where o.id=:id and o.tenantId=:tenantId and o.status in ('PENDING','RETRY') and (o.nextAttemptAt is null or o.nextAttemptAt<=:now)")
    int claim(@Param("id") UUID id,@Param("tenantId") UUID tenantId,@Param("now") Instant now);
    List<NotificationOutbox> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
