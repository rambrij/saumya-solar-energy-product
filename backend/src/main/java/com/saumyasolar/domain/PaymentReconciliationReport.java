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
    @Column(length = 60) String provider;
    @Column(length = 160) String reportReference;
    @Column(length = 3, columnDefinition = "CHAR(3)") String currency;
    @Column(precision = 19, scale = 4) BigDecimal reportedTotal;
    @Column(precision = 19, scale = 4) BigDecimal matchedTotal;
    Integer mismatchCount;
    @Column(length = 24) String status;
    @Column(length = 64) String payloadHash;
    UUID createdBy;
    Instant createdAt;
    Instant processedAt;
}
