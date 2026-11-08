package com.saumyasolar.repo;
import com.saumyasolar.domain.SiteSurveyAttachment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;import java.util.Optional;import java.util.UUID;
public interface SiteSurveyAttachmentRepository extends JpaRepository<SiteSurveyAttachment,UUID>{List<SiteSurveyAttachment> findByTenantIdAndSurveyIdOrderByCreatedAtAsc(UUID tenantId,UUID surveyId);Optional<SiteSurveyAttachment> findByIdAndTenantIdAndSurveyId(UUID id,UUID tenantId,UUID surveyId);}
