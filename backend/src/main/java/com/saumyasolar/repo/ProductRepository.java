package com.saumyasolar.repo;

import com.saumyasolar.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
}
