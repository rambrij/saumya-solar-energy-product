package com.saumyasolar.service;

import com.saumyasolar.api.Phase2OperationsController;
import com.saumyasolar.domain.ExportJob;
import com.saumyasolar.repo.ExportJobRepository;
import com.saumyasolar.security.Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {
    @Mock ExportJobRepository jobs;
    @Mock ApplicationEventPublisher events;
    @Mock Phase2OperationsController reports;
    @Mock PrivateObjectStorageService storage;

    private ExportService service() {
        return new ExportService(jobs, events, reports, storage, "./target/test-private-exports", 24);
    }

    private Principal principal(UUID tenant, UUID user, Set<String> permissions) {
        return new Principal(user, tenant, "vendor@example.test", "VENDOR", Set.of("VENDOR_ADMIN"), permissions);
    }

    @Test
    void createPersistsTenantScopedPendingJobBeforePublishingEvent() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
        when(jobs.save(any(ExportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExportJob job = service().create(principal(tenant, user, Set.of("REPORT_EXPORT")),
                "OPERATIONS", "CSV", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals("PENDING", job.getStatus());
        assertEquals(tenant, job.getTenantId());
        assertEquals(user, job.getRequestedBy());
        assertEquals("CSV", job.getFormat());
        verify(jobs).save(job);
        verify(events).publishEvent(argThat(event -> event instanceof ExportRequestedEvent requested
                && job.getId().equals(requested.exportId()) && tenant.equals(requested.tenantId())));
    }

    @Test
    void createRequiresExplicitReportExportPermission() {
        Principal p = principal(UUID.randomUUID(), UUID.randomUUID(), Set.of("REPORT_VIEW"));
        assertThrows(ResponseStatusException.class, () -> service().create(p, "OPERATIONS", "CSV", null, null));
        verifyNoInteractions(jobs, events);
    }

    @Test
    void processingCreatesPrivateFileAndCompletesJob() throws Exception {
        UUID tenant = UUID.randomUUID();
        ExportJob job = new ExportJob();
        job.setId(UUID.randomUUID()); job.setTenantId(tenant); job.setRequestedBy(UUID.randomUUID());
        job.setReportType("OPERATIONS"); job.setFormat("CSV"); job.setStatus("PROCESSING");
        when(jobs.claim(eq(job.getId()), eq(tenant), any(java.time.Instant.class), any(java.time.Instant.class))).thenReturn(1);
        when(jobs.findByIdAndTenantId(job.getId(), tenant)).thenReturn(Optional.of(job));
        when(reports.operationsReportForTenant(eq(tenant), nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(Map.of("orders", 3, "amount", new BigDecimal("10.00")));
        when(storage.putReplacing(any(), eq("exports"), eq(tenant), anyString(), any(), anyString()))
                .thenReturn(tenant + "/exports/" + job.getId() + ".csv");
        when(jobs.save(any(ExportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().process(job.getId(), tenant);

        assertEquals("COMPLETED", job.getStatus());
        assertNotNull(job.getStorageKey());
        assertEquals("text/csv; charset=UTF-8", job.getContentType());
        assertTrue(job.getFileSize() > 0);
        assertNotNull(job.getExpiresAt());
        verify(storage).putReplacing(any(), eq("exports"), eq(tenant), anyString(), any(), anyString());
        verify(jobs).save(job);
    }

    @Test
    void statusLookupIsRestrictedToTheRequestingTenantAndUser() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();
        when(jobs.findByIdAndTenantIdAndRequestedBy(id, tenant, user)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service().status(id, principal(tenant, user, Set.of("REPORT_EXPORT"))));
        verify(jobs).findByIdAndTenantIdAndRequestedBy(id, tenant, user);
    }
}
