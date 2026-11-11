package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_reconciliation_report", uniqueConstraints =
        @UniqueConstraint(name = "uq_payment_reconciliation_report", columnNames = {"tenant_id", "provider", "report_reference"}))
@Getter @Setter @NoArgsConstructor
public class PaymentReconciliationReport {
    @Id UUID id;
    UUID tenantId;
    String provider;
    String reportReference;
    @Column(length = 3, columnDefinition = "CHAR(3)") String currency;
    BigDecimal reportedTotal;
    BigDecimal matchedTotal;
    Integer mismatchCount;
    String status;
    String payloadHash;
    Instant createdAt;
    Instant processedAt;
}
