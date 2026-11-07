package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.math.BigDecimal;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="credit_note") @Getter @Setter @NoArgsConstructor
public class CreditNote {@Id UUID id;UUID tenantId;UUID invoiceId;String creditNoteNumber;String reason;BigDecimal amount;String status;UUID createdBy;Instant createdAt;}
