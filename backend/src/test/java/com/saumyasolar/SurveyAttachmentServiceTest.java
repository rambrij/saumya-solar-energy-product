package com.saumyasolar;

import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.SiteSurveyAttachment;
import com.saumyasolar.repo.SiteSurveyAttachmentRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PrivateObjectStorageService;
import com.saumyasolar.service.SurveyAttachmentService;
import com.saumyasolar.service.SurveyLifecycleService;
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

class SurveyAttachmentServiceTest {
    private SiteSurveyAttachmentRepository attachments;
    private SurveyLifecycleService surveys;
    private PrivateObjectStorageService storage;
    private SurveyAttachmentService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        attachments = mock(SiteSurveyAttachmentRepository.class);
        surveys = mock(SurveyLifecycleService.class);
        storage = mock(PrivateObjectStorageService.class);
        service = new SurveyAttachmentService(tempDir.toString(), attachments, surveys, storage);
    }

    @Test
    void readOnlySurveyViewerCannotUploadEvidence() {
        UUID tenant = UUID.randomUUID();
        UUID surveyId = UUID.randomUUID();
        Principal viewer = new Principal(UUID.randomUUID(), tenant, "viewer@example.test", "VENDOR",
                Set.of("CUSTOM_SURVEY_VIEWER"), Set.of("SURVEY_VIEW"), 0);
        MockMultipartFile pdf = new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                "%PDF-1.4 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThrows(ResponseStatusException.class, () -> service.upload(surveyId, pdf, viewer));

        verify(surveys).get(surveyId, viewer);
        verifyNoInteractions(attachments, storage);
    }

    @Test
    void assignedSurveyCompleterCanUploadEvidence() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID surveyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Principal engineer = new Principal(userId, tenant, "engineer@example.test", "VENDOR",
                Set.of("CUSTOM_SERVICE_ENGINEER"), Set.of("SURVEY_VIEW", "SURVEY_COMPLETE"), 0);
        MockMultipartFile pdf = new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                "%PDF-1.4 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        when(storage.put(eq(tempDir.toAbsolutePath().normalize()), eq("surveys"), eq(tenant),
                anyString(), any(byte[].class), eq("application/pdf")))
                .thenReturn(tenant + "/surveys/evidence.pdf");
        when(attachments.save(any(SiteSurveyAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SiteSurveyAttachment saved = service.upload(surveyId, pdf, engineer);

        assertEquals(surveyId, saved.getSurveyId());
        assertEquals(tenant, saved.getTenantId());
        assertEquals(userId, saved.getUploadedBy());
        verify(surveys).get(surveyId, engineer);
        verify(storage).put(eq(tempDir.toAbsolutePath().normalize()), eq("surveys"), eq(tenant),
                anyString(), any(byte[].class), eq("application/pdf"));
        verify(attachments).save(any(SiteSurveyAttachment.class));
    }
}
