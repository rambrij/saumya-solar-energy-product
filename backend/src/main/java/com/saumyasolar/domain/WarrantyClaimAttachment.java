package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "warranty_claim_attachment")
@Getter
@Setter
@NoArgsConstructor
public class WarrantyClaimAttachment {
    @Id
    UUID id;
    UUID tenantId;
    UUID claimId;
    String originalName;
    String storageKey;
    String contentType;
    long sizeBytes;
    UUID uploadedBy;
    Instant createdAt;
}
