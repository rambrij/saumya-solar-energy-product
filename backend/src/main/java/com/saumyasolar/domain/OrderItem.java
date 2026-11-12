package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    @Id
    UUID id;
    @Column(nullable = false)
    UUID tenantId;
    UUID orderId;
    UUID productId;
    UUID packageId;
    String description;
    int quantity;
    BigDecimal unitPrice;
    BigDecimal taxPercent;
    BigDecimal lineTotal;
    BigDecimal discountAmount;
    BigDecimal taxableAmount;
    BigDecimal taxAmount;
}
