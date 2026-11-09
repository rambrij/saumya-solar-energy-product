package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {
    @Id
    UUID id;
    UUID tenantId;
    UUID branchId;
    UUID actorId;
    String actorType;
    String action;
    String entityType;
    String entityId;
    String outcome;
    @Column(columnDefinition = "longtext")
    String beforeJson;
    @Column(columnDefinition = "longtext")
    String afterJson;
    String reason;
    String correlationId;
    String requestId;
    String sourceIp;
    String userAgent;
    Instant createdAt;
}
