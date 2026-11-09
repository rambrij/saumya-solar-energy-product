package com.saumyasolar.repo;
import com.saumyasolar.domain.TenantSubscription;import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;import java.util.*;import java.util.UUID;
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription,UUID> {
 Optional<TenantSubscription> findFirstByTenantIdAndStatusInOrderByStartsAtDesc(UUID tenantId,List<String> statuses);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
 List<TenantSubscription> findByStatusInAndEndsAtBefore(List<String> statuses,Instant now);
 boolean existsByPlanId(UUID planId);
}
