package com.saumyasolar.repo;

import com.saumyasolar.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.util.UUID;
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByTenantIdOrderByNameAsc(UUID tenantId);
    
    Optional<Branch> findByIdAndTenantId(UUID id, UUID tenantId);
    
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    
    long countByTenantIdAndActiveTrue(UUID tenantId);
}
