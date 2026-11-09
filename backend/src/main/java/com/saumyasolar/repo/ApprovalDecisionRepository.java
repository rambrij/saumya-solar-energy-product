package com.saumyasolar.repo;

import com.saumyasolar.domain.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, UUID> {
    List<ApprovalDecision> findByTenantIdAndInstanceIdOrderByStepOrderAscCreatedAtAsc(UUID tenantId, UUID instanceId);
}
