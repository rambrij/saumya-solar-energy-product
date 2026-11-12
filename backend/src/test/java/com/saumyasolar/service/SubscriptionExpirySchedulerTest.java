package com.saumyasolar.service;

import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.repo.TenantSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class SubscriptionExpirySchedulerTest {
    @Test
    void queriesExpiredSubscriptionsSeparatelyForEachTenant() {
        TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
        AuditEventRepository audits = mock(AuditEventRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Tenant first = new Tenant(); first.setId(firstId);
        Tenant second = new Tenant(); second.setId(secondId);
        when(tenants.findAll()).thenReturn(List.of(first, second));
        when(subscriptions.findByTenantIdAndStatusInAndEndsAtBefore(any(), anyList(), any(Instant.class)))
                .thenReturn(List.of());

        new SubscriptionExpiryScheduler(subscriptions, audits, tenants).expireSubscriptions();

        verify(subscriptions).findByTenantIdAndStatusInAndEndsAtBefore(eq(firstId), anyList(), any(Instant.class));
        verify(subscriptions).findByTenantIdAndStatusInAndEndsAtBefore(eq(secondId), anyList(), any(Instant.class));
        verifyNoInteractions(audits);
    }
}
