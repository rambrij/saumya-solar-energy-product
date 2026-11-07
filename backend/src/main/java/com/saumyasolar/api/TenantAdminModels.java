package com.saumyasolar.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TenantAdminModels {
    private TenantAdminModels() {}
    public record TenantUpdate(@NotBlank @Size(max=150) String name) {}
    public record TenantRow(UUID id, String name, String slug, String administratorName,
                            String administratorEmail, String status, Instant createdAt, Instant updatedAt) {}
    public record TenantPage(long total, int page, int size, int totalPages,
                             long active, long inactive, long suspended,
                             java.util.List<TenantRow> recentlyCreated,
                             java.util.List<TenantRow> items) {}
}
