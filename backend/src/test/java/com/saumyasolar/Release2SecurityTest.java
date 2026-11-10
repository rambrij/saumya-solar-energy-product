package com.saumyasolar;

import com.saumyasolar.security.Principal;
import com.saumyasolar.api.Models.Input;
import com.saumyasolar.repo.*;
import com.saumyasolar.service.AppService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
class Release2SecurityTest {
    @Test
    void principalsKeepTenantRoleAndPermissionBoundaries() {
        var t = UUID.randomUUID();
        var p = new Principal(UUID.randomUUID(), t, "admin@test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("CATALOG_MANAGE"));
        assertEquals(t, p.tenantId());
        assertTrue(p.permissions().contains("CATALOG_MANAGE"));
        assertFalse(p.roles().contains("PLATFORM_SUPER_ADMIN"));
    }
    
    @Test
    void platformPrincipalHasNoTenant() {
        var p = new Principal(UUID.randomUUID(), null, "platform@test", "PLATFORM", Set.of("PLATFORM_SUPER_ADMIN"), Set.of("PLATFORM_MANAGE"));
        assertNull(p.tenantId());
    }
    
    @Test
    void vendorWithoutCatalogPermissionCannotListProducts() {
        var service = service(mock(ProductCategoryRepository.class), mock(ProductRepository.class));
        var principal = new Principal(UUID.randomUUID(), UUID.randomUUID(), "sales@test", "VENDOR", Set.of("SALES_EXECUTIVE"), Set.of("LEAD_MANAGE"));
        var error = assertThrows(ResponseStatusException.class, () -> service.list("products", principal));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }
    
    @Test
    void productCannotReferenceAnotherTenantsCategory() throws Exception {
        var categories = mock(ProductCategoryRepository.class);
        var products = mock(ProductRepository.class);
        var service = service(categories, products);
        var tenantId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        when(categories.findByIdAndTenantId(categoryId, tenantId)).thenReturn(Optional.empty());
        var principal = new Principal(UUID.randomUUID(), tenantId, "admin@test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("PRODUCT_CREATE"));
        
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.create("products", inputWithCategory(categoryId), principal)
        );
        
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(products, never()).save(any());
    }
    

    @Test
    void customSalesRoleWithoutLeadAssignSeesOnlyItsAssignedLeads() {
        var tenantId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var assigned = new com.saumyasolar.domain.SalesLead();
        assigned.setId(UUID.randomUUID());
        assigned.setTenantId(tenantId);
        assigned.setAssignedUserId(userId);
        var unassigned = new com.saumyasolar.domain.SalesLead();
        unassigned.setId(UUID.randomUUID());
        unassigned.setTenantId(tenantId);
        unassigned.setAssignedUserId(otherUserId);
        var leads = mock(SalesLeadRepository.class);
        var quotations = mock(QuotationRepository.class);
        when(leads.findByTenantIdOrderByIdDesc(tenantId)).thenReturn(java.util.List.of(assigned, unassigned));
        when(quotations.findByTenantIdOrderByIdDesc(tenantId)).thenReturn(java.util.List.of());
        var service = new AppService(
                mock(TenantRepository.class), mock(AppUserRepository.class), mock(ProductCategoryRepository.class),
                mock(ProductRepository.class), mock(SolarPackageRepository.class), mock(PackageItemRepository.class),
                mock(CustomerProfileRepository.class), mock(EnquiryRepository.class), leads,
                mock(LeadHistoryRepository.class), mock(SiteSurveyRepository.class), quotations,
                mock(QuotationItemRepository.class), mock(CustomerOrderRepository.class),
                mock(OrderItemRepository.class), mock(IdempotencyRecordRepository.class),
                mock(RoleRepository.class), mock(OrderStatusHistoryRepository.class),
                mock(com.saumyasolar.service.NotificationService.class), mock(com.saumyasolar.service.ApprovalWorkflowService.class)
        );
        var customSales = new Principal(userId, tenantId, "sales@test", "VENDOR",
                Set.of("CUSTOM_SALES_READ"), Set.of("LEAD_VIEW"));
        var visible = (java.util.List<com.saumyasolar.domain.SalesLead>) service.list("leads", customSales);
        assertEquals(1, visible.size());
        assertEquals(assigned.getId(), visible.get(0).getId());
    }

    private AppService service(ProductCategoryRepository categories, ProductRepository products) {
        return new AppService(
                mock(TenantRepository.class), mock(AppUserRepository.class), categories, products,
                mock(SolarPackageRepository.class), mock(PackageItemRepository.class),
                mock(CustomerProfileRepository.class), mock(EnquiryRepository.class),
                mock(SalesLeadRepository.class), mock(LeadHistoryRepository.class),
                mock(SiteSurveyRepository.class), mock(QuotationRepository.class),
                mock(QuotationItemRepository.class), mock(CustomerOrderRepository.class),
                mock(OrderItemRepository.class), mock(IdempotencyRecordRepository.class),
                mock(RoleRepository.class), mock(OrderStatusHistoryRepository.class), mock(com.saumyasolar.service.NotificationService.class),
                mock(com.saumyasolar.service.ApprovalWorkflowService.class)
        );
    }
    
    private Input inputWithCategory(UUID categoryId) throws Exception {
        var components = Input.class.getRecordComponents();
        var types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        var args = new Object[components.length];
        args[0] = "Test product";
        args[3] = "TEST-SKU";
        args[4] = categoryId;
        args[5] = BigDecimal.TEN;
        args[6] = BigDecimal.ZERO;
        var constructor = Input.class.getDeclaredConstructor(types);
        return (Input) constructor.newInstance(args);
    }
    @Test
    void assignedSalesExecutiveCannotReadAnotherEmployeesQuotationRevisions() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherAssignee = UUID.randomUUID();
        UUID quotationId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        var quotation = new com.saumyasolar.domain.Quotation();
        quotation.setId(quotationId);
        quotation.setTenantId(tenantId);
        quotation.setLeadId(leadId);
        quotation.setCreatedBy(otherAssignee);
        quotation.setQuoteGroupId(groupId);

        var lead = new com.saumyasolar.domain.SalesLead();
        lead.setId(leadId);
        lead.setTenantId(tenantId);
        lead.setAssignedUserId(otherAssignee);

        var quotations = mock(QuotationRepository.class);
        var leads = mock(SalesLeadRepository.class);
        when(quotations.findByIdAndTenantId(quotationId, tenantId)).thenReturn(Optional.of(quotation));
        when(leads.findByIdAndTenantId(leadId, tenantId)).thenReturn(Optional.of(lead));

        AppService service = new AppService(
                mock(TenantRepository.class), mock(AppUserRepository.class), mock(ProductCategoryRepository.class),
                mock(ProductRepository.class), mock(SolarPackageRepository.class), mock(PackageItemRepository.class),
                mock(CustomerProfileRepository.class), mock(EnquiryRepository.class), leads,
                mock(LeadHistoryRepository.class), mock(SiteSurveyRepository.class), quotations,
                mock(QuotationItemRepository.class), mock(CustomerOrderRepository.class),
                mock(OrderItemRepository.class), mock(IdempotencyRecordRepository.class),
                mock(RoleRepository.class), mock(OrderStatusHistoryRepository.class),
                mock(com.saumyasolar.service.NotificationService.class), mock(com.saumyasolar.service.ApprovalWorkflowService.class)
        );
        var principal = new Principal(userId, tenantId, "sales@test", "VENDOR",
                Set.of("SALES_EXECUTIVE"), Set.of("QUOTATION_VIEW", "LEAD_VIEW"));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.quotationRevisions(quotationId, principal));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verify(quotations, never()).findByQuoteGroupIdAndTenantIdOrderByRevisionAsc(groupId, tenantId);
    }

}
