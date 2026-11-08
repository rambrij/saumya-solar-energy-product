package com.saumyasolar.repo;

import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    List<NotificationDelivery> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
