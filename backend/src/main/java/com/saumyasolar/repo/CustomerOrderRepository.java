package com.saumyasolar.repo;

import com.saumyasolar.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    List<CustomerOrder> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<CustomerOrder> findByIdAndTenantId(UUID id, UUID tenantId);
}
