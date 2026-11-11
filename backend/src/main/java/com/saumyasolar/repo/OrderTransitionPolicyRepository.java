package com.saumyasolar.repo;

import com.saumyasolar.domain.OrderTransitionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderTransitionPolicyRepository extends JpaRepository<OrderTransitionPolicy, UUID> {
    List<OrderTransitionPolicy> findByTenantIdIsNullOrderByFromStatusAscToStatusAsc();
    List<OrderTransitionPolicy> findByTenantIdOrderByFromStatusAscToStatusAsc(UUID tenantId);
    List<OrderTransitionPolicy> findByTenantIdIsNullAndFromStatusOrderByToStatusAsc(String fromStatus);
    Optional<OrderTransitionPolicy> findByTenantIdAndFromStatusAndToStatus(UUID tenantId,String fromStatus,String toStatus);
    Optional<OrderTransitionPolicy> findByTenantIdIsNullAndFromStatusAndToStatus(String fromStatus,String toStatus);
}
