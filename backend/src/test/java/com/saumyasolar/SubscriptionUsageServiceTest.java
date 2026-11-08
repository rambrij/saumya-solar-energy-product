package com.saumyasolar;

import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.SubscriptionUsageService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionUsageServiceTest {
    @Test
    void recordsUsageForGrandfatheredTenantWithoutInventingPlanLimits() {
        TenantSubscriptionRepository subscriptions=mock(TenantSubscriptionRepository.class);
        SubscriptionPlanRepository plans=mock(SubscriptionPlanRepository.class);
        AppUserRepository users=mock(AppUserRepository.class);
        BranchRepository branches=mock(BranchRepository.class);
        WarehouseRepository warehouses=mock(WarehouseRepository.class);
        SubscriptionUsageRepository usage=mock(SubscriptionUsageRepository.class);
        TenantRepository tenants=mock(TenantRepository.class);
        UUID tenantId=UUID.randomUUID();
        when(tenants.findByIdForUpdate(tenantId)).thenReturn(Optional.of(new Tenant()));
        when(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(users.findByTenantId(tenantId)).thenReturn(List.of());
        when(branches.countByTenantIdAndActiveTrue(tenantId)).thenReturn(0L);
        when(warehouses.findByTenantIdOrderByNameAsc(tenantId)).thenReturn(List.of());
        when(usage.findByTenantIdAndMeterCodeAndPeriodStartAndPeriodEnd(eq(tenantId),anyString(),any(),any())).thenReturn(Optional.empty());
        when(usage.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        SubscriptionUsageService service=new SubscriptionUsageService(subscriptions,plans,users,branches,warehouses,usage,tenants);
        Principal vendor=new Principal(UUID.randomUUID(),tenantId,"vendor@example.test","VENDOR",Set.of("VENDOR_ADMIN"),Set.of(),0);

        Map<String,Object> result=service.current(vendor);

        assertEquals("GRANDFATHERED",result.get("subscriptionStatus"));
        assertEquals(3,((List<?>)result.get("meters")).size());
        verify(usage,times(3)).save(any());
    }
}
