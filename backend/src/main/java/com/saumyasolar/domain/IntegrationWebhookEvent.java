package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "integration_webhook_event")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationWebhookEvent {
    @Id
    UUID id;
    UUID tenantId;
    String providerCode;
    String providerEventId;
    String payloadHash;
    String status;
    Instant receivedAt;
    Instant processedAt;
}
