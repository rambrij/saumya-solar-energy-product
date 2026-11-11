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
    private UUID id;
    private UUID tenantId;
    private UUID branchId;
    private UUID actorId;
    private String actorType;
    private String action;
    private String entityType;
    private String entityId;
    private String outcome;
    @Column(columnDefinition = "longtext")
    private String beforeJson;
    @Column(columnDefinition = "longtext")
    private String afterJson;
    private String reason;
    private String correlationId;
    private String requestId;
    private String sourceIp;
    private String userAgent;
    private Instant createdAt;
}
