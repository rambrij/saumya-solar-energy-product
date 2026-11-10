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
    
    @Query("""
            select distinct d.tenantId from NotificationDelivery d
            where d.status = :status and d.nextAttemptAt < :before and d.tenantId is not null
            """)
    List<UUID> findDistinctTenantIdsForRetry(
            @Param("status") String status,
            @Param("before") java.time.Instant before
    );

    List<NotificationDelivery> findTop50ByTenantIdAndStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            UUID tenantId,
            String status,
            java.time.Instant before
    );

    @Query("""
            select distinct d.tenantId from NotificationDelivery d
            where d.status = 'PENDING' and d.tenantId is not null
              and (d.nextAttemptAt is null or d.nextAttemptAt <= :staleBefore)
            """)
    List<UUID> findDistinctTenantIdsForPending(@Param("staleBefore") java.time.Instant staleBefore);

    List<NotificationDelivery> findTop50ByTenantIdAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            UUID tenantId, String status, java.time.Instant before
    );

    @Modifying
    @Transactional
    @Query("""
            update NotificationDelivery d
            set d.nextAttemptAt = :now
            where d.id = :id and d.tenantId = :tenantId and d.status = 'PENDING'
              and (d.nextAttemptAt is null or d.nextAttemptAt <= :staleBefore)
            """)
    int claimPendingDelivery(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("now") java.time.Instant now, @Param("staleBefore") java.time.Instant staleBefore
    );

    java.util.Optional<NotificationDelivery> findByIdAndTenantId(UUID id, UUID tenantId);

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
