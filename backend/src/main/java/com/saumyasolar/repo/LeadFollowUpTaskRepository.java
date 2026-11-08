package com.saumyasolar.repo;

import com.saumyasolar.domain.LeadFollowUpTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadFollowUpTaskRepository extends JpaRepository<LeadFollowUpTask, UUID> {
    List<LeadFollowUpTask> findByTenantIdAndLeadIdOrderByDueAtAsc(UUID tenantId, UUID leadId);
    Optional<LeadFollowUpTask> findByIdAndTenantId(UUID id, UUID tenantId);
    List<LeadFollowUpTask> findTop50ByStatusAndDueAtLessThanEqualAndReminderSentAtIsNullOrderByDueAtAsc(String status, Instant now);
}
