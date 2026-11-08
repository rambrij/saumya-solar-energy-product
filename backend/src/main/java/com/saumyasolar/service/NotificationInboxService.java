package com.saumyasolar.service;

import com.saumyasolar.api.NotificationModels.NotificationView;
import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationInboxService {
    private final NotificationDeliveryRepository deliveries;
    public NotificationInboxService(NotificationDeliveryRepository deliveries) { this.deliveries = deliveries; }

    public List<NotificationView> list(Principal actor) {
        return deliveries.findTop100ByUserIdOrderByCreatedAtDesc(actor.userId()).stream()
                .filter(item -> java.util.Objects.equals(item.getTenantId(), actor.tenantId()))
                .map(this::view).toList();
    }

    @Transactional
    public NotificationView markRead(UUID id, Principal actor) {
        NotificationDelivery item = deliveries.findByIdAndUserId(id, actor.userId())
                .filter(value -> java.util.Objects.equals(value.getTenantId(), actor.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (item.getReadAt() == null) {
            item.setReadAt(Instant.now());
            deliveries.save(item);
        }
        return view(item);
    }

    private NotificationView view(NotificationDelivery item) {
        return new NotificationView(item.getId(), item.getEventType(), item.getSubject(), item.getStatus(),
                item.getCreatedAt(), item.getSentAt(), item.getReadAt());
    }
}
