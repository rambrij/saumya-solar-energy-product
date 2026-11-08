package com.saumyasolar.repo;
import com.saumyasolar.domain.IntegrationSyncJob;import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;import java.util.List;import java.util.Optional;import java.util.UUID;
public interface IntegrationSyncJobRepository extends JpaRepository<IntegrationSyncJob,UUID>{
 List<IntegrationSyncJob> findTop100ByTenantIdAndConnectionIdOrderByCreatedAtDesc(UUID tenantId,UUID connectionId);
 List<IntegrationSyncJob> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(List<String> statuses,Instant now);
 Optional<IntegrationSyncJob> findByIdAndTenantId(UUID id,UUID tenantId);
 Optional<IntegrationSyncJob> findByTenantIdAndConnectionIdAndIdempotencyKey(UUID tenantId,UUID connectionId,String idempotencyKey);
}
