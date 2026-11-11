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
    @Column(length = 60) String provider;
    @Column(name = "event_id", length = 180) String eventId;
    @Column(length = 64) String payloadHash;
    @Column(length = 24) String processingStatus;
    @Column(length = 150) String providerRefundReference;
    Instant receivedAt;
    Instant processedAt;
}
