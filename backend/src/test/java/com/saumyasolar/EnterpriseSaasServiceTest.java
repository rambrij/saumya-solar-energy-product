package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
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
class EnterpriseSaasServiceTest {
    @Mock BranchRepository branches;
    @Mock BranchMembershipRepository memberships;
    @Mock SubscriptionPlanRepository plans;
    @Mock TenantSubscriptionRepository subscriptions;
    @Mock TenantRepository tenants;
    @Mock AppUserRepository users;
    @Mock AuditEventRepository audits;
    @Mock WarehouseRepository warehouses;
    @Mock TenantDomainRepository tenantDomains;

    EnterpriseSaasService service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseSaasService(branches, memberships, plans, subscriptions, tenants,
                users, audits, warehouses, tenantDomains, new ObjectMapper());
    }

    @Test
    void branchCreationIsRejectedWhenSubscriptionBranchLimitIsReached() {
        UUID tenantId = UUID.randomUUID();
        when(tenants.findByIdForUpdate(tenantId)).thenReturn(Optional.of(new Tenant()));
        TenantSubscription subscription = subscription(tenantId);
        SubscriptionPlan plan = plan(1);
        subscription.setPlanId(plan.getId());
        when(branches.existsByTenantIdAndCodeIgnoreCase(tenantId, "HQ")).thenReturn(false);
        when(subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(tenantId, List.of("TRIAL", "ACTIVE")))
                .thenReturn(Optional.of(subscription));
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(branches.countByTenantIdAndActiveTrue(tenantId)).thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createBranch(new EnterpriseSaasService.BranchInput("HQ", "Head Office", null, null, null),
                        principal(tenantId, Set.of("BRANCH_MANAGE"))));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(branches, never()).save(any());
    }

    @Test
    void branchCreationPersistsWithinTenantAndWritesAuditEvent() {
        UUID tenantId = UUID.randomUUID();
        TenantSubscription subscription = subscription(tenantId);
        SubscriptionPlan plan = plan(3);
        subscription.setPlanId(plan.getId());
        when(branches.existsByTenantIdAndCodeIgnoreCase(tenantId, "HQ")).thenReturn(false);
        when(subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(tenantId, List.of("TRIAL", "ACTIVE")))
                .thenReturn(Optional.of(subscription));
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(branches.countByTenantIdAndActiveTrue(tenantId)).thenReturn(0L);
        when(branches.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(audits.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Branch created = service.createBranch(
                new EnterpriseSaasService.BranchInput("hq", "Head Office", "Main road", null, null),
                principal(tenantId, Set.of("BRANCH_MANAGE")));

        assertEquals(tenantId, created.getTenantId());
        assertEquals("HQ", created.getCode());
        assertTrue(created.isActive());
        verify(audits).save(argThat(event -> "BRANCH_CREATED".equals(event.getAction())
                && tenantId.equals(event.getTenantId())));
    }

    @Test
    void warehouseCannotBeAssignedToAnotherVendorsBranch() {
        UUID tenantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID foreignBranchId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setTenantId(tenantId);
        when(warehouses.findByIdAndTenantId(warehouseId, tenantId)).thenReturn(Optional.of(warehouse));
        when(branches.findByIdAndTenantId(foreignBranchId, tenantId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assignWarehouseBranch(warehouseId, foreignBranchId,
                        principal(tenantId, Set.of("INVENTORY_MANAGE"))));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(warehouses, never()).save(any());
    }

    @Test
    void brandingRejectsUnsafeLogoSchemes() {
        UUID tenantId = UUID.randomUUID();
        var input = new EnterpriseSaasService.BrandingInput("Vendor", "javascript:alert(1)",
                "#0B6B4B", "#F6C85F", null, null, null, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateBranding(input, principal(tenantId, Set.of("BRANDING_MANAGE"))));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(tenants, never()).findByIdForUpdate(any());
    }

    private TenantSubscription subscription(UUID tenantId) {
        TenantSubscription value = new TenantSubscription();
        value.setId(UUID.randomUUID());
        value.setTenantId(tenantId);
        value.setPlanId(UUID.randomUUID());
        value.setStatus("ACTIVE");
        value.setStartsAt(Instant.now().minusSeconds(60));
        return value;
    }

    private SubscriptionPlan plan(int maxBranches) {
        SubscriptionPlan value = new SubscriptionPlan();
        value.setId(UUID.randomUUID());
        value.setActive(true);
        value.setMaxBranches(maxBranches);
        return value;
    }

    private Principal principal(UUID tenantId, Set<String> permissions) {
        return new Principal(UUID.randomUUID(), tenantId, "vendor@example.test", "VENDOR",
                Set.of("VENDOR_ADMIN"), permissions, 0);
    }
}
