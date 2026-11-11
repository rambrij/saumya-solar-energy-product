package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.CmsContent;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.CmsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CmsServiceTest {
    @Test
    void hidesContentOwnedByAnotherTenant() {
        CmsContentRepository contents = mock(CmsContentRepository.class);
        CmsService service = new CmsService(contents, mock(CmsContentRevisionRepository.class),
                mock(TenantRepository.class), mock(AuditEventRepository.class), new ObjectMapper());
        Principal actor = new Principal(UUID.randomUUID(), UUID.randomUUID(), "writer@example.test",
                "VENDOR", Set.of("CONTENT_MANAGER"), Set.of("CMS_VIEW"), 0);
        UUID foreignContent = UUID.randomUUID();
        when(contents.findByIdAndTenantId(foreignContent, actor.tenantId())).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.get(foreignContent, actor));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void deniesCmsReadWithoutPermissionBeforeRepositoryAccess() {
        CmsContentRepository contents = mock(CmsContentRepository.class);
        CmsService service = new CmsService(contents, mock(CmsContentRevisionRepository.class),
                mock(TenantRepository.class), mock(AuditEventRepository.class), new ObjectMapper());
        Principal actor = new Principal(UUID.randomUUID(), UUID.randomUUID(), "sales@example.test",
                "VENDOR", Set.of("SALES_EXECUTIVE"), Set.of("LEAD_VIEW"), 0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.list(actor, null, 0, 20));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(contents);
    }

    @Test
    void rejectsPublishingWithoutEnteringReview() {
        CmsContentRepository contents = mock(CmsContentRepository.class);
        CmsContent draft = new CmsContent();
        draft.setId(UUID.randomUUID());
        draft.setTenantId(UUID.randomUUID());
        draft.setStatus("DRAFT");
        Principal actor = new Principal(UUID.randomUUID(), draft.getTenantId(), "writer@example.test",
                "VENDOR", Set.of("CONTENT_MANAGER"), Set.of("CMS_UPDATE", "CMS_PUBLISH"), 0);
        when(contents.findByIdAndTenantId(draft.getId(), draft.getTenantId())).thenReturn(Optional.of(draft));
        CmsService service = new CmsService(contents, mock(CmsContentRevisionRepository.class),
                mock(TenantRepository.class), mock(AuditEventRepository.class), new ObjectMapper());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.transition(draft.getId(), "PUBLISHED", actor));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(contents, never()).save(any());
    }
}
