package com.saumyasolar.repo;

import com.saumyasolar.domain.SiteSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface SiteSurveyRepository extends JpaRepository<SiteSurvey, UUID> {
    List<SiteSurvey> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<SiteSurvey> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SiteSurvey> findByLeadIdAndTenantIdOrderByIdDesc(UUID leadId, UUID tenantId);
}
