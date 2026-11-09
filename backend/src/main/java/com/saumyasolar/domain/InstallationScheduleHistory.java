package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "installation_schedule_history")
@Getter
@Setter
@NoArgsConstructor
public class InstallationScheduleHistory {
    @Id
    UUID id;
    UUID tenantId;
    UUID installationId;
    UUID actorId;
    @Column(nullable = false, length = 30)
    String eventType;
    Instant previousScheduledAt;
    Instant newScheduledAt;
    @Column(nullable = false, length = 1000)
    String reason;
    Instant createdAt;
}
