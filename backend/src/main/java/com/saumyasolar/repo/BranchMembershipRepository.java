package com.saumyasolar.repo;
import com.saumyasolar.domain.BranchMembership;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface BranchMembershipRepository extends JpaRepository<BranchMembership,UUID> {
 boolean existsByTenantIdAndBranchIdAndUserId(UUID tenantId,UUID branchId,UUID userId);
 long countByTenantIdAndBranchId(UUID tenantId,UUID branchId);
}
