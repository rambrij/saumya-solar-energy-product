package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Tenant {
    @Id
    UUID id;
    String name;
    String slug;
    String displayName;
    String logoUrl;
    String primaryColor = "#0B6B4B";
    String secondaryColor = "#F6C85F";
    String contactEmail;
    String contactPhone;
    String supportEmail;
    String supportPhone;
    String status;
    BigDecimal discountApprovalThreshold = BigDecimal.TEN;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "updated_at")
    Instant updatedAt;
    String gstin;
    String invoicePrefix = "INV";
    Integer serviceReopenDays;

    /**
     * Keep newly-created tenants valid even when callers do not provide
     * optional branding fields. Hibernate includes null-valued properties in
     * INSERT statements, so database DEFAULT clauses alone are insufficient.
     */
    @PrePersist
    void applyDefaults() {
        if (displayName == null || displayName.isBlank()) {
            displayName = name;
        }
        if (primaryColor == null || primaryColor.isBlank()) {
            primaryColor = "#0B6B4B";
        }
        if (secondaryColor == null || secondaryColor.isBlank()) {
            secondaryColor = "#F6C85F";
        }
        if (discountApprovalThreshold == null) {
            discountApprovalThreshold = BigDecimal.TEN;
        }
        if (invoicePrefix == null || invoicePrefix.isBlank()) {
            invoicePrefix = "INV";
        }
    }
}
