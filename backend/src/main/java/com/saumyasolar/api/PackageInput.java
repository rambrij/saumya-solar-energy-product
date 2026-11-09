package com.saumyasolar.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PackageInput(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull @DecimalMin("0.01") BigDecimal capacityKw,
        @NotBlank @Size(max = 80) String packageType,
        @NotNull @DecimalMin("0.00") BigDecimal basePrice,
        Integer warrantyYears,
        String imageUrl,
        Boolean active,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(@NotNull UUID productId, @NotNull Integer quantity, Boolean optionalItem) {
    }
}
