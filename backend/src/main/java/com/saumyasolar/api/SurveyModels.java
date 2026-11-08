package com.saumyasolar.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SurveyModels {
    private SurveyModels() {}

    public record UpdateRequest(
            Instant scheduledAt,
            UUID assignedUserId,
            @Size(max = 50) String propertyType,
            @Size(max = 50) String roofType,
            @DecimalMin("0.01") BigDecimal usableAreaSqft,
            @Size(max = 100) String shading,
            @DecimalMin("0.01") BigDecimal sanctionedLoadKw,
            @Size(max = 40) String roofOrientation,
            @DecimalMin("0.00") BigDecimal monthlyElectricityBill,
            @Size(max = 30) String electricalPhase,
            @Size(max = 40) String meterType,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Size(max = 10000) String notes,
            @Size(max = 10000) String findings,
            @Size(max = 10000) String recommendation
    ) {}

    public record DecisionRequest(@NotBlank @Size(max = 1000) String reason) {}
}
