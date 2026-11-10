package com.saumyasolar.service;

import org.springframework.context.ApplicationEvent;
import java.util.UUID;

/** Identifies a persisted tenant-scoped notification delivery to process. */
public final class BusinessNotificationRequestedEvent extends ApplicationEvent {
    private final UUID deliveryId;
    private final UUID tenantId;
    public BusinessNotificationRequestedEvent(Object source, UUID deliveryId, UUID tenantId) {
        super(source);
        if (deliveryId == null || tenantId == null) throw new IllegalArgumentException("deliveryId and tenantId are required");
        this.deliveryId = deliveryId;
        this.tenantId = tenantId;
    }
    public UUID deliveryId() { return deliveryId; }
    public UUID tenantId() { return tenantId; }
}
