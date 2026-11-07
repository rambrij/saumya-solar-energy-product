package com.saumyasolar.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ServiceSlaScheduler {
    private final FinanceService finance;
    public ServiceSlaScheduler(FinanceService finance){this.finance=finance;}
    @Scheduled(fixedDelayString="${app.service.sla-check-interval-ms:60000}")
    public void evaluate(){finance.evaluateSlaBreaches();}
}
