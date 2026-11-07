package com.saumyasolar.repo;
import com.saumyasolar.domain.ServiceAttachment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface ServiceAttachmentRepository extends JpaRepository<ServiceAttachment,UUID>{List<ServiceAttachment> findByTenantIdAndServiceRequestIdOrderByCreatedAtAsc(UUID tenantId,UUID requestId);Optional<ServiceAttachment> findByIdAndTenantIdAndServiceRequestId(UUID id,UUID tenantId,UUID requestId);}
