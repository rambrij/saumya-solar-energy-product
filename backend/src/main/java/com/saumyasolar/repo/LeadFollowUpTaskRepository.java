package com.saumyasolar.repo;

import com.saumyasolar.domain.LeadFollowUpTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadFollowUpTaskRepository extends JpaRepository<LeadFollowUpTask, UUID> {
    List<LeadFollowUpTask> findByTenantIdAndLeadIdOrderByDueAtAsc(UUID tenantId, UUID leadId);
    Optional<LeadFollowUpTask> findByIdAndTenantId(UUID id, UUID tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LeadFollowUpTask t where t.status = :status and t.dueAt <= :now and t.reminderSentAt is null order by t.dueAt asc")
    List<LeadFollowUpTask> findDueFollowUpsForUpdate(@Param("status") String status, @Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}
