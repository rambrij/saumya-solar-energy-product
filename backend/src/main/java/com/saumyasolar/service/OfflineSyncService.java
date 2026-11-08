package com.saumyasolar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.api.SurveyModels;
import com.saumyasolar.domain.OfflineSyncOperation;
import com.saumyasolar.domain.AuditEvent;
import com.saumyasolar.domain.ServiceComment;
import com.saumyasolar.domain.ServiceRequest;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class OfflineSyncService {
    private static final int MAX_BATCH = 50;
    private static final int MAX_PAYLOAD_CHARS = 16_000;
    private final OfflineSyncOperationRepository operations;
    private final AuditEventRepository audits;
    private final ServiceCommentControllerAccess comments;
    private final SurveyLifecycleService surveys;
    private final ObjectMapper mapper;

    public OfflineSyncService(OfflineSyncOperationRepository operations,
                              AuditEventRepository audits,
                              ServiceRequestRepository tickets,
                              ServiceCommentRepository serviceComments,
                              CustomerProfileRepository customers,
                              NotificationService notifications,
                              SurveyLifecycleService surveys,
                              ObjectMapper mapper) {
        this.operations = operations;
        this.audits = audits;
        this.comments = new ServiceCommentControllerAccess(tickets, serviceComments, customers, notifications, mapper);
        this.surveys = surveys;
        this.mapper = mapper;
    }

    @Transactional
    public List<SyncResult> synchronize(List<SyncCommand> commands, Principal actor) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null
                || !Set.of("VENDOR", "CUSTOMER").contains(actor.accountType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated tenant user required");
        }
        if (commands == null || commands.isEmpty() || commands.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submit between 1 and " + MAX_BATCH + " offline operations");
        }
        List<SyncResult> results = new ArrayList<>();
        for (SyncCommand command : commands) results.add(apply(command, actor));
        return results;
    }

    @Transactional
    public List<SyncResult> status(Principal actor) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null
                || !Set.of("VENDOR", "CUSTOMER").contains(actor.accountType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return operations.findTop100ByTenantIdAndActorIdOrderByReceivedAtDesc(actor.tenantId(), actor.userId())
                .stream().map(this::result).toList();
    }

    private SyncResult apply(SyncCommand command, Principal actor) {
        if (command == null || command.operationId() == null || command.resourceId() == null
                || command.type() == null || command.payload() == null || command.clientCreatedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offline operation ID, resource, type, payload and client timestamp are required");
        }
        if (command.payload().length() > MAX_PAYLOAD_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Offline operation payload is too large");
        }
        Instant now = Instant.now();
        if (command.clientCreatedAt().isAfter(now.plus(Duration.ofMinutes(5)))
                || command.clientCreatedAt().isBefore(now.minus(Duration.ofDays(30)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offline operation timestamp is outside the allowed 30-day window");
        }
        String type = command.type().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SERVICE_COMMENT_CREATE", "SURVEY_UPDATE", "SURVEY_START", "SURVEY_SUBMIT").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported offline operation type");
        }
        String hash = hash(type + "\n" + command.resourceId() + "\n" + command.payload());
        Optional<OfflineSyncOperation> existing = operations.findByTenantIdAndActorIdAndClientOperationId(
                actor.tenantId(), actor.userId(), command.operationId());
        if (existing.isPresent()) {
            OfflineSyncOperation prior = existing.get();
            if (!prior.getPayloadHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Offline operation ID was already used with a different payload");
            }
            return result(prior);
        }

        String resultJson;
        try {
            resultJson = switch (type) {
                case "SERVICE_COMMENT_CREATE" -> mapper.writeValueAsString(comments.create(
                        command.resourceId(), command.payload(), actor));
                case "SURVEY_UPDATE" -> mapper.writeValueAsString(surveys.update(
                        command.resourceId(), mapper.readValue(command.payload(), SurveyModels.UpdateRequest.class), actor));
                case "SURVEY_START" -> mapper.writeValueAsString(surveys.start(command.resourceId(), actor));
                case "SURVEY_SUBMIT" -> mapper.writeValueAsString(surveys.submit(command.resourceId(), actor));
                default -> throw new IllegalStateException("Unsupported operation");
            };
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offline operation payload is invalid");
        }

        OfflineSyncOperation row = new OfflineSyncOperation();
        row.setId(UUID.randomUUID());
        row.setTenantId(actor.tenantId());
        row.setActorId(actor.userId());
        row.setClientOperationId(command.operationId());
        row.setOperationType(type);
        row.setResourceId(command.resourceId());
        row.setPayloadHash(hash);
        row.setStatus("APPLIED");
        row.setResultJson(resultJson);
        row.setClientCreatedAt(command.clientCreatedAt());
        row.setReceivedAt(now);
        row.setAppliedAt(Instant.now());
        operations.save(row);
        AuditEvent audit = new AuditEvent();
        audit.setId(UUID.randomUUID()); audit.setTenantId(actor.tenantId()); audit.setActorId(actor.userId());
        audit.setActorType(actor.accountType()); audit.setAction("OFFLINE_SYNC_" + type);
        audit.setEntityType("OFFLINE_SYNC_OPERATION"); audit.setEntityId(row.getId().toString());
        audit.setOutcome("SUCCESS");
        try {
            audit.setAfterJson(mapper.writeValueAsString(Map.of("operationId", command.operationId().toString(),
                    "resourceId", command.resourceId().toString(), "payloadHash", hash)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize offline sync audit event", ex);
        }
        audit.setCreatedAt(Instant.now()); audits.save(audit);
        return result(row);
    }

    private SyncResult result(OfflineSyncOperation row) {
        return new SyncResult(row.getClientOperationId(), row.getOperationType(), row.getResourceId(),
                row.getStatus(), row.getResultJson(), row.getErrorMessage(), row.getAppliedAt());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record SyncCommand(UUID operationId, String type, UUID resourceId,
                              String payload, Instant clientCreatedAt) {}
    public record SyncResult(UUID operationId, String type, UUID resourceId, String status,
                             String resultJson, String errorMessage, Instant appliedAt) {}

    /**
     * Narrow service facade that preserves the exact authorization and notification behavior
     * of the existing service-comment endpoint without duplicating its policy.
     */
    static final class ServiceCommentControllerAccess {
        private final ServiceRequestRepository tickets;
        private final ServiceCommentRepository comments;
        private final CustomerProfileRepository customers;
        private final NotificationService notifications;
        private final ObjectMapper mapper;
        ServiceCommentControllerAccess(ServiceRequestRepository tickets, ServiceCommentRepository comments,
                                       CustomerProfileRepository customers, NotificationService notifications, ObjectMapper mapper) {
            this.tickets = tickets; this.comments = comments; this.customers = customers; this.notifications = notifications; this.mapper = mapper;
        }
        ServiceComment create(UUID ticketId, String payload, Principal actor) throws Exception {
            var input = mapper.readValue(payload, CommentPayload.class);
            if (input.body() == null || input.body().isBlank() || input.body().length() > 4000
                    || (input.visibility() != null && !Set.of("CUSTOMER", "INTERNAL").contains(input.visibility()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid service comment payload");
            }
            ServiceRequest ticket = tickets.findByIdAndTenantId(ticketId, actor.tenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service ticket not found"));
            if ("CUSTOMER".equals(actor.accountType())) {
                var customer = customers.findByTenantIdAndUserId(actor.tenantId(), actor.userId())
                        .filter(com.saumyasolar.domain.CustomerProfile::isActive)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
                if (!customer.getId().equals(ticket.getCustomerId())
                        || (input.visibility() != null && !"CUSTOMER".equals(input.visibility()))) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service ticket not found");
                }
            } else {
                if (!"VENDOR".equals(actor.accountType()) || actor.permissions() == null
                        || !actor.permissions().contains("SERVICE_MANAGE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                boolean manager = actor.roles() != null && actor.roles().stream()
                        .anyMatch(r -> Set.of("VENDOR_ADMIN", "SERVICE_MANAGER").contains(r));
                if (!manager && !actor.userId().equals(ticket.getAssignedEngineerId()))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            String visibility = "CUSTOMER".equals(actor.accountType()) ? "CUSTOMER"
                    : (input.visibility() == null ? "CUSTOMER" : input.visibility());
            ServiceComment comment = new ServiceComment();
            comment.setId(UUID.randomUUID()); comment.setTenantId(actor.tenantId());
            comment.setServiceRequestId(ticketId); comment.setAuthorId(actor.userId());
            comment.setVisibility(visibility); comment.setBody(input.body().trim());
            comment.setCreatedAt(Instant.now());
            ServiceComment saved = comments.save(comment);
            if ("CUSTOMER".equals(visibility) && !"CUSTOMER".equals(actor.accountType())) {
                customers.findByIdAndTenantId(ticket.getCustomerId(), actor.tenantId()).ifPresent(customer ->
                        notifications.sendBusinessNotification(customer.getUserId(), actor.tenantId(),
                                "SERVICE_TICKET_COMMENT", customer.getEmail(),
                                "Update on service request " + ticket.getTicketNumber(), input.body().trim()));
            }
            return saved;
        }
        record CommentPayload(String body, String visibility) {}
    }
}
