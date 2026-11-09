package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {
    @Id
    UUID id;
    UUID tenantId;
    UUID branchId;
    UUID orderId;
    String invoiceNumber;
    String fiscalYear;
    String status;
    String customerName;
    String customerEmail;
    String customerAddress;
    String customerGstin;
    String vendorName;
    String vendorGstin;
    @Column(length = 3, columnDefinition = "CHAR(3)")
    String currency = "INR";
    BigDecimal subtotal;
    BigDecimal taxTotal;
    BigDecimal cgstAmount = BigDecimal.ZERO;
    BigDecimal sgstAmount = BigDecimal.ZERO;
    BigDecimal igstAmount = BigDecimal.ZERO;
    BigDecimal grandTotal;
    String taxTreatment;
    LocalDate dueDate;
    Instant issuedAt;
    UUID createdBy;
    Instant createdAt;
    String voidReason;
}
