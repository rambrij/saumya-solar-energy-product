package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "export_job")
@Getter @Setter @NoArgsConstructor
public class ExportJob {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID requestedBy;
    @Column(nullable = false, length = 40) private String reportType;
    @Column(nullable = false, length = 10) private String format;
    private LocalDate fromDate;
    private LocalDate toDate;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 500) private String storageKey;
    @Column(length = 120) private String contentType;
    private Long fileSize;
    @Column(length = 500) private String lastError;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant completedAt;
    private Instant expiresAt;
}
