package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
@Entity
@Table(name = "service_sla_policy", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "priority" }))
@Getter
@Setter
@NoArgsConstructor
public class ServiceSlaPolicy {
    @Id
    UUID id;
    UUID tenantId;
    String priority;
    int responseMinutes;
    int resolutionMinutes;
    String timeZone = "Asia/Kolkata";
    LocalTime businessStart;
    LocalTime businessEnd;
    @Column(columnDefinition = "JSON")
    String holidaysJson = "[]";
    @Column(columnDefinition = "JSON")
    String workingDaysJson = "[]";
    boolean active = true;
    Instant createdAt;
    Instant updatedAt;
}
