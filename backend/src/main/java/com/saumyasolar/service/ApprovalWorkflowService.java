package com.saumyasolar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
@Service
public class ApprovalWorkflowService {
    private final ApprovalWorkflowRepository workflows;
    private final ApprovalInstanceRepository instances;
    private final ApprovalDecisionRepository decisions;
    private final AuditEventRepository audits;
    private final ObjectMapper mapper;
    private final EnterpriseSaasService enterpriseSaas;
    
    public ApprovalWorkflowService(
            ApprovalWorkflowRepository w,
            ApprovalInstanceRepository i,
            ApprovalDecisionRepository d,
            AuditEventRepository a,
            ObjectMapper m,
            EnterpriseSaasService e
    ) {
        workflows = w;
        instances = i;
        decisions = d;
        audits = a;
        mapper = m;
        enterpriseSaas = e;
    }
    
    private UUID requireVendorPermission(Principal p, String permission) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null || p.permissions() == null
                || !p.permissions().contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return p.tenantId();
    }

    private UUID vendor(Principal p) {
        return requireVendorPermission(p, "APPROVAL_WORKFLOW_MANAGE");
    }

    private boolean assignedToApprovalStep(ApprovalInstance instance, Principal p) {
        try {
            JsonNode steps = parseSteps(instance.getStepsSnapshotJson());
            for (JsonNode step : steps) {
                String role = step.hasNonNull("approverRole") ? step.get("approverRole").asText() : null;
                String permission = step.hasNonNull("permissionCode") ? step.get("permissionCode").asText() : null;
                if (role != null && p.roles() != null && p.roles().contains(role)) return true;
                if (permission != null && p.permissions() != null && p.permissions().contains(permission)) return true;
            }
        } catch (ResponseStatusException ignored) {
            return false;
        }
        return false;
    }

    private boolean canViewInstance(ApprovalInstance instance, Principal p) {
        return p.permissions() != null && p.permissions().contains("APPROVAL_WORKFLOW_MANAGE")
                || assignedToApprovalStep(instance, p);
    }
    
    private String json(Object v) {
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private void audit(Principal p, String action, String type, UUID id, Object value) {
        AuditEvent a = new AuditEvent();
        a.setId(UUID.randomUUID());
        a.setTenantId(p.tenantId());
        a.setActorId(p.userId());
        a.setActorType(p.accountType());
        a.setAction(action);
        a.setEntityType(type);
        a.setEntityId(id.toString());
        a.setOutcome("SUCCESS");
        a.setAfterJson(json(value));
        a.setCreatedAt(Instant.now());
        audits.save(a);
    }
    
    private JsonNode parseSteps(String value) {
        try {
            JsonNode root = mapper.readTree(value);
            if (!root.isArray() || root.isEmpty() || root.size() > 20) throw new IllegalArgumentException();
            for (JsonNode step : root) {
                if (!step.isObject()) throw new IllegalArgumentException();
                boolean hasRole = step.hasNonNull("approverRole"), hasPermission = step.hasNonNull("permissionCode");
                if (!hasRole && !hasPermission) throw new IllegalArgumentException();
                if (hasRole && ( step.get("approverRole").asText().isBlank() || step.get("approverRole").asText().length() > 80 ))
                    throw new IllegalArgumentException();
                if (hasPermission && ( step.get("permissionCode").asText().isBlank() || step.get("permissionCode").asText().length() > 100 ))
                    throw new IllegalArgumentException();
                if (step.has("minAmount") && !step.get("minAmount").isNumber()) throw new IllegalArgumentException();
                if (step.has("maxAmount") && !step.get("maxAmount").isNumber()) throw new IllegalArgumentException();
                if (step.has("minAmount") && step.has("maxAmount") && step.get("minAmount").decimalValue().compareTo(step.get("maxAmount").decimalValue()) > 0)
                    throw new IllegalArgumentException();
            }
            return root;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow steps must be a JSON array of 1 to 20 steps; each step must define exactly one approverRole or permissionCode and valid amount bounds");
        }
    }
    
    @Transactional
    public ApprovalWorkflow create(WorkflowInput x, Principal p) {
        UUID t = vendor(p);
        enterpriseSaas.requireEntitlement(t, "APPROVAL_WORKFLOWS");
        String code = x.code().trim().toUpperCase(Locale.ROOT);
        String type = x.entityType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("QUOTATION", "ORDER", "INVOICE", "PURCHASE_ORDER", "SERVICE_REQUEST").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported approval entity type");
        if (workflows.existsByTenantIdAndCodeIgnoreCase(t, code))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow code already exists");
        parseSteps(x.stepsJson());
        ApprovalWorkflow w = new ApprovalWorkflow();
        w.setId(UUID.randomUUID());
        w.setTenantId(t);
        w.setCode(code);
        w.setName(x.name().trim());
        w.setEntityType(type);
        w.setStepsJson(x.stepsJson());
        w.setActive(true);
        w.setCreatedAt(Instant.now());
        w.setUpdatedAt(Instant.now());
        workflows.save(w);
        audit(p, "APPROVAL_WORKFLOW_CREATED", "APPROVAL_WORKFLOW", w.getId(), w);
        return w;
    }
    
    public List<ApprovalWorkflow> list(Principal p) {
        return workflows.findByTenantIdOrderByCreatedAtDesc(vendor(p));
    }
    
    @Transactional
    public Optional<ApprovalInstance> startIfConfigured(String entityType, UUID entityId, Principal p) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        enterpriseSaas.requireEntitlement(p.tenantId(), "APPROVAL_WORKFLOWS");
        Optional<ApprovalInstance> pending = instances.findFirstByTenantIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(p.tenantId(), entityType, entityId.toString(), "PENDING");
        if (pending.isPresent()) return pending;
        Optional<ApprovalWorkflow> found = workflows.findFirstByTenantIdAndEntityTypeAndActiveTrueOrderByUpdatedAtDesc(p.tenantId(), entityType);
        if (found.isEmpty()) return Optional.empty();
        ApprovalWorkflow w = found.get();
        parseSteps(w.getStepsJson());
        ApprovalInstance i = new ApprovalInstance();
        i.setId(UUID.randomUUID());
        i.setTenantId(p.tenantId());
        i.setWorkflowId(w.getId());
        i.setEntityType(entityType);
        i.setEntityId(entityId.toString());
        i.setStepsSnapshotJson(w.getStepsJson());
        i.setStatus("PENDING");
        i.setCurrentStep(1);
        i.setRequestedBy(p.userId());
        i.setRequestedAt(Instant.now());
        instances.save(i);
        audit(p, "APPROVAL_REQUESTED", "APPROVAL_INSTANCE", i.getId(), i);
        return Optional.of(i);
    }
    
    @Transactional
    public ApprovalInstance decide(UUID instanceId, String decision, String comments, Principal p) {
        UUID tenantId = requireVendorPermission(p, "APPROVAL_INSTANCE_DECIDE");
        ApprovalInstance i = instances.findForUpdate(instanceId, tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        return decideInternal(i, decision, comments, p);
    }
    
    @Transactional
    public Optional<String> decideForEntity(
            UUID tenantId,
            String entityType,
            UUID entityId,
            String decision,
            String comments,
            Principal p
    ) {
        if (!"VENDOR".equals(p.accountType()) || !Objects.equals(tenantId, p.tenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Optional<ApprovalInstance> pending = instances.findFirstByTenantIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(tenantId, entityType, entityId.toString(), "PENDING");
        if (pending.isEmpty()) return Optional.empty();
        ApprovalInstance locked = instances.findForUpdate(pending.get().getId(), tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        return Optional.of(decideInternal(locked, decision, comments, p).getStatus());
    }
    
    private ApprovalInstance decideInternal(ApprovalInstance i, String decision, String comments, Principal p) {
        if (!"VENDOR".equals(p.accountType()) || !Objects.equals(i.getTenantId(), p.tenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!"PENDING".equals(i.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request is not pending");
        if (!Set.of("APPROVE", "REJECT").contains(decision))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVE or REJECT");
        JsonNode steps = parseSteps(i.getStepsSnapshotJson());
        if (i.getCurrentStep() < 1 || i.getCurrentStep() > steps.size())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval workflow step is invalid");
        JsonNode step = steps.get(i.getCurrentStep() - 1);
        String role = step.hasNonNull("approverRole") ? step.get("approverRole").asText() : null;
        String permission = step.hasNonNull("permissionCode") ? step.get("permissionCode").asText() : null;
        if (role != null && ( p.roles() == null || !p.roles().contains(role) ))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current role cannot approve this step");
        if (permission != null && ( p.permissions() == null || !p.permissions().contains(permission) ))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing approval permission");
        if (role == null && permission == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval step has no valid approver");
        ApprovalDecision d = new ApprovalDecision();
        d.setId(UUID.randomUUID());
        d.setTenantId(i.getTenantId());
        d.setInstanceId(i.getId());
        d.setStepOrder(i.getCurrentStep());
        d.setDecision(decision);
        d.setActorId(p.userId());
        d.setComments(comments == null || comments.isBlank() ? null : comments.trim());
        d.setCreatedAt(Instant.now());
        decisions.save(d);
        if ("REJECT".equals(decision)) {
            i.setStatus("REJECTED");
            i.setCompletedAt(Instant.now());
        } else if (i.getCurrentStep() < steps.size()) i.setCurrentStep(i.getCurrentStep() + 1);
        else {
            i.setStatus("APPROVED");
            i.setCompletedAt(Instant.now());
        }
        instances.save(i);
        AuditEvent a = new AuditEvent();
        a.setId(UUID.randomUUID());
        a.setTenantId(i.getTenantId());
        a.setActorId(p.userId());
        a.setActorType(p.accountType());
        a.setAction("APPROVAL_" + decision);
        a.setEntityType("APPROVAL_INSTANCE");
        a.setEntityId(i.getId().toString());
        a.setOutcome("SUCCESS");
        a.setAfterJson(json(Map.of("status", i.getStatus(), "currentStep", i.getCurrentStep(), "comments", d.getComments() == null ? "" : d.getComments())));
        a.setCreatedAt(Instant.now());
        audits.save(a);
        return i;
    }
    
    public ApprovalInstance get(UUID id, Principal p) {
        UUID tenantId = requireVendorPermission(p, "APPROVAL_INSTANCE_VIEW");
        ApprovalInstance instance = instances.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        if (!canViewInstance(instance, p))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found");
        return instance;
    }

    public List<ApprovalInstance> instances(Principal p) {
        UUID tenantId = requireVendorPermission(p, "APPROVAL_INSTANCE_VIEW");
        return instances.findByTenantIdOrderByRequestedAtDesc(tenantId).stream()
                .filter(instance -> canViewInstance(instance, p))
                .toList();
    }

    public List<ApprovalDecision> history(UUID id, Principal p) {
        ApprovalInstance instance = get(id, p);
        return decisions.findByTenantIdAndInstanceIdOrderByStepOrderAscCreatedAtAsc(p.tenantId(), instance.getId());
    }
    
    public record WorkflowInput(String code, String name, String entityType, String stepsJson) {
    }
}
