package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification")
@Getter
@Setter
@NoArgsConstructor
public class UserNotification {
    @Id
    UUID id;
    @Column(name = "tenant_id")
    UUID tenantId;
    @Column(name = "user_id", nullable = false)
    UUID userId;
    @Column(name = "event_type", nullable = false, length = 60)
    String eventType;
    @Column(nullable = false, length = 255)
    String title;
    @Column(nullable = false, length = 4000)
    String body;
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 64)
    String dedupeKey;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "read_at")
    Instant readAt;
}
