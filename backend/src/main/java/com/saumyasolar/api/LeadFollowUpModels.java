package com.saumyasolar.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class LeadFollowUpModels {
    private LeadFollowUpModels() {}
    public record CreateFollowUp(@NotNull UUID assignedTo, @NotNull Instant dueAt, @Size(max=1000) String note) {}
}
