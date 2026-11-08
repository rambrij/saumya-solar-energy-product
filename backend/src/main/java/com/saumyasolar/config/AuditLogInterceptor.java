package com.saumyasolar.config;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditLogInterceptor implements HandlerInterceptor {
    private final AuditLogService audit;
    public AuditLogInterceptor(AuditLogService audit) { this.audit=audit; }

    @Override
    public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) {
        if (!request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/v1/public/")) return;
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Principal actor)) return;
        try {
            audit.record(actor,request.getMethod(),request.getRequestURI(),response.getStatus(),request.getRemoteAddr());
        } catch (RuntimeException ignored) {
            // Audit persistence must not mask the business response; infrastructure monitoring should alert on DB failures.
        }
    }
}
