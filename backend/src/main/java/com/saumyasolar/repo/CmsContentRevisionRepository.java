package com.saumyasolar.repo;

import com.saumyasolar.domain.CmsContentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CmsContentRevisionRepository extends JpaRepository<CmsContentRevision, UUID> {
    List<CmsContentRevision> findByTenantIdAndContentIdOrderByRevisionNumberDesc(UUID tenantId, UUID contentId);
    Optional<CmsContentRevision> findByIdAndTenantIdAndContentId(UUID id, UUID tenantId, UUID contentId);
    Optional<CmsContentRevision> findTopByTenantIdAndContentIdOrderByRevisionNumberDesc(UUID tenantId, UUID contentId);
}
