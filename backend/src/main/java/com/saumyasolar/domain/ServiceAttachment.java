package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="service_attachment") @Getter @Setter @NoArgsConstructor
public class ServiceAttachment {@Id UUID id;UUID tenantId;UUID serviceRequestId;String originalName;String storageKey;String contentType;long sizeBytes;UUID uploadedBy;Instant createdAt;}
