package com.saumyasolar.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class VendorProfileModels {
    private VendorProfileModels() {}
    public record VendorProfileInput(
            @NotBlank @Size(max=180) String legalName,
            @NotBlank @Pattern(regexp="^[0-9]{10}$") String contactPhone,
            @NotBlank @Size(max=500) String registeredAddress,
            @Email @Size(max=190) String contactEmail,
            @Size(max=15) String gstin,
            @Size(max=255) String website
    ) {}
    public record VendorProfileView(UUID tenantId, String displayName, String legalName, String contactPhone,
                                    String registeredAddress, String contactEmail, String gstin, String website) {}
    public record VerificationDocumentView(UUID id, UUID tenantId, String documentType, String originalName,
                                           String contentType, long sizeBytes, String reviewStatus,
                                           String reviewNotes, Instant createdAt, Instant reviewedAt) {}
    public record DocumentReviewInput(@NotBlank String decision, @Size(max=1000) String notes) {}
}
