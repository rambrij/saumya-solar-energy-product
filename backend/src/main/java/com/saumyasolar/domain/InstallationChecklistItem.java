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
@Table(name = "installation_checklist_item")
@Getter
@Setter
@NoArgsConstructor
public class InstallationChecklistItem {
    @Id
    UUID id;
    UUID tenantId;
    UUID installationId;
    String code;
    String title;
    boolean mandatory;
    boolean completed;
    @Column(length = 1000)
    String evidenceUrl;
    @Column(length = 2000)
    String notes;
    UUID completedBy;
    Instant completedAt;
}
