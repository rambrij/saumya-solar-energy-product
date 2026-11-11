package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refund")
@Getter @Setter @NoArgsConstructor
public class Refund {
    @Id UUID id;
    UUID tenantId;
    UUID paymentId;
    BigDecimal amount;
    String reason;
    String status;
    UUID recordedBy;
    @Column(length = 60) String provider;
    @Column(length = 150) String providerRefundReference;
    @Column(length = 160) String idempotencyKey;
    Instant processedAt;
    Instant createdAt;
}
