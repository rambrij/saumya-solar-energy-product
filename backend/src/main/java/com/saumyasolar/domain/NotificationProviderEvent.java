package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notification_provider_event", uniqueConstraints=@UniqueConstraint(columnNames={"provider_code","provider_event_id"}))
@Getter @Setter @NoArgsConstructor
public class NotificationProviderEvent {
    @Id UUID id;
    @Column(name="tenant_id", nullable=false) UUID tenantId;
    @Column(name="delivery_id", nullable=false) UUID deliveryId;
    @Column(name="provider_code", nullable=false, length=40) String providerCode;
    @Column(name="provider_event_id", nullable=false, length=160) String providerEventId;
    @Column(name="event_type", nullable=false, length=40) String eventType;
    @Column(name="delivery_status", nullable=false, length=20) String deliveryStatus;
    @Column(name="payload_hash", nullable=false, length=64) String payloadHash;
    @Column(name="received_at", nullable=false) Instant receivedAt;
}
