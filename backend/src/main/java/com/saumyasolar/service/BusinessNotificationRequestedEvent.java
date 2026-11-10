package com.saumyasolar.service;

import java.util.UUID;

/** Immutable payload for a tenant-scoped business notification request. */
public record BusinessNotificationRequestedEvent(
        UUID userId, UUID tenantId, String eventType, String recipient, String subject, String body
) {
    public BusinessNotificationRequestedEvent {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
    }
}
