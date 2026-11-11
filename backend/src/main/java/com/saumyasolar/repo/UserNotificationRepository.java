package com.saumyasolar.repo;

import com.saumyasolar.domain.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
    boolean existsByDedupeKey(String dedupeKey);
    Page<UserNotification> findByUserIdAndTenantIdOrderByCreatedAtDesc(UUID userId, UUID tenantId, Pageable pageable);
    Page<UserNotification> findByUserIdAndTenantIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, UUID tenantId, Pageable pageable);
    Page<UserNotification> findByUserIdAndTenantIdIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<UserNotification> findByUserIdAndTenantIdIsNullAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    long countByUserIdAndTenantIdAndReadAtIsNull(UUID userId, UUID tenantId);
    long countByUserIdAndTenantIdIsNullAndReadAtIsNull(UUID userId);
    Optional<UserNotification> findByIdAndUserIdAndTenantId(UUID id, UUID userId, UUID tenantId);
    Optional<UserNotification> findByIdAndUserIdAndTenantIdIsNull(UUID id, UUID userId);

    @Modifying
    @Query("update UserNotification n set n.readAt=:now where n.userId=:userId and n.tenantId=:tenantId and n.readAt is null")
    int markAllTenantRead(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId, @Param("now") Instant now);

    @Modifying
    @Query("update UserNotification n set n.readAt=:now where n.userId=:userId and n.tenantId is null and n.readAt is null")
    int markAllPlatformRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
