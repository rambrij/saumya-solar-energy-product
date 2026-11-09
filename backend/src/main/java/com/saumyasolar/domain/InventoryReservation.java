package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "inventory_reservation")
@Getter
@Setter
@NoArgsConstructor
public class InventoryReservation {
    @Id
    UUID id;
    UUID tenantId;
    UUID orderId;
    UUID warehouseId;
    UUID productId;
    BigDecimal quantity;
    String status;
    Instant createdAt;
    Instant updatedAt;
}
