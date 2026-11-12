package com.saumyasolar.service;

import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.TenantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ServiceSlaSchedulerTest {
    @Test
    void evaluatesEachTenantWithExplicitTenantId() {
        FinanceService finance = mock(FinanceService.class);
        TenantRepository tenants = mock(TenantRepository.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Tenant first = new Tenant(); first.setId(firstId);
        Tenant second = new Tenant(); second.setId(secondId);
        when(tenants.findAll()).thenReturn(List.of(first, second));

        new ServiceSlaScheduler(finance, tenants).evaluate();

        verify(finance).evaluateSlaBreaches(firstId);
        verify(finance).evaluateSlaBreaches(secondId);
        verifyNoMoreInteractions(finance);
    }
}
