package com.saumyasolar.repo;

import com.saumyasolar.domain.OfflineSyncOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfflineSyncOperationRepository extends JpaRepository<OfflineSyncOperation, UUID> {
    Optional<OfflineSyncOperation> findByTenantIdAndActorIdAndClientOperationId(
            UUID tenantId, UUID actorId, UUID clientOperationId);
    
    List<OfflineSyncOperation> findTop100ByTenantIdAndActorIdOrderByReceivedAtDesc(
            UUID tenantId, UUID actorId);
}
