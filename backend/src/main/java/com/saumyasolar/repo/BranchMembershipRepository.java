package com.saumyasolar.repo;
import com.saumyasolar.domain.BranchMembership;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface BranchMembershipRepository extends JpaRepository<BranchMembership,UUID> {
 List<BranchMembership> findByTenantIdAndUserId(UUID tenantId,UUID userId);
 boolean existsByTenantIdAndBranchIdAndUserId(UUID tenantId,UUID branchId,UUID userId);
 long countByTenantIdAndBranchId(UUID tenantId,UUID branchId);
 void deleteByTenantIdAndBranchIdAndUserId(UUID tenantId,UUID branchId,UUID userId);
}
