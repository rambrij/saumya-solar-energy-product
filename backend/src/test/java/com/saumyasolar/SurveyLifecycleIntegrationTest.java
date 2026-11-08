package com.saumyasolar;

import com.saumyasolar.api.SurveyModels.UpdateRequest;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.SalesLeadRepository;
import com.saumyasolar.repo.SiteSurveyRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.SurveyLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SurveyLifecycleIntegrationTest {
    @Autowired SurveyLifecycleService lifecycle;
    @Autowired SiteSurveyRepository surveys;
    @Autowired SalesLeadRepository leads;
    @Autowired TenantRepository tenants;
    @Autowired AppUserRepository users;

    private UUID tenantId;
    private UUID actorId;
    private UUID surveyId;
    private Principal actor;

    @BeforeEach
    void setup() {
        var admin = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        tenantId = admin.getTenantId();
        actorId = admin.getId();
        actor = new Principal(actorId, tenantId, admin.getEmail(), "VENDOR",
                Set.of("VENDOR_ADMIN"), Set.of("SURVEY_MANAGE"));
        var lead = leads.findByTenantIdOrderByIdDesc(tenantId).stream().findFirst().orElseThrow();
        SiteSurvey survey = new SiteSurvey();
        surveyId = UUID.randomUUID();
        survey.setId(surveyId);
        survey.setTenantId(tenantId);
        survey.setLeadId(lead.getId());
        survey.setScheduledAt(Instant.now().plusSeconds(3600));
        survey.setStatus("SCHEDULED");
        surveys.save(survey);
    }

    @Test
    void requiresCompleteAssessmentBeforeSubmissionAndApprovalBeforeQuotationUse() {
        lifecycle.start(surveyId, actor);
        ResponseStatusException incomplete = assertThrows(ResponseStatusException.class,
                () -> lifecycle.submit(surveyId, actor));
        assertEquals(HttpStatus.BAD_REQUEST, incomplete.getStatusCode());

        lifecycle.update(surveyId, new UpdateRequest(null, null, "RESIDENTIAL", "RCC",
                new BigDecimal("1200"), "LOW", new BigDecimal("5"), "SOUTH",
                new BigDecimal("4500"), "SINGLE_PHASE", "DIGITAL",
                new BigDecimal("19.0760"), new BigDecimal("72.8777"),
                "Survey notes", "Roof is suitable; minor shade in evening",
                "Install 5 kW rooftop system with elevated mounting"), actor);

        SiteSurvey submitted = lifecycle.submit(surveyId, actor);
        assertEquals("SUBMITTED", submitted.getStatus());
        SiteSurvey approved = lifecycle.approve(surveyId, actor);
        assertEquals("APPROVED", approved.getStatus());
        assertNotNull(approved.getReviewedAt());
        assertEquals(actorId, approved.getReviewedBy());
        assertTrue(lifecycle.history(surveyId, actor).size() >= 4);
    }

    @Test
    void rejectedSurveyRequiresReasonAndCanBeResubmittedAfterCorrection() {
        lifecycle.start(surveyId, actor);
        lifecycle.update(surveyId, new UpdateRequest(null, null, "RESIDENTIAL", "RCC",
                new BigDecimal("900"), "HIGH", new BigDecimal("3"), null, null, null, null,
                null, null, null, "Insufficient usable roof area",
                "Rework mounting layout and remeasure"), actor);
        lifecycle.submit(surveyId, actor);
        ResponseStatusException missingReason = assertThrows(ResponseStatusException.class,
                () -> lifecycle.reject(surveyId, "  ", actor));
        assertEquals(HttpStatus.BAD_REQUEST, missingReason.getStatusCode());
        assertEquals("REJECTED", lifecycle.reject(surveyId, "Need revised shade analysis", actor).getStatus());
        assertEquals("SUBMITTED", lifecycle.submit(surveyId, actor).getStatus());
    }

    @Test
    void surveyReadsAndTransitionsAreTenantScopedAndPermissionProtected() {
        Tenant other = new Tenant();
        other.setId(UUID.randomUUID());
        other.setName("Survey isolation tenant");
        other.setSlug("survey-" + UUID.randomUUID());
        other.setStatus("ACTIVE");
        tenants.save(other);
        Principal foreign = new Principal(actorId, other.getId(), "foreign@example.test", "VENDOR",
                Set.of("VENDOR_ADMIN"), Set.of("SURVEY_MANAGE"));
        ResponseStatusException notFound = assertThrows(ResponseStatusException.class,
                () -> lifecycle.get(surveyId, foreign));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());

        Principal withoutPermission = new Principal(actorId, tenantId, "admin@saumya.local", "VENDOR",
                Set.of("VENDOR_ADMIN"), Set.of());
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> lifecycle.get(surveyId, withoutPermission));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void cannotCancelAnApprovedSurvey() {
        lifecycle.start(surveyId, actor);
        lifecycle.update(surveyId, new UpdateRequest(null, null, "RESIDENTIAL", "RCC",
                new BigDecimal("1000"), "LOW", new BigDecimal("4"), null, null, null, null,
                null, null, null, "Assessment complete", "Suitable for rooftop solar"), actor);
        lifecycle.submit(surveyId, actor);
        lifecycle.approve(surveyId, actor);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> lifecycle.cancel(surveyId, "Cancelled after approval", actor));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }
}
