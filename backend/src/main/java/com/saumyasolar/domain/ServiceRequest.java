package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "service_request")
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequest {
    @Id
    UUID id;
    UUID tenantId;
    UUID branchId;
    String ticketNumber;
    UUID customerId;
    UUID installationId;
    UUID productId;
    String serialNumber;
    String category;
    String priority;
    @Column(length = 4000)
    String description;
    String warrantyStatus;
    String status;
    UUID assignedEngineerId;
    Instant responseDueAt;
    Instant resolutionDueAt;
    Instant firstRespondedAt;
    Instant resolvedAt;
    Instant customerConfirmedAt;
    Instant closedAt;
    long pausedSeconds;
    Instant slaPausedAt;
    Instant responseBreachedAt;
    Instant resolutionBreachedAt;
    @Column(length = 2000)
    String resolutionNotes;
    Instant createdAt;
    Instant updatedAt;
}
