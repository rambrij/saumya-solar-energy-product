package com.saumyasolar;

import com.saumyasolar.domain.ServiceAttachment;
import com.saumyasolar.repo.ServiceAttachmentRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.FinanceService;
import com.saumyasolar.service.PrivateObjectStorageService;
import com.saumyasolar.service.ServiceAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceAttachmentServiceTest {
    private ServiceAttachmentRepository attachments;
    private FinanceService finance;
    private PrivateObjectStorageService storage;
    private ServiceAttachmentService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        attachments = mock(ServiceAttachmentRepository.class);
        finance = mock(FinanceService.class);
        storage = mock(PrivateObjectStorageService.class);
        service = new ServiceAttachmentService(tempDir.toString(), attachments, finance, storage);
    }


    @Test
    void assignedReadOnlyEngineerCanDownloadEvidenceAfterTicketScopeCheck() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Principal viewer = new Principal(UUID.randomUUID(), tenant, "viewer@example.test", "VENDOR",
                Set.of("CUSTOM_SERVICE_ENGINEER"), Set.of("SERVICE_VIEW"), 0);
        ServiceAttachment attachment = new ServiceAttachment();
        attachment.setId(attachmentId);
        attachment.setTenantId(tenant);
        attachment.setServiceRequestId(ticketId);
        attachment.setStorageKey("private/service-evidence.pdf");
        attachment.setContentType("application/pdf");
        byte[] bytes = "%PDF-1.4 private evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(attachments.findByIdAndTenantIdAndServiceRequestId(attachmentId, tenant, ticketId))
                .thenReturn(java.util.Optional.of(attachment));
        when(storage.get(eq(tempDir.toAbsolutePath().normalize()), eq(attachment.getStorageKey()))).thenReturn(bytes);

        ServiceAttachmentService.Download download = service.download(ticketId, attachmentId, viewer);

        assertArrayEquals(bytes, download.content());
        verify(finance).getTicket(ticketId, viewer);
        verify(storage).get(eq(tempDir.toAbsolutePath().normalize()), eq(attachment.getStorageKey()));
    }

    @Test
    void unassignedEngineerCannotDownloadAnotherEngineersEvidence() {
        UUID tenant = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Principal unassigned = new Principal(UUID.randomUUID(), tenant, "other@example.test", "VENDOR",
                Set.of("CUSTOM_SERVICE_ENGINEER"), Set.of("SERVICE_VIEW"), 0);
        org.springframework.web.server.ResponseStatusException hidden = new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND);
        doThrow(hidden).when(finance).getTicket(ticketId, unassigned);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.download(ticketId, attachmentId, unassigned));

        verify(finance).getTicket(ticketId, unassigned);
        verifyNoInteractions(attachments, storage);
    }

    @Test
    void readOnlyServiceViewerCannotUploadEvidence() {
        UUID tenant = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Principal viewer = new Principal(UUID.randomUUID(), tenant, "viewer@example.test", "VENDOR",
                Set.of("CUSTOM_SERVICE_VIEWER"), Set.of("SERVICE_VIEW"), 0);
        MockMultipartFile pdf = new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                "%PDF-1.4 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThrows(ResponseStatusException.class, () -> service.upload(ticketId, pdf, viewer));

        verify(finance).getTicket(ticketId, viewer);
        verifyNoInteractions(attachments, storage);
    }

    @Test
    void assignedServiceUpdaterCanUploadEvidenceAfterTicketScopeCheck() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Principal updater = new Principal(userId, tenant, "engineer@example.test", "VENDOR",
                Set.of("CUSTOM_SERVICE_ENGINEER"), Set.of("SERVICE_VIEW", "SERVICE_UPDATE"), 0);
        MockMultipartFile pdf = new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                "%PDF-1.4 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        when(storage.put(eq(tempDir.toAbsolutePath().normalize()), eq("service-tickets"), eq(tenant),
                anyString(), any(byte[].class), eq("application/pdf")))
                .thenReturn(tenant + "/service-tickets/evidence.pdf");
        when(attachments.save(any(ServiceAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceAttachment saved = service.upload(ticketId, pdf, updater);

        assertEquals(ticketId, saved.getServiceRequestId());
        assertEquals(tenant, saved.getTenantId());
        assertEquals(userId, saved.getUploadedBy());
        verify(finance).getTicket(ticketId, updater);
        verify(storage).put(eq(tempDir.toAbsolutePath().normalize()), eq("service-tickets"), eq(tenant),
                anyString(), any(byte[].class), eq("application/pdf"));
        verify(attachments).save(any(ServiceAttachment.class));
    }
}
