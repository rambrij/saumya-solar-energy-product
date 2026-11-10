package com.saumyasolar.service;

import com.saumyasolar.domain.AuditEvent;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.security.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationAuditService {
    private final AuditEventRepository events;
    private final ClientAddressResolver addresses;

    public AuthenticationAuditService(AuditEventRepository events, ClientAddressResolver addresses) {
        this.events = events;
        this.addresses = addresses;
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public void record(String action, UUID tenantId, UUID actorId, String outcome,
                       String safeReason, String entityId, HttpServletRequest request) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setActorId(actorId);
        event.setActorType("AUTHENTICATION");
        event.setAction(action);
        event.setEntityType("AUTHENTICATION");
        event.setEntityId(entityId);
        event.setOutcome(outcome);
        event.setReason(safeReason);
        event.setSourceIp(request == null ? null : addresses.resolve(request));
        event.setUserAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 512));
        event.setRequestId(request == null ? null : truncate(request.getHeader("X-Request-ID"), 100));
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
