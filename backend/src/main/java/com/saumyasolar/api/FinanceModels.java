package com.saumyasolar.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class FinanceModels {
    private FinanceModels() {
    }
    
    public record InvoiceCreate(@NotNull UUID orderId, java.time.LocalDate dueDate, String taxTreatment) {
    }
    
    public record CreditNoteCreate(
            @NotNull @DecimalMin("0.01") BigDecimal amount, @NotBlank @Size(max = 500) String reason
    ) {
    }
    
    public record ManualPayment(
            @NotNull UUID invoiceId, @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER|UPI|CHEQUE|CARD|OTHER") String method,
            @Size(max = 150) String reference, @NotNull Instant paidAt
    ) {
    }
    
    public record RefundCreate(
            @NotNull UUID paymentId, @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 500) String reason, @Size(max = 160) String idempotencyKey
    ) {
        public RefundCreate(UUID paymentId, BigDecimal amount, String reason) { this(paymentId, amount, reason, null); }
    }
    }
    
    public record WebhookEvent(
            @NotBlank String eventId, @NotBlank String paymentReference,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "SUCCEEDED|FAILED") String status
    ) {
    }
    
    public record ServiceCreate(
            @NotBlank String category, @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String priority,
            @NotBlank @Size(max = 4000) String description, UUID customerId, UUID installationId, UUID productId,
            @Size(max = 150) String serialNumber
    ) {
    }
    
    public record ServiceAssign(@NotNull UUID engineerId, @NotBlank String reason) {
    }
    
    public record ServiceVisitCreate(@NotNull UUID engineerId, @NotNull Instant scheduledAt, @NotBlank String reason) {
    }
    
    public record ServicePart(
            @NotNull UUID warehouseId, @NotNull UUID productId, @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotBlank String reason
    ) {
    }
    
    public record ServiceVisitComplete(
            @NotBlank String engineerNotes, @NotBlank String resolution, @Size(max = 1000) String evidenceUrl,
            @Valid List<ServicePart> parts
    ) {
    }
    
    public record ServiceResolve(@NotBlank String resolution) {
    }
    
    public record SlaPolicyInput(
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String priority,
            @Min(1) int responseMinutes, @Min(1) int resolutionMinutes, @NotBlank String timeZone,
            LocalTime businessStart, LocalTime businessEnd, @Size(max = 4000) String holidaysJson,
            @Size(max = 1000) String workingDaysJson
    ) {
    }
    
    public record ServiceReopenPolicyInput(@Min(0) @Max(90) int reopenDays) {
    }
}
