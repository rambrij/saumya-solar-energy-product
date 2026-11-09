package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "subscription_assignment_history")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionAssignmentHistory {
    @Id
    UUID id;
    UUID tenantId;
    UUID subscriptionId;
    UUID actorId;
    String action;
    String previousStatus;
    String newStatus;
    UUID previousPlanId;
    UUID newPlanId;
    @Column(columnDefinition = "longtext")
    String detailsJson;
    Instant createdAt;
}
