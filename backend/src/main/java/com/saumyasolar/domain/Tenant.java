package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
    String status;
    @Column(name = "verification_status", nullable = false, length = 30)
    String verificationStatus = "PENDING_VERIFICATION";
    @Column(name = "verified_at")
    Instant verifiedAt;
    @Column(name = "verified_by")
    UUID verifiedBy;
    @Column(name = "rejection_reason", length = 1000)
    String rejectionReason;
    BigDecimal discountApprovalThreshold = BigDecimal.TEN;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "updated_at")
    Instant updatedAt;
    String gstin;
    String invoicePrefix = "INV";
    Integer serviceReopenDays;
}
