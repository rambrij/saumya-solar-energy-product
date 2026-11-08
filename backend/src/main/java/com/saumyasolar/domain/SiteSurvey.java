package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "site_survey")
@Getter
@Setter
@NoArgsConstructor
public class SiteSurvey {
    @Id
    UUID id;
    UUID tenantId;
    UUID branchId;
    UUID leadId;
    Instant scheduledAt;
    UUID assignedUserId;
    String propertyType;
    String roofType;
    BigDecimal usableAreaSqft;
    String shading;
    BigDecimal sanctionedLoadKw;
    @Column(columnDefinition = "TEXT")
    String notes;
    String status;
    Instant completedAt;
    BigDecimal latitude;
    BigDecimal longitude;
    String roofOrientation;
    BigDecimal monthlyElectricityBill;
    String electricalPhase;
    String meterType;
    @Column(columnDefinition = "TEXT")
    String findings;
    @Column(columnDefinition = "TEXT")
    String recommendation;
    Instant submittedAt;
    UUID reviewedBy;
    Instant reviewedAt;
    @Column(length = 1000)
    String rejectionReason;
}
