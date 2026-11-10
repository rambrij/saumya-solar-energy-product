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

        byte[] privateCsv = "id,total\\n1,10.00\\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(jobs.findByIdAndTenantIdAndRequestedBy(job.getId(), tenant, job.getRequestedBy()))
                .thenReturn(Optional.of(job));
        when(storage.get(any(), eq(job.getStorageKey()))).thenReturn(privateCsv);
        ExportService.Download ownerDownload = service().download(
                job.getId(), principal(tenant, job.getRequestedBy(), Set.of("REPORT_EXPORT")));
        assertArrayEquals(privateCsv, ownerDownload.bytes());
        verify(storage).get(any(), eq(job.getStorageKey()));
    }

    @Test
    void scheduledRecoveryRepublishesPersistedPendingExportJobs() {
        UUID tenant = UUID.randomUUID();
        ExportJob pending = new ExportJob();
        pending.setId(UUID.randomUUID());
        pending.setTenantId(tenant);
        pending.setStatus("PENDING");
        when(jobs.findDistinctTenantIdsForRecovery(any())).thenReturn(java.util.List.of(tenant));
        when(jobs.findRecoverableByTenant(eq(tenant), any(), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> {
                    assertEquals(tenant, com.saumyasolar.security.TenantExecutionContext.requireTenantId());
                    return java.util.List.of(pending);
                });

        service().recoverAndExpire();

        verify(events).publishEvent(argThat(event -> event instanceof ExportRequestedEvent requested
                && pending.getId().equals(requested.exportId()) && tenant.equals(requested.tenantId())));
        verify(jobs, never()).claim(any(), any(), any(), any());
    }

    @Test
    void statusLookupIsRestrictedToTheRequestingTenantAndUser() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();
        when(jobs.findByIdAndTenantIdAndRequestedBy(id, tenant, user)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service().status(id, principal(tenant, user, Set.of("REPORT_EXPORT"))));
        verify(jobs).findByIdAndTenantIdAndRequestedBy(id, tenant, user);

        UUID foreignTenant = UUID.randomUUID();
        Principal sameUserIdFromAnotherTenant = principal(foreignTenant, user, Set.of("REPORT_EXPORT"));
        when(jobs.findByIdAndTenantIdAndRequestedBy(id, foreignTenant, user)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service().status(id, sameUserIdFromAnotherTenant));
        verify(jobs).findByIdAndTenantIdAndRequestedBy(id, foreignTenant, user);
    }
    @Test
    void expiryCleanupLoadsAndDeletesExportsInsideTheirTenantScope() throws Exception {
        UUID tenant = UUID.randomUUID();
        ExportJob expired = new ExportJob();
        expired.setId(UUID.randomUUID());
        expired.setTenantId(tenant);
        expired.setStatus("COMPLETED");
        expired.setStorageKey(tenant + "/exports/" + expired.getId() + ".csv");
        when(jobs.findDistinctTenantIdsForExpiry(any())).thenReturn(java.util.List.of(tenant));
        when(jobs.findTop100ByTenantIdAndStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(tenant), eq("COMPLETED"), any())).thenAnswer(invocation -> {
                    assertEquals(tenant, com.saumyasolar.security.TenantExecutionContext.requireTenantId());
                    return java.util.List.of(expired);
                });
        when(jobs.save(any(ExportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertEquals(tenant, com.saumyasolar.security.TenantExecutionContext.requireTenantId());
            return null;
        }).when(storage).delete(any(), eq(expired.getStorageKey()));

        service().recoverAndExpire();

        assertEquals("EXPIRED", expired.getStatus());
        assertNull(expired.getStorageKey());
        verify(storage).delete(any(), eq(tenant + "/exports/" + expired.getId() + ".csv"));
        assertThrows(IllegalStateException.class,
                com.saumyasolar.security.TenantExecutionContext::requireTenantId);
    }


    @Test
    void downloadCannotBeClaimedByAnotherUserOrTenant() {
        UUID tenant = UUID.randomUUID(), owner = UUID.randomUUID(), otherUser = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();
        Principal attacker = principal(tenant, otherUser, Set.of("REPORT_EXPORT"));
        when(jobs.findByIdAndTenantIdAndRequestedBy(exportId, tenant, otherUser)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service().download(exportId, attacker));

        verify(jobs).findByIdAndTenantIdAndRequestedBy(exportId, tenant, otherUser);

        UUID foreignTenant = UUID.randomUUID();
        Principal sameUserFromAnotherTenant = principal(foreignTenant, owner, Set.of("REPORT_EXPORT"));
        when(jobs.findByIdAndTenantIdAndRequestedBy(exportId, foreignTenant, owner)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service().download(exportId, sameUserFromAnotherTenant));
        verify(jobs).findByIdAndTenantIdAndRequestedBy(exportId, foreignTenant, owner);
        verifyNoInteractions(storage);
    }

    @Test
    void downloadRejectsPendingAndExpiredExportsBeforePrivateStorageAccess() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
        Principal owner = principal(tenant, user, Set.of("REPORT_EXPORT"));
        UUID pendingId = UUID.randomUUID();
        ExportJob pending = new ExportJob();
        pending.setId(pendingId);
        pending.setTenantId(tenant);
        pending.setRequestedBy(user);
        pending.setStatus("PENDING");
        when(jobs.findByIdAndTenantIdAndRequestedBy(pendingId, tenant, user)).thenReturn(Optional.of(pending));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> service().download(pendingId, owner))
                        .getStatusCode());

        UUID expiredId = UUID.randomUUID();
        ExportJob expired = new ExportJob();
        expired.setId(expiredId);
        expired.setTenantId(tenant);
        expired.setRequestedBy(user);
        expired.setStatus("COMPLETED");
        expired.setStorageKey(tenant + "/exports/" + expiredId + ".csv");
        expired.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        when(jobs.findByIdAndTenantIdAndRequestedBy(expiredId, tenant, user)).thenReturn(Optional.of(expired));
        assertEquals(org.springframework.http.HttpStatus.GONE,
                assertThrows(ResponseStatusException.class, () -> service().download(expiredId, owner))
                        .getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    void expiryCleanupRetainsStorageKeyWhenDeletionFailsForRetry() throws Exception {
        UUID tenant = UUID.randomUUID();
        ExportJob expired = new ExportJob();
        expired.setId(UUID.randomUUID());
        expired.setTenantId(tenant);
        expired.setStatus("COMPLETED");
        String key = tenant + "/exports/" + expired.getId() + ".csv";
        expired.setStorageKey(key);
        when(jobs.findDistinctTenantIdsForExpiry(any())).thenReturn(java.util.List.of(tenant));
        when(jobs.findTop100ByTenantIdAndStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(tenant), eq("COMPLETED"), any())).thenReturn(java.util.List.of(expired));
        doThrow(new java.io.IOException("storage temporarily unavailable"))
                .when(storage).delete(any(), eq(key));

        service().recoverAndExpire();

        assertEquals("COMPLETED", expired.getStatus());
        assertEquals(key, expired.getStorageKey());
        verify(jobs, never()).save(expired);
        assertThrows(IllegalStateException.class,
                com.saumyasolar.security.TenantExecutionContext::requireTenantId);
    }

}
