package com.saumyasolar.service;

import com.saumyasolar.domain.AuditEvent;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.security.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationAuditServiceTest {
    @Test
    void recordJoinsTheCallingTransactionToAvoidSelfBlockingOnActorForeignKey() throws Exception {
        var method = AuthenticationAuditService.class.getMethod(
                "record", String.class, UUID.class, UUID.class, String.class,
                String.class, String.class, HttpServletRequest.class);

        var transaction = method.getAnnotation(jakarta.transaction.Transactional.class);

        assertNotNull(transaction, "Authentication audit must participate in the caller transaction");
        assertEquals(jakarta.transaction.Transactional.TxType.REQUIRED, transaction.value());
    }

    @Test
    void persistsSafeAuthenticationEventWithResolvedRequestMetadata() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ClientAddressResolver resolver = mock(ClientAddressResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(resolver.resolve(request)).thenReturn("203.0.113.20");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");
        when(request.getHeader("X-Request-ID")).thenReturn("request-123");
        AuthenticationAuditService service = new AuthenticationAuditService(repository, resolver);
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        service.record("LOGIN_FAILED", tenantId, actorId, "DENIED", "INVALID_CREDENTIALS", null, request);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(tenantId, event.getTenantId());
        assertEquals(actorId, event.getActorId());
        assertEquals("AUTHENTICATION", event.getActorType());
        assertEquals("LOGIN_FAILED", event.getAction());
        assertEquals("DENIED", event.getOutcome());
        assertEquals("INVALID_CREDENTIALS", event.getReason());
        assertEquals("203.0.113.20", event.getSourceIp());
        assertEquals("test-agent", event.getUserAgent());
        assertEquals("request-123", event.getRequestId());
        assertNotNull(event.getCreatedAt());
        assertNull(event.getBeforeJson());
        assertNull(event.getAfterJson());
    }
}
