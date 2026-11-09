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
}
