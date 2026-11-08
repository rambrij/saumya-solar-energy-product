package com.saumyasolar.repo;

import com.saumyasolar.domain.NotificationDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    List<NotificationDelivery> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<NotificationDelivery> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
    java.util.Optional<NotificationDelivery> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.status in ('PENDING','FAILED') and d.attemptCount < :maxAttempts and (d.nextAttemptAt is null or d.nextAttemptAt <= :now) order by d.createdAt asc")
    List<NotificationDelivery> findDueForUpdate(@Param("now") Instant now,
                                                 @Param("maxAttempts") int maxAttempts,
                                                 Pageable pageable);
}
