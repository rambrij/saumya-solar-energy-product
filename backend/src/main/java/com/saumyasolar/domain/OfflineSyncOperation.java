package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "offline_sync_operation")
@Getter
@Setter
@NoArgsConstructor
public class OfflineSyncOperation {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID actorId;
    @Column(nullable = false)
    private UUID clientOperationId;
    @Column(nullable = false, length = 40)
    private String operationType;
    @Column(nullable = false)
    private UUID resourceId;
    @Column(nullable = false, length = 64)
    private String payloadHash;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;
    @Column(length = 1000)
    private String errorMessage;
    @Column(nullable = false)
    private Instant clientCreatedAt;
    @Column(nullable = false)
    private Instant receivedAt;
    private Instant appliedAt;
    private Long expectedVersion;
    private Long resultingVersion;
    @Column(length = 64)
    private String conflictCode;
    @Column(nullable = false)
    private int attemptCount;
    private Instant resolvedAt;
    @Column(length = 32)
    private String resolutionAction;
}
