package com.saumyasolar.repo;
import com.saumyasolar.domain.ApprovalWorkflow;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;import java.util.UUID;
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow,UUID> {
 List<ApprovalWorkflow> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
 Optional<ApprovalWorkflow> findFirstByTenantIdAndEntityTypeAndActiveTrueOrderByUpdatedAtDesc(UUID tenantId,String entityType);
 boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId,String code);
 Optional<ApprovalWorkflow> findByIdAndTenantId(UUID id,UUID tenantId);
}
