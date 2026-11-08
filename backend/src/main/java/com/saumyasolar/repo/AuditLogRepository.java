package com.saumyasolar.repo;

import com.saumyasolar.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    java.util.List<AuditLog> findTop20ByActorIdOrderByCreatedAtDesc(UUID actorId);
    java.util.List<AuditLog> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    java.util.List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
