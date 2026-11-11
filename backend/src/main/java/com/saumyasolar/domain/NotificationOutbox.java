package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox")
@Getter @Setter @NoArgsConstructor
public class NotificationOutbox {
    @Id UUID id;
    @Column(name="tenant_id", nullable=false) UUID tenantId;
    @Column(name="delivery_id", nullable=false, unique=true) UUID deliveryId;
    @Column(nullable=false, length=20) String status;
    @Column(name="attempt_count", nullable=false) int attemptCount;
    @Column(name="next_attempt_at") Instant nextAttemptAt;
    @Column(name="last_error", length=1000) String lastError;
    @Column(name="created_at", nullable=false) Instant createdAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
}
