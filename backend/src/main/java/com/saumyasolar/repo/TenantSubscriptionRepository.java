package com.saumyasolar.repo;
import com.saumyasolar.domain.TenantSubscription;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;import java.util.UUID;
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription,UUID> {
 Optional<TenantSubscription> findFirstByTenantIdAndStatusInOrderByStartsAtDesc(UUID tenantId,List<String> statuses);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
