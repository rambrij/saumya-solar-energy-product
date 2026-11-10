package com.saumyasolar.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.TenantExecutionContext;

@Component
public class ServiceSlaScheduler {
    private final FinanceService finance;
    private final TenantRepository tenants;
    
    public ServiceSlaScheduler(FinanceService finance, TenantRepository tenants) {
        this.finance = finance;
        this.tenants = tenants;
    }
    
    @Scheduled(fixedDelayString = "${app.service.sla-check-interval-ms:60000}")
    public void evaluate() {
        for (Tenant tenant : tenants.findAll()) {
            try (var scope = TenantExecutionContext.open(tenant.getId())) {
                finance.evaluateSlaBreaches(tenant.getId());
            }
        }
    }
}
