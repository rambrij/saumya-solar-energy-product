package com.saumyasolar.repo;

import com.saumyasolar.domain.LeadHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface LeadHistoryRepository extends JpaRepository<LeadHistory, UUID> {
    List<LeadHistory> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    List<LeadHistory> findByLeadIdAndTenantIdOrderByCreatedAtDesc(UUID leadId, UUID tenantId);
    
    Optional<LeadHistory> findByIdAndTenantId(UUID id, UUID tenantId);
}
