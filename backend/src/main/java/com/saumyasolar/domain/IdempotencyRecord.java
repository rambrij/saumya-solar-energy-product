package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {
    @Id
    UUID id;
    @Column(nullable = false)
    UUID tenantId;
    String operationName;
    String idempotencyKey;
    UUID resourceId;
}
