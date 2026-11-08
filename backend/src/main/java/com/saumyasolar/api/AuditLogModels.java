package com.saumyasolar.api;

import java.time.Instant;
import java.util.UUID;

public final class AuditLogModels {
    private AuditLogModels() {}
    public record AuditLogView(UUID id, UUID tenantId, UUID actorId, String accountType, String httpMethod,
                               String requestPath, int responseStatus, String sourceIp, Instant createdAt) {}
}
