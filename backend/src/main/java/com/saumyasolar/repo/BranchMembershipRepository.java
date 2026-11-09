package com.saumyasolar.repo;

import com.saumyasolar.domain.BranchMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchMembershipRepository extends JpaRepository<BranchMembership, UUID> {
    boolean existsByTenantIdAndBranchIdAndUserId(UUID tenantId, UUID branchId, UUID userId);
    
    long countByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    
    List<BranchMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    
    List<BranchMembership> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    
    Optional<BranchMembership> findByTenantIdAndBranchIdAndUserId(UUID tenantId, UUID branchId, UUID userId);
    
    long deleteByTenantIdAndBranchIdAndUserId(UUID tenantId, UUID branchId, UUID userId);
}
