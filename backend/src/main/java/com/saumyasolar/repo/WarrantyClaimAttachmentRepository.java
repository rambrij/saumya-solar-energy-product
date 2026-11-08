package com.saumyasolar.repo;
import com.saumyasolar.domain.WarrantyClaimAttachment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;import java.util.Optional;import java.util.UUID;
public interface WarrantyClaimAttachmentRepository extends JpaRepository<WarrantyClaimAttachment,UUID>{List<WarrantyClaimAttachment> findByTenantIdAndClaimIdOrderByCreatedAtAsc(UUID tenantId,UUID claimId);Optional<WarrantyClaimAttachment> findByIdAndTenantIdAndClaimId(UUID id,UUID tenantId,UUID claimId);}
