package com.saumyasolar.repo;

import com.saumyasolar.domain.SiteSurveyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SiteSurveyHistoryRepository extends JpaRepository<SiteSurveyHistory, UUID> {
    List<SiteSurveyHistory> findBySurveyIdAndTenantIdOrderByCreatedAtDesc(UUID surveyId, UUID tenantId);
}
