package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class LeadHistory {
    @Id
    UUID id;
    UUID tenantId;
    UUID leadId;
    String oldStage;
    String newStage;
    UUID actorId;
    String reason;
    Instant createdAt;
}
