package com.saumyasolar.repo;

import com.saumyasolar.domain.IntegrationSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface IntegrationSyncJobRepository extends JpaRepository<IntegrationSyncJob, UUID> {
    List<IntegrationSyncJob> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<IntegrationSyncJob> findTop100ByTenantIdAndConnectionIdOrderByCreatedAtDesc(UUID tenantId, UUID connectionId);
    
    List<IntegrationSyncJob> findTop50ByTenantIdAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            UUID tenantId, List<String> statuses, Instant now
    );
    
    Optional<IntegrationSyncJob> findByIdAndTenantId(UUID id, UUID tenantId);
    
    Optional<IntegrationSyncJob> findByTenantIdAndConnectionIdAndIdempotencyKey(
            UUID tenantId,
            UUID connectionId,
            String idempotencyKey
    );
    
    List<IntegrationSyncJob> findTop50ByTenantIdAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            UUID tenantId, String status, Instant staleBefore
    );
    
    @Modifying
    @Transactional
    @Query("update IntegrationSyncJob j set j.status='PROCESSING', j.attemptCount=j.attemptCount+1, j.updatedAt=:now where j.id=:id and j.tenantId=:tenantId and ((j.status in ('QUEUED','RETRY') and j.nextAttemptAt<=:now) or (j.status='PROCESSING' and j.updatedAt<=:staleBefore))")
    int claimIfDue(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
