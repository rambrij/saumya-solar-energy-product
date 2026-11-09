package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
@Entity
@Table(name = "engineer_profile")
@Getter
@Setter
@NoArgsConstructor
public class EngineerProfile {
    @Id
    UUID id;
    UUID tenantId;
    UUID userId;
    @Column(columnDefinition = "json", nullable = false)
    String skillsJson;
    @Column(columnDefinition = "json", nullable = false)
    String certificationsJson;
    @Column(length = 500)
    String territory;
    LocalTime availableFrom;
    LocalTime availableUntil;
    int maxConcurrentJobs;
    boolean active;
    Instant createdAt;
    Instant updatedAt;
}
