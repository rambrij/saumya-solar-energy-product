package com.saumyasolar.repo;

import com.saumyasolar.domain.CmsContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CmsContentRepository extends JpaRepository<CmsContent, UUID> {
    Page<CmsContent> findByTenantIdAndTypeOrderByUpdatedAtDesc(UUID tenantId, String type, Pageable pageable);
    Page<CmsContent> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);
    Optional<CmsContent> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<CmsContent> findByTenantIdAndTypeAndSlugAndPublishedRevisionIdIsNotNull(
            UUID tenantId, String type, String slug);
    Page<CmsContent> findByTenantIdAndTypeAndPublishedRevisionIdIsNotNullAndStatusNotOrderByPublishedAtDesc(
            UUID tenantId, String type, String excludedStatus, Pageable pageable);
    boolean existsByTenantIdAndTypeAndSlug(UUID tenantId, String type, String slug);
    boolean existsByTenantIdAndTypeAndSlugAndIdNot(UUID tenantId, String type, String slug, UUID id);
}
