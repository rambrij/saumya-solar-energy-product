package com.saumyasolar.api;

import java.time.Instant;
import java.util.UUID;

public final class NotificationModels {
    private NotificationModels() {}
    public record NotificationView(UUID id, String eventType, String subject, String status,
                                   Instant createdAt, Instant sentAt, Instant readAt) {}
}
