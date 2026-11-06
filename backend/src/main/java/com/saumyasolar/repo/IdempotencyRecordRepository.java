package com.saumyasolar.repo;

import com.saumyasolar.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByTenantIdAndOperationNameAndIdempotencyKey(
            UUID tenantId,
            String operationName,
            String idempotencyKey
    );
}
