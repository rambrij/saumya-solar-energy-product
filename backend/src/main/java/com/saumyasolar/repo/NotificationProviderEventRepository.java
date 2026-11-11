package com.saumyasolar.repo;

import com.saumyasolar.domain.NotificationProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationProviderEventRepository extends JpaRepository<NotificationProviderEvent,UUID> {
    boolean existsByProviderCodeAndProviderEventId(String providerCode,String providerEventId);
}
