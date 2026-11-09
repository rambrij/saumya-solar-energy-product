package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "integration_connection")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationConnection {
    @Id
    UUID id;
    UUID tenantId;
    String providerCode;
    String integrationType;
    String displayName;
    String status;
    String secretReference;
    @Column(columnDefinition = "longtext")
    String configJson;
    Instant createdAt;
    Instant updatedAt;
}
