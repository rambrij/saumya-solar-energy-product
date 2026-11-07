package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Quotation {
    @Id
    UUID id;
    UUID tenantId;
    UUID quoteGroupId;
    String quotationNumber;
    int revision;
    UUID customerId;
    UUID leadId;
    UUID surveyId;
    String status;
    BigDecimal subtotal;
    BigDecimal discountPercent;
    BigDecimal discountAmount;
    BigDecimal taxAmount;
    BigDecimal grandTotal;
    LocalDate validUntil;
    boolean approvalRequired;
    UUID decisionBy;
    Instant decisionAt;
    UUID createdBy;
    Instant createdAt;
}
