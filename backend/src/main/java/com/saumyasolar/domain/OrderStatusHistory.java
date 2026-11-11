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
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
public class OrderStatusHistory {
    @Id
    UUID id;
    UUID tenantId;
    UUID orderId;
    String oldStatus;
    String newStatus;
    String reason;
    UUID actorId;
    Instant createdAt;
}
