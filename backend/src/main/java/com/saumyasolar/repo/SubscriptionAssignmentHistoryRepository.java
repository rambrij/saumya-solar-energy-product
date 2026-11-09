package com.saumyasolar.repo;
import com.saumyasolar.domain.SubscriptionAssignmentHistory;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface SubscriptionAssignmentHistoryRepository extends JpaRepository<SubscriptionAssignmentHistory,UUID> {
 List<SubscriptionAssignmentHistory> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
 List<SubscriptionAssignmentHistory> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
}
