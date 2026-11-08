package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="vendor_verification_document")
@Getter @Setter @NoArgsConstructor
public class VendorVerificationDocument {
    @Id
    private UUID id;
    @Column(name="tenant_id", nullable=false)
    private UUID tenantId;
    @Column(name="document_type", nullable=false, length=50)
    private String documentType;
    @Column(name="original_name", nullable=false, length=200)
    private String originalName;
    @Column(name="storage_key", nullable=false, unique=true, length=100)
    private String storageKey;
    @Column(name="content_type", nullable=false, length=100)
    private String contentType;
    @Column(name="size_bytes", nullable=false)
    private long sizeBytes;
    @Column(name="uploaded_by", nullable=false)
    private UUID uploadedBy;
    @Column(name="review_status", nullable=false, length=20)
    private String reviewStatus;
    @Column(name="review_notes", length=1000)
    private String reviewNotes;
    @Column(name="reviewed_by")
    private UUID reviewedBy;
    @Column(name="reviewed_at")
    private Instant reviewedAt;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
}
