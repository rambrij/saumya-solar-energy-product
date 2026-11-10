package com.saumyasolar.repo;

import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    List<NotificationDelivery> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    
    List<NotificationDelivery> findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            String status,
            java.time.Instant before
    );
    @Modifying
    @Transactional
    @Query("""
            update NotificationDelivery d
            set d.status = 'PENDING'
            where d.id = :id
              and d.tenantId = :tenantId
              and d.status = 'FAILED'
              and d.nextAttemptAt <= :now
              and d.attemptCount < 5
            """)
    int claimFailedDelivery(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("now") java.time.Instant now
    );

}
