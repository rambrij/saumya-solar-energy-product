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
@Table(name = "installation_checklist_template")
@Getter
@Setter
@NoArgsConstructor
public class InstallationChecklistTemplate {
    @Id
    UUID id;
    UUID tenantId;
    String code;
    String title;
    boolean mandatory;
    boolean active;
    Instant createdAt;
}
