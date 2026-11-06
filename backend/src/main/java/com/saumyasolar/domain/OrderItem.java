package com.saumyasolar.domain;

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
    UUID tenantId;
    UUID orderId;
    UUID productId;
    UUID packageId;
    String description;
    int quantity;
    BigDecimal unitPrice;
    BigDecimal lineTotal;
}
