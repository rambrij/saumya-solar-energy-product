package com.saumyasolar.domain;

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
    UUID tenantId;
    String operationName;
    String idempotencyKey;
    UUID resourceId;
}
