package com.saumyasolar.repo;

import com.saumyasolar.domain.VendorVerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorVerificationDocumentRepository extends JpaRepository<VendorVerificationDocument, UUID> {
    List<VendorVerificationDocument> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<VendorVerificationDocument> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndReviewStatus(UUID tenantId, String reviewStatus);
}
