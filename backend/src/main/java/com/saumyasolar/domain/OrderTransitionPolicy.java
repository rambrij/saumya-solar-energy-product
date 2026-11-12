package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_transition_policy")
@Getter
@Setter
@NoArgsConstructor
public class OrderTransitionPolicy {
    @Id
    UUID id;
    UUID tenantId;
    String fromStatus;
    String toStatus;
    String requiredPermission;
    boolean enabled = true;
    Instant createdAt;
    Instant updatedAt;
}
