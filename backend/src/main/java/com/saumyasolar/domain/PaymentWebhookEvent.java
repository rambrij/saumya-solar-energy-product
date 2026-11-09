package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "payment_webhook_event", uniqueConstraints = @UniqueConstraint(columnNames = { "provider", "event_id" }))
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookEvent {
    @Id
    UUID id;
    String provider;
    @Column(name = "event_id")
    String eventId;
    String payloadHash;
    String processingStatus;
    Instant receivedAt;
    Instant processedAt;
}
