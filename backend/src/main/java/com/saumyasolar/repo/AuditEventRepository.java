package com.saumyasolar.repo;

import com.saumyasolar.domain.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;
import java.util.UUID;
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop200ByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID tenantId,
            Instant from,
            Instant to
    );
    
    List<AuditEvent> findTop200ByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
    
    @Query(
            """
                     select a from AuditEvent a
                     where a.createdAt between :from and :to
                       and (:tenantId is null or a.tenantId=:tenantId)
                       and (:actorId is null or a.actorId=:actorId)
                       and (:action is null or lower(a.action)=lower(:action))
                       and (:entityType is null or lower(a.entityType)=lower(:entityType))
                       and (:outcome is null or lower(a.outcome)=lower(:outcome))
                     order by a.createdAt desc
                    """
    )
    List<AuditEvent> search(
            @Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to,
            @Param("actorId") UUID actorId, @Param("action") String action, @Param("entityType") String entityType,
            @Param("outcome") String outcome, Pageable page
    );
}
