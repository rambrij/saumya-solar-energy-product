package com.saumyasolar.repo;

import com.saumyasolar.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference,UUID> {
    List<NotificationPreference> findByTenantIdAndUserId(UUID tenantId,UUID userId);
    Optional<NotificationPreference> findByTenantIdAndUserIdAndChannel(UUID tenantId,UUID userId,String channel);
}
