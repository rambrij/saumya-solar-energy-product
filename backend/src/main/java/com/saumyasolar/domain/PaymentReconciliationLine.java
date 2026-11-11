package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_reconciliation_line", uniqueConstraints =
        @UniqueConstraint(name = "uq_payment_reconciliation_line", columnNames = {"tenant_id", "report_id", "provider_reference", "outcome"}))
@Getter @Setter @NoArgsConstructor
public class PaymentReconciliationLine {
    @Id UUID id;
    UUID tenantId;
    UUID reportId;
    @Column(length = 150) String providerReference;
    @Column(precision = 19, scale = 4) BigDecimal amount;
    @Column(length = 3, columnDefinition = "CHAR(3)") String currency;
    @Column(length = 32) String outcome;
    UUID paymentId;
    @Column(length = 500) String details;
}
