package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="lead_follow_up_task")
@Getter @Setter @NoArgsConstructor
public class LeadFollowUpTask {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="lead_id",nullable=false) private UUID leadId;
    @Column(name="assigned_to",nullable=false) private UUID assignedTo;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="due_at",nullable=false) private Instant dueAt;
    @Column(nullable=false,length=20) private String status;
    @Column(length=1000) private String note;
    @Column(name="reminder_sent_at") private Instant reminderSentAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
