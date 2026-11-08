package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.ApprovalWorkflowService;
import com.saumyasolar.service.EnterpriseSaasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowServiceTest {
    @Mock ApprovalWorkflowRepository workflows;
    @Mock ApprovalInstanceRepository instances;
    @Mock ApprovalDecisionRepository decisions;
    @Mock AuditEventRepository audits;
    @Mock EnterpriseSaasService enterpriseSaas;
    ApprovalWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalWorkflowService(workflows, instances, decisions, audits, new ObjectMapper(), enterpriseSaas);
        doNothing().when(enterpriseSaas).requireEntitlement(any(UUID.class), eq("APPROVAL_WORKFLOWS"));
    }

    @Test
    void configuredWorkflowRequiresEveryStepBeforeApprovalCompletes() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(UUID.randomUUID());
        workflow.setTenantId(tenantId);
        workflow.setEntityType("QUOTATION");
        workflow.setStepsJson("[{\"approverRole\":\"SALES_MANAGER\"},{\"approverRole\":\"VENDOR_ADMIN\"}]");
        workflow.setActive(true);
        when(workflows.findFirstByTenantIdAndEntityTypeAndActiveTrueOrderByUpdatedAtDesc(tenantId, "QUOTATION"))
                .thenReturn(Optional.of(workflow));
        when(instances.save(any(ApprovalInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(decisions.save(any(ApprovalDecision.class))).thenAnswer(inv -> inv.getArgument(0));
        when(audits.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Principal requester = principal(userId, tenantId, Set.of("SALES_MANAGER"), Set.of());
        ApprovalInstance instance = service.startIfConfigured("QUOTATION", UUID.randomUUID(), requester).orElseThrow();

        when(instances.findForUpdate(instance.getId(), tenantId)).thenReturn(Optional.of(instance));
        ApprovalInstance first = service.decide(instance.getId(), "APPROVE", "Reviewed by sales", requester);
        assertEquals("PENDING", first.getStatus());
        assertEquals(2, first.getCurrentStep());

        Principal finalApprover = principal(UUID.randomUUID(), tenantId, Set.of("VENDOR_ADMIN"), Set.of());
        ApprovalInstance completed = service.decide(instance.getId(), "APPROVE", "Final approval", finalApprover);
        assertEquals("APPROVED", completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        verify(decisions, times(2)).save(any(ApprovalDecision.class));
    }

    @Test
    void workflowDecisionCannotBeReadOrChangedAcrossTenants() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        when(instances.findForUpdate(instanceId, tenantId)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.decide(instanceId, "APPROVE", null, principal(UUID.randomUUID(), tenantId, Set.of("VENDOR_ADMIN"), Set.of())));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(decisions, never()).save(any());
    }

    @Test
    void malformedWorkflowStepsAreRejected() {
        Principal p = principal(UUID.randomUUID(), UUID.randomUUID(), Set.of("VENDOR_ADMIN"), Set.of("APPROVAL_WORKFLOW_MANAGE"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new ApprovalWorkflowService.WorkflowInput("QUOTE", "Quote approval", "QUOTATION", "[{}]"), p));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private Principal principal(UUID userId, UUID tenantId, Set<String> roles, Set<String> permissions) {
        return new Principal(userId, tenantId, "approver@example.test", "VENDOR", roles, permissions, 0);
    }
}
