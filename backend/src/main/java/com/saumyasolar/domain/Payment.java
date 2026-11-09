package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    UUID id;
    UUID tenantId;
    UUID invoiceId;
    UUID orderId;
    BigDecimal amount;
    @Column(length = 3, columnDefinition = "CHAR(3)")
    String currency = "INR";
    String method;
    String source;
    String status;
    String reference;
    String provider;
    String providerReference;
    @Column(length = 160)
    String idempotencyKey;
    @Column(length = 1000)
    String checkoutUrl;
    Instant paidAt;
    UUID recordedBy;
    Instant createdAt;
}
