package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "approval_instance")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalInstance {
    @Id
    UUID id;
    UUID tenantId;
    UUID workflowId;
    String entityType;
    String entityId;
    @Column(columnDefinition = "longtext")
    String stepsSnapshotJson;
    String status;
    int currentStep;
    UUID requestedBy;
    Instant requestedAt;
    Instant completedAt;
}
