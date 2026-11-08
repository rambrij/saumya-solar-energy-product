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
import java.time.Duration;
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
    @Mock SubscriptionAssignmentHistoryRepository subscriptionHistory;
    @Mock ProductRepository productRecords;
    @Mock CustomerProfileRepository customerProfiles;

    EnterpriseSaasService service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseSaasService(branches, memberships, plans, subscriptions, tenants,
                users, audits, warehouses, tenantDomains, new ObjectMapper(), subscriptionHistory, productRecords, customerProfiles);
    }

    @Test
    void branchCreationIsRejectedWhenSubscriptionBranchLimitIsReached() {
        UUID tenantId = UUID.randomUUID();
        when(tenants.findByIdForUpdate(tenantId)).thenReturn(Optional.of(new Tenant()));
        TenantSubscription subscription = subscription(tenantId);
        SubscriptionPlan plan = plan(1);
        subscription.setPlanId(plan.getId());
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(subscription));
        when(branches.existsByTenantIdAndCodeIgnoreCase(tenantId, "HQ")).thenReturn(false);
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(subscription));

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
        when(tenants.findByIdForUpdate(tenantId)).thenReturn(Optional.of(new Tenant()));
        TenantSubscription subscription = subscription(tenantId);
        SubscriptionPlan plan = plan(3);
        subscription.setPlanId(plan.getId());
        when(branches.existsByTenantIdAndCodeIgnoreCase(tenantId, "HQ")).thenReturn(false);
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(subscription));

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
                        principal(tenantId, Set.of("INVENTORY_MANAGE", "BRANCH_MANAGE"))));

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

    @Test
    void scheduledSubscriptionDoesNotEndCurrentSubscriptionEarlyOrExtendItsExistingEnd() {
        UUID tenantId=UUID.randomUUID();
        Tenant tenant=new Tenant();
        when(tenants.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
        SubscriptionPlan plan=plan(3);
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        Instant now=Instant.now();
        TenantSubscription current=subscription(tenantId);
        current.setEndsAt(now.plus(Duration.ofDays(10)));
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(current));
        when(subscriptions.save(any(TenantSubscription.class))).thenAnswer(invocation->invocation.getArgument(0));
        when(audits.save(any(AuditEvent.class))).thenAnswer(invocation->invocation.getArgument(0));
        Instant scheduledStart=now.plus(Duration.ofDays(5));
        Principal platform=new Principal(UUID.randomUUID(),null,"platform@example.test","PLATFORM",
                Set.of("PLATFORM_SUPER_ADMIN"),Set.of("SUBSCRIPTION_MANAGE"),0);

        service.assignSubscription(tenantId,plan.getId(),
                new EnterpriseSaasService.SubscriptionInput("ACTIVE",scheduledStart,null,null,"scheduled","MONTHLY",null,true,"scheduled replacement test"),platform);

        assertEquals("ACTIVE",current.getStatus());
        assertEquals(scheduledStart,current.getEndsAt());
    }

    @Test
    void branchWarehouseRequiresExplicitMembershipForNonAdmin() {
        UUID tenantId=UUID.randomUUID(),userId=UUID.randomUUID(),branchId=UUID.randomUUID();
        Warehouse warehouse=new Warehouse();warehouse.setTenantId(tenantId);warehouse.setBranchId(branchId);
        when(memberships.findByTenantIdAndUserId(tenantId,userId)).thenReturn(List.of());
        Principal employee=new Principal(userId,tenantId,"employee@example.test","VENDOR",Set.of("SALES"),Set.of(),0);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.assertWarehouseAccess(warehouse,employee));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
    }

    @Test
    void subscriptionFeatureCanBeDeniedByPlanEntitlements() {
        UUID tenantId=UUID.randomUUID();
        TenantSubscription active=subscription(tenantId);
        SubscriptionPlan plan=plan(2);plan.setEntitlementsJson("{\"features\":{\"APPROVAL_WORKFLOWS\":false}}");
        active.setPlanId(plan.getId());
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(active));
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.requireEntitlement(tenantId,"APPROVAL_WORKFLOWS"));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
    }

    @Test
    void platformCanCreatePlanWithValidatedLimitsAndAudit() {
        when(plans.findByPlanCodeIgnoreCase("GROWTH")).thenReturn(Optional.empty());
        when(plans.save(any(SubscriptionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(audits.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Principal platform=new Principal(UUID.randomUUID(),null,"platform@example.test","PLATFORM",
                Set.of("PLATFORM_SUPER_ADMIN"),Set.of("SUBSCRIPTION_MANAGE"),0);
        var input=new EnterpriseSaasService.PlanInput("growth","Growth","Growing teams","MONTHLY",
                java.math.BigDecimal.valueOf(4999),"INR",25,3,3,10240,2000,2000,14,4,"{\"branches\":true}");
        SubscriptionPlan created=service.createPlan(input,platform);
        assertEquals("GROWTH",created.getPlanCode());
        assertEquals(2000,created.getMaxProducts());
        assertTrue(created.isActive());
        verify(audits).save(argThat(event->"PLAN_CREATED".equals(event.getAction())));
    }

    @Test
    void planWithAssignmentHistoryCannotBePhysicallyDeleted() {
        UUID planId=UUID.randomUUID();SubscriptionPlan plan=plan(2);plan.setId(planId);
        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptions.existsByPlanId(planId)).thenReturn(true);
        Principal platform=new Principal(UUID.randomUUID(),null,"platform@example.test","PLATFORM",
                Set.of("PLATFORM_SUPER_ADMIN"),Set.of("SUBSCRIPTION_MANAGE"),0);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.deletePlan(planId,platform));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verify(plans,never()).delete(any(SubscriptionPlan.class));
    }

    @Test
    void productLimitIsEnforcedFromAssignedPlan() {
        UUID tenantId=UUID.randomUUID();TenantSubscription assigned=subscription(tenantId);SubscriptionPlan plan=plan(2);
        plan.setMaxProducts(1);assigned.setPlanId(plan.getId());
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(assigned));
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(productRecords.countByTenantIdAndActiveTrue(tenantId)).thenReturn(1L);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.enforceProductLimit(tenantId));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
    }

    @Test
    void customerLimitIsEnforcedFromAssignedPlan() {
        UUID tenantId=UUID.randomUUID();TenantSubscription assigned=subscription(tenantId);SubscriptionPlan plan=plan(2);
        plan.setMaxCustomers(2);assigned.setPlanId(plan.getId());
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(assigned));
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(customerProfiles.countByTenantIdAndActiveTrue(tenantId)).thenReturn(2L);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.enforceCustomerLimit(tenantId));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
    }

    @Test
    void vendorCannotManagePlatformSubscriptionPlans() {
        Principal vendor=principal(UUID.randomUUID(),Set.of("SUBSCRIPTION_MANAGE"));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.allPlans(vendor));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
        verify(plans,never()).findAllByOrderByDisplayOrderAscNameAsc();
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
        value.setEntitlementsJson("{\"branches\":true,\"multiWarehouse\":true,\"customBranding\":true,\"approvalWorkflows\":true,\"accountingIntegration\":true,\"paymentGateway\":true,\"apiIntegrations\":true}");
        return value;
    }

    private Principal principal(UUID tenantId, Set<String> permissions) {
        return new Principal(UUID.randomUUID(), tenantId, "vendor@example.test", "VENDOR",
                Set.of("VENDOR_ADMIN"), permissions, 0);
    }
}
