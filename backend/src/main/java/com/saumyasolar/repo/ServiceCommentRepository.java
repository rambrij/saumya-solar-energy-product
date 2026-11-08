package com.saumyasolar.repo;
import com.saumyasolar.domain.ServiceComment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface ServiceCommentRepository extends JpaRepository<ServiceComment,UUID>{
 List<ServiceComment> findByTenantIdAndServiceRequestIdOrderByCreatedAtAsc(UUID tenantId,UUID serviceRequestId);
}