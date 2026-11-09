package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "service_event")
@Getter
@Setter
@NoArgsConstructor
public class ServiceEvent {
    @Id
    UUID id;
    UUID tenantId;
    UUID serviceRequestId;
    UUID actorId;
    String eventType;
    String oldStatus;
    String newStatus;
    @Column(length = 2000)
    String reason;
    Instant occurredAt;
}
