package com.saumyasolar.repo;

import com.saumyasolar.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<OrderItem> findByIdAndTenantId(UUID id, UUID tenantId);
}
