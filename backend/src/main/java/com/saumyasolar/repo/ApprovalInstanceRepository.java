package com.saumyasolar.repo;
import com.saumyasolar.domain.ApprovalInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance,UUID> {
 Optional<ApprovalInstance> findByIdAndTenantId(UUID id,UUID tenantId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select i from ApprovalInstance i where i.id=:id and i.tenantId=:tenantId")
 Optional<ApprovalInstance> findForUpdate(@Param("id") UUID id,@Param("tenantId") UUID tenantId);
 Optional<ApprovalInstance> findFirstByTenantIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(UUID tenantId,String entityType,String entityId,String status);
 List<ApprovalInstance> findByTenantIdOrderByRequestedAtDesc(UUID tenantId);
}
