package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_transaction")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransaction {
    @Id UUID id;
    UUID tenantId;
    UUID warehouseId;
    UUID productId;
    UUID orderId;
    UUID installationId;
    UUID serviceRequestId;
    String type;
    BigDecimal quantityDelta;
    BigDecimal reservedDelta;
    BigDecimal damagedDelta;
    @Column(length = 500) String reason;
    UUID actorId;
    Instant createdAt;

    /** Ledger rows are append-only; corrections must be recorded as new movements. */
    @PreUpdate
    private void preventMutation() {
        throw new IllegalStateException("Inventory transactions are immutable; append a compensating movement instead.");
    }

    @PreRemove
    private void preventDeletion() {
        throw new IllegalStateException("Inventory transactions cannot be deleted.");
    }
}
