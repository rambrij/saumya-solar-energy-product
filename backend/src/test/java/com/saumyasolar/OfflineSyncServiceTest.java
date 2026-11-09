package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.OfflineSyncOperation;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfflineSyncServiceTest {
    @Test
    void appliesSurveyStartOnceAndReturnsStoredResultForDuplicateOperation() throws Exception {
        OfflineSyncOperationRepository operations = mock(OfflineSyncOperationRepository.class);
        ServiceRequestRepository tickets = mock(ServiceRequestRepository.class);
        ServiceCommentRepository comments = mock(ServiceCommentRepository.class);
        CustomerProfileRepository customers = mock(CustomerProfileRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        SurveyLifecycleService surveys = mock(SurveyLifecycleService.class);
        SiteSurvey survey = new SiteSurvey();
        UUID tenant = UUID.randomUUID(), actorId = UUID.randomUUID(), surveyId = UUID.randomUUID(), operationId = UUID.randomUUID();
        Principal actor = new Principal(
                actorId, tenant, "engineer@example.test", "VENDOR", Set.of("SERVICE_ENGINEER"),
                Set.of("SURVEY_MANAGE"), 0
        );
        OfflineSyncOperation stored = new OfflineSyncOperation();
        stored.setId(UUID.randomUUID());
        stored.setTenantId(tenant);
        stored.setActorId(actorId);
        stored.setClientOperationId(operationId);
        stored.setOperationType("SURVEY_START");
        stored.setResourceId(surveyId);
        stored.setPayloadHash(hash("SURVEY_START\n" + surveyId + "\n{}"));
        stored.setStatus("APPLIED");
        stored.setResultJson(new ObjectMapper().writeValueAsString(survey));
        stored.setAppliedAt(Instant.now());
        when(operations.findByTenantIdAndActorIdAndClientOperationId(tenant, actorId, operationId))
                .thenReturn(Optional.empty(), Optional.of(stored));
        when(operations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(surveys.start(surveyId, actor)).thenReturn(survey);
        OfflineSyncService service = new OfflineSyncService(
                operations, mock(AuditEventRepository.class), tickets, comments, customers,
                notifications, surveys, new ObjectMapper()
        );
        OfflineSyncService.SyncCommand command = new OfflineSyncService.SyncCommand(
                operationId, "SURVEY_START", surveyId, "{}", Instant.now());
        var first = service.synchronize(List.of(command), actor).get(0);
        var replay = service.synchronize(List.of(command), actor).get(0);
        assertEquals("APPLIED", first.status());
        assertEquals(first.resultJson(), replay.resultJson());
        verify(surveys, times(1)).start(surveyId, actor);
        verify(operations, times(1)).save(any());
    }
    
    @Test
    void rejectsOfflineCommandsFromPlatformAccounts() {
        OfflineSyncService service = new OfflineSyncService(
                mock(OfflineSyncOperationRepository.class), mock(AuditEventRepository.class),
                mock(ServiceRequestRepository.class), mock(ServiceCommentRepository.class),
                mock(CustomerProfileRepository.class), mock(NotificationService.class),
                mock(SurveyLifecycleService.class), new ObjectMapper()
        );
        Principal platform = new Principal(UUID.randomUUID(), null, "platform@example.test", "PLATFORM", Set.of(), Set.of(), 0);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.synchronize(
                        List.of(new OfflineSyncService.SyncCommand(
                                UUID.randomUUID(), "SURVEY_START", UUID.randomUUID(), "{}", Instant.now())), platform
                )
        );
    }
    
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
