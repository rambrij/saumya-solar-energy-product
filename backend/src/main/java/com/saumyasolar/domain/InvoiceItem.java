package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;
@Entity
@Table(name = "invoice_item")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceItem {
    @Id
    UUID id;
    UUID tenantId;
    UUID invoiceId;
    UUID productId;
    String description;
    String hsnCode;
    int quantity;
    BigDecimal unitPrice;
    BigDecimal taxPercent;
    BigDecimal taxableAmount;
    BigDecimal taxAmount;
    BigDecimal lineTotal;
}
