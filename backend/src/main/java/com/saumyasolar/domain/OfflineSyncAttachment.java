package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="offline_sync_attachment") @Getter @Setter @NoArgsConstructor
public class OfflineSyncAttachment {@Id UUID id;UUID tenantId;UUID actorId;UUID clientAttachmentId;UUID operationId;String fileName;String contentType;long expectedSize;long receivedSize;@Column(length=64,columnDefinition="char(64)") String checksumSha256;String objectKey;String status;Instant createdAt;Instant completedAt;}