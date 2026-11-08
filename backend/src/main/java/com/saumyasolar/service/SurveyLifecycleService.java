package com.saumyasolar.service;

import com.saumyasolar.api.SurveyModels.UpdateRequest;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.SiteSurveyHistory;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.SalesLeadRepository;
import com.saumyasolar.repo.SiteSurveyHistoryRepository;
import com.saumyasolar.repo.SiteSurveyRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SurveyLifecycleService {
    private static final Set<String> EDITABLE = Set.of("SCHEDULED", "IN_PROGRESS", "REJECTED");
    private final SiteSurveyRepository surveys;
    private final SiteSurveyHistoryRepository history;
    private final SalesLeadRepository leads;
    private final AppUserRepository users;

    public SurveyLifecycleService(SiteSurveyRepository surveys,
                                  SiteSurveyHistoryRepository history,
                                  SalesLeadRepository leads,
                                  AppUserRepository users) {
        this.surveys = surveys;
        this.history = history;
        this.leads = leads;
        this.users = users;
    }

    private void authorize(Principal actor) {
        if (actor == null || !"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("SURVEY_MANAGE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Survey management permission is required");
        }
    }

    private SiteSurvey requireSurvey(UUID id, Principal actor) {
        return surveys.findByIdAndTenantId(id, actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found"));
    }

    private void record(SiteSurvey survey, Principal actor, String event, String oldStatus, String newStatus, String reason) {
        SiteSurveyHistory row = new SiteSurveyHistory();
        row.setId(UUID.randomUUID());
        row.setTenantId(actor.tenantId());
        row.setSurveyId(survey.getId());
        row.setActorId(actor.userId());
        row.setEventType(event);
        row.setOldStatus(oldStatus);
        row.setNewStatus(newStatus);
        row.setReason(reason);
        row.setCreatedAt(Instant.now());
        history.save(row);
    }

    @Transactional
    public void recordCreated(SiteSurvey survey, Principal actor) {
        authorize(actor);
        if (survey == null || !actor.tenantId().equals(survey.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found");
        }
        record(survey, actor, "CREATED", null, survey.getStatus(), "Survey scheduled");
    }

    @Transactional
    public SiteSurvey get(UUID id, Principal actor) {
        authorize(actor);
        return requireSurvey(id, actor);
    }

    @Transactional
    public List<SiteSurveyHistory> history(UUID id, Principal actor) {
        authorize(actor);
        requireSurvey(id, actor);
        return history.findBySurveyIdAndTenantIdOrderByCreatedAtDesc(id, actor.tenantId());
    }

    @Transactional
    public SiteSurvey update(UUID id, UpdateRequest input, Principal actor) {
        authorize(actor);
        SiteSurvey survey = requireSurvey(id, actor);
        if (!EDITABLE.contains(survey.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Survey cannot be edited in status " + survey.getStatus());
        }
        if (input.assignedUserId() != null && users.findById(input.assignedUserId())
                .filter(user -> actor.tenantId().equals(user.getTenantId())
                        && "VENDOR".equals(user.getAccountType()) && user.isEnabled()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned engineer must be an enabled employee of this vendor");
        }
        if (input.scheduledAt() != null) survey.setScheduledAt(input.scheduledAt());
        if (input.assignedUserId() != null) survey.setAssignedUserId(input.assignedUserId());
        if (input.propertyType() != null) survey.setPropertyType(input.propertyType().trim());
        if (input.roofType() != null) survey.setRoofType(input.roofType().trim());
        if (input.usableAreaSqft() != null) survey.setUsableAreaSqft(input.usableAreaSqft());
        if (input.shading() != null) survey.setShading(input.shading().trim());
        if (input.sanctionedLoadKw() != null) survey.setSanctionedLoadKw(input.sanctionedLoadKw());
        if (input.roofOrientation() != null) survey.setRoofOrientation(input.roofOrientation().trim());
        if (input.monthlyElectricityBill() != null) survey.setMonthlyElectricityBill(input.monthlyElectricityBill());
        if (input.electricalPhase() != null) survey.setElectricalPhase(input.electricalPhase().trim());
        if (input.meterType() != null) survey.setMeterType(input.meterType().trim());
        if (input.latitude() != null) survey.setLatitude(input.latitude());
        if (input.longitude() != null) survey.setLongitude(input.longitude());
        if ((survey.getLatitude() == null) != (survey.getLongitude() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and longitude must be provided together");
        }
        if (input.notes() != null) survey.setNotes(input.notes().trim());
        if (input.findings() != null) survey.setFindings(input.findings().trim());
        if (input.recommendation() != null) survey.setRecommendation(input.recommendation().trim());
        record(survey, actor, "UPDATED", survey.getStatus(), survey.getStatus(), "Survey assessment updated");
        return surveys.save(survey);
    }

    @Transactional
    public SiteSurvey start(UUID id, Principal actor) {
        authorize(actor);
        SiteSurvey survey = requireSurvey(id, actor);
        transition(survey, actor, "SCHEDULED", "IN_PROGRESS", "STARTED", "Survey started");
        return surveys.save(survey);
    }

    @Transactional
    public SiteSurvey submit(UUID id, Principal actor) {
        authorize(actor);
        SiteSurvey survey = requireSurvey(id, actor);
        if (!Set.of("IN_PROGRESS", "REJECTED").contains(survey.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an in-progress or rejected survey can be submitted");
        }
        if (survey.getPropertyType() == null || survey.getRoofType() == null
                || survey.getUsableAreaSqft() == null || survey.getSanctionedLoadKw() == null
                || survey.getFindings() == null || survey.getFindings().isBlank()
                || survey.getRecommendation() == null || survey.getRecommendation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Property, roof, usable area, sanctioned load, findings and recommendation are required before submission");
        }
        String old = survey.getStatus();
        survey.setStatus("SUBMITTED");
        survey.setSubmittedAt(Instant.now());
        survey.setCompletedAt(Instant.now());
        survey.setRejectionReason(null);
        record(survey, actor, "SUBMITTED", old, "SUBMITTED", "Survey submitted for review");
        return surveys.save(survey);
    }

    @Transactional
    public SiteSurvey approve(UUID id, Principal actor) {
        authorize(actor);
        SiteSurvey survey = requireSurvey(id, actor);
        transition(survey, actor, "SUBMITTED", "APPROVED", "APPROVED", "Survey approved");
        survey.setReviewedBy(actor.userId());
        survey.setReviewedAt(Instant.now());
        return surveys.save(survey);
    }

    @Transactional
    public SiteSurvey reject(UUID id, String reason, Principal actor) {
        authorize(actor);
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        SiteSurvey survey = requireSurvey(id, actor);
        transition(survey, actor, "SUBMITTED", "REJECTED", "REJECTED", reason.trim());
        survey.setReviewedBy(actor.userId());
        survey.setReviewedAt(Instant.now());
        survey.setRejectionReason(reason.trim());
        return surveys.save(survey);
    }

    @Transactional
    public SiteSurvey cancel(UUID id, String reason, Principal actor) {
        authorize(actor);
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        SiteSurvey survey = requireSurvey(id, actor);
        if (!Set.of("SCHEDULED", "IN_PROGRESS", "REJECTED").contains(survey.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Survey cannot be cancelled in status " + survey.getStatus());
        }
        String old = survey.getStatus();
        survey.setStatus("CANCELLED");
        record(survey, actor, "CANCELLED", old, "CANCELLED", reason.trim());
        return surveys.save(survey);
    }

    private void transition(SiteSurvey survey, Principal actor, String expected, String target, String event, String reason) {
        if (!expected.equals(survey.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expected survey status " + expected + " but found " + survey.getStatus());
        }
        survey.setStatus(target);
        record(survey, actor, event, expected, target, reason);
    }
}
