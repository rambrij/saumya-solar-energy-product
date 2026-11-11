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
    String providerReference;
    BigDecimal amount;
    @Column(length = 3, columnDefinition = "CHAR(3)") String currency;
    String outcome;
    UUID paymentId;
    String details;
}
