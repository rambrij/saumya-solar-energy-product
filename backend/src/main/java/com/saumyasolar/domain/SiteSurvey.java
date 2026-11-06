package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SiteSurvey {
    @Id
    UUID id;
    UUID tenantId;
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
}
