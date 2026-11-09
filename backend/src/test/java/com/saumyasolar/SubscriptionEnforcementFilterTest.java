package com.saumyasolar;

import com.saumyasolar.domain.TenantSubscription;
import com.saumyasolar.repo.TenantSubscriptionRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.security.SubscriptionEnforcementFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionEnforcementFilterTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }
    
    @Test
    void expiredVendorSubscriptionBlocksBusinessApisButAllowsSubscriptionRead() throws Exception {
        TenantSubscriptionRepository repository = mock(TenantSubscriptionRepository.class);
        UUID tenant = UUID.randomUUID();
        when(repository.findByTenantIdOrderByCreatedAtDesc(tenant)).thenReturn(List.of(new TenantSubscription()));
        when(repository.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(tenant, List.of("TRIAL", "ACTIVE"))).thenReturn(Optional.empty());
        authenticate(tenant);
        SubscriptionEnforcementFilter filter = new SubscriptionEnforcementFilter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.setServletPath("/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        
        filter.doFilter(request, response, chain);
        assertEquals(402, response.getStatus());
        assertNull(chain.getRequest());
        
        MockHttpServletRequest subscriptionRequest = new MockHttpServletRequest("GET", "/api/v1/subscription");
        subscriptionRequest.setServletPath("/api/v1/subscription");
        MockHttpServletResponse subscriptionResponse = new MockHttpServletResponse();
        MockFilterChain subscriptionChain = new MockFilterChain();
        filter.doFilter(subscriptionRequest, subscriptionResponse, subscriptionChain);
        assertNotNull(subscriptionChain.getRequest());
    }
    
    @Test
    void activeSubscriptionAllowsVendorApiAccess() throws Exception {
        TenantSubscriptionRepository repository = mock(TenantSubscriptionRepository.class);
        UUID tenant = UUID.randomUUID();
        TenantSubscription active = new TenantSubscription();
        active.setStatus("ACTIVE");
        active.setStartsAt(Instant.now().minusSeconds(60));
        active.setEndsAt(Instant.now().plusSeconds(3600));
        when(repository.findByTenantIdOrderByCreatedAtDesc(tenant)).thenReturn(List.of(active));
        when(repository.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(tenant, List.of("TRIAL", "ACTIVE"))).thenReturn(Optional.of(active));
        authenticate(tenant);
        SubscriptionEnforcementFilter filter = new SubscriptionEnforcementFilter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.setServletPath("/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }
    
    private void authenticate(UUID tenant) {
        Principal p = new Principal(UUID.randomUUID(), tenant, "vendor@example.test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of(), 0);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null));
    }
}
