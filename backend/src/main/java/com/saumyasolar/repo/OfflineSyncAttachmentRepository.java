package com.saumyasolar.repo;

import com.saumyasolar.domain.OfflineSyncAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
public interface OfflineSyncAttachmentRepository extends JpaRepository<OfflineSyncAttachment, UUID> {
    Optional<OfflineSyncAttachment> findByTenantIdAndActorIdAndClientAttachmentId(
            UUID tenantId,
            UUID actorId,
            UUID clientAttachmentId
    );
    
    Optional<OfflineSyncAttachment> findByIdAndTenantIdAndActorId(UUID id, UUID tenantId, UUID actorId);
}
