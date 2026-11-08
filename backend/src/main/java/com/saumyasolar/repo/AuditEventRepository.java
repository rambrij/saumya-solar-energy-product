package com.saumyasolar.repo;
import com.saumyasolar.domain.AuditEvent;import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;import java.util.*;import java.util.UUID;
public interface AuditEventRepository extends JpaRepository<AuditEvent,UUID> {
 List<AuditEvent> findTop200ByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID tenantId,Instant from,Instant to);
 List<AuditEvent> findTop200ByCreatedAtBetweenOrderByCreatedAtDesc(Instant from,Instant to);
}
