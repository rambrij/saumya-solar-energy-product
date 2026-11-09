package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "warranty_claim")
@Getter
@Setter
@NoArgsConstructor
public class WarrantyClaim {
    @Id
    UUID id;
    UUID tenantId;
    UUID warrantyId;
    UUID customerId;
    @Column(nullable = false, length = 50)
    String claimNumber;
    @Column(nullable = false, length = 200)
    String subject;
    @Column(nullable = false, length = 4000)
    String description;
    @Column(length = 1000)
    String evidenceUrl;
    @Column(nullable = false, length = 30)
    String status;
    @Column(length = 1000)
    String decisionReason;
    @Column(length = 2000)
    String resolutionNotes;
    Instant createdAt;
    Instant updatedAt;
    UUID reviewedBy;
    Instant reviewedAt;
}
