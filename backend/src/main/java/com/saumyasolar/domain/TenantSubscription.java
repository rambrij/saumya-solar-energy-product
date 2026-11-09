package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "tenant_subscription")
@Getter
@Setter
@NoArgsConstructor
public class TenantSubscription {
    @Id
    UUID id;
    UUID tenantId;
    UUID planId;
    String status;
    Instant startsAt;
    Instant endsAt;
    Instant renewsAt;
    String externalReference;
    UUID createdBy;
    String billingCycle;
    Instant trialEndsAt;
    boolean autoRenew;
    String notes;
    UUID replacedBySubscriptionId;
    Instant createdAt;
    Instant updatedAt;
}
