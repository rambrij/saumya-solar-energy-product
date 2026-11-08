package com.saumyasolar.repo;
import com.saumyasolar.domain.ApprovalInstance;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;import java.util.UUID;
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance,UUID> {
 Optional<ApprovalInstance> findByIdAndTenantId(UUID id,UUID tenantId);
 Optional<ApprovalInstance> findFirstByTenantIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(UUID tenantId,String entityType,String entityId,String status);
}
