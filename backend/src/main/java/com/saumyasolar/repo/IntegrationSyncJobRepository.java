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
public interface IntegrationSyncJobRepository extends JpaRepository<IntegrationSyncJob,UUID>{
 List<IntegrationSyncJob> findTop100ByTenantIdAndConnectionIdOrderByCreatedAtDesc(UUID tenantId,UUID connectionId);
 List<IntegrationSyncJob> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(List<String> statuses,Instant now);
 Optional<IntegrationSyncJob> findByIdAndTenantId(UUID id,UUID tenantId);
 Optional<IntegrationSyncJob> findByTenantIdAndConnectionIdAndIdempotencyKey(UUID tenantId,UUID connectionId,String idempotencyKey);
 @Modifying
 @Transactional
 @Query("update IntegrationSyncJob j set j.status='PROCESSING', j.attemptCount=j.attemptCount+1, j.updatedAt=:now where j.id=:id and j.status in ('QUEUED','RETRY') and j.nextAttemptAt<=:now")
 int claimIfDue(@Param("id") UUID id,@Param("now") Instant now);
}
