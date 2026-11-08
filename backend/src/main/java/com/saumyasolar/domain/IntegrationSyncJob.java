package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="integration_sync_job") @Getter @Setter @NoArgsConstructor
public class IntegrationSyncJob {
 @Id UUID id;UUID tenantId;UUID connectionId;String entityType;String entityId;String operation;String idempotencyKey;
 @Column(columnDefinition="longtext") String payloadJson;String status;int attemptCount;Instant nextAttemptAt;String lastError;String externalReference;Instant createdAt;Instant updatedAt;
}
