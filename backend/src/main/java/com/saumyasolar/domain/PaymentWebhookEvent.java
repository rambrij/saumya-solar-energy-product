package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_event", uniqueConstraints =
        @UniqueConstraint(name = "uq_payment_webhook_tenant_provider_event", columnNames = {"tenant_id", "provider", "event_id"}))
@Getter @Setter @NoArgsConstructor
public class PaymentWebhookEvent {
    @Id UUID id;
    UUID tenantId;
    String provider;
    @Column(name = "event_id") String eventId;
    String payloadHash;
    String processingStatus;
    String providerRefundReference;
    Instant receivedAt;
    Instant processedAt;
}
