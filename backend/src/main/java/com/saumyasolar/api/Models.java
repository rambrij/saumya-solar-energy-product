package com.saumyasolar.api;

import com.saumyasolar.domain.Quotation;
import com.saumyasolar.domain.QuotationItem;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
public final class Models {
    public record Login(@Email String email, @NotBlank String password) {
    }
    
    public record Register(
            @NotBlank String storeSlug, @NotBlank String name, @Email String email, @Size(min = 8) String password
    ) {
    }
    
    public record Refresh(@NotBlank String refreshToken) {
    }

    public record EmployeeCreate(
            @NotBlank String name, @Email String email, @Size(min = 12) String password,
            @NotBlank String roleName
    ) {
    }
    
    public record PasswordReset(@Email String email, String token, String newPassword) {
    }
    
    public record Session(String accessToken, String refreshToken, UserView user) {
    }
    
    public record UserView(
            UUID id, UUID tenantId, String name, String email, String accountType, Set<String> roles,
            Set<String> permissions
    ) {
    }
    
    public record Input(
            String name, String description, Boolean active, String sku, UUID categoryId, BigDecimal price,
            BigDecimal taxPercent, String code, BigDecimal capacityKw, String packageType, BigDecimal basePrice,
            Integer warrantyYears, String email, String phone, String address, UUID customerId, String propertyType,
            BigDecimal monthlyBill, BigDecimal requiredCapacityKw, String message, UUID enquiryId, String priority,
            UUID assignedUserId, Instant nextFollowUp, String stage, String reason, String lostReason, UUID leadId,
            Instant scheduledAt, String roofType, BigDecimal usableAreaSqft, String shading,
            BigDecimal sanctionedLoadKw, String notes, String status, UUID surveyId, BigDecimal discountPercent,
            LocalDate validUntil, List<Line> items, String slug, String adminName, String adminEmail,
            String adminPassword
    ) {
    }
    
    public record Line(
            UUID productId, UUID packageId, String description, int quantity, BigDecimal unitPrice,
            BigDecimal taxPercent, Boolean optionalItem
    ) {
    }
    
    public record QuoteView(Quotation quotation, List<QuotationItem> items) {
    }
}
