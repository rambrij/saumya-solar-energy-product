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
@Table(name = "customer_order")
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrder {
    @Id
    UUID id;
    UUID tenantId;
    UUID quotationId;
    UUID customerId;
    String orderNumber;
    String status;
    BigDecimal total;
    Instant createdAt;
}
