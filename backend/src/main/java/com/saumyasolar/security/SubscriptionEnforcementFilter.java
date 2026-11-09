package com.saumyasolar.security;

import com.saumyasolar.domain.TenantSubscription;
import com.saumyasolar.repo.TenantSubscriptionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {
    private final TenantSubscriptionRepository subscriptions;
    
    public SubscriptionEnforcementFilter(TenantSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/public/") ||
                path.startsWith("/api/v1/payment-webhooks/") || path.startsWith("/uploads/") || path.startsWith("/actuator/") ||
                path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs/");
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Principal p && "VENDOR".equals(p.accountType()) && p.tenantId() != null) {
            List<TenantSubscription> history = subscriptions.findByTenantIdOrderByCreatedAtDesc(p.tenantId());
            if (!history.isEmpty() && !allowedDuringExpiry(request.getServletPath()) && !hasEffectiveSubscription(p.tenantId())) {
                response.setStatus(402);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"status\":402,\"error\":\"Subscription Required\",\"detail\":\"The vendor subscription is inactive or expired. Contact the platform administrator to renew or reactivate it.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    
    private boolean allowedDuringExpiry(String path) {
        return path.equals("/api/v1/subscription") || path.equals("/api/v1/subscription-plans");
    }
    
    private boolean hasEffectiveSubscription(java.util.UUID tenantId) {
        return subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(tenantId, List.of("TRIAL", "ACTIVE"))
                .filter(s -> s.getStartsAt() != null && !s.getStartsAt().isAfter(Instant.now()) && ( s.getEndsAt() == null || s.getEndsAt().isAfter(Instant.now()) )).isPresent();
    }
}
