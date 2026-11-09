package com.saumyasolar.repo;

import com.saumyasolar.domain.WarrantyClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, UUID> {
    Optional<WarrantyClaim> findByIdAndTenantId(UUID id, UUID tenantId);
    
    List<WarrantyClaim> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    List<WarrantyClaim> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
}
