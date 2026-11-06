package com.saumyasolar.repo;

import com.saumyasolar.domain.SalesLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface SalesLeadRepository extends JpaRepository<SalesLead, UUID> {
    List<SalesLead> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<SalesLead> findByIdAndTenantId(UUID id, UUID tenantId);
}
