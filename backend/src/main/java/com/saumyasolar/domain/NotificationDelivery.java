package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery")
@Getter
@Setter
@NoArgsConstructor
public class NotificationDelivery {
    @Id
    UUID id;
    UUID tenantId;
    UUID userId;
    @Column(nullable = false, length = 60)
    String eventType;
    @Column(nullable=false,length=20) String channel = "EMAIL";
    @Column(length=120) String idempotencyKey;
    @Column(length=120) String providerMessageId;
    @Column(columnDefinition="TEXT") String messageBody;
    Instant nextAttemptAt;
    @Column(nullable = false, length = 320)
    String recipient;
    @Column(nullable = false, length = 255)
    String subject;
    @Column(nullable = false, length = 20)
    String status;
    int attemptCount;
    Instant createdAt;
    Instant sentAt;
    @Column(length = 1000)
    String lastError;
}
