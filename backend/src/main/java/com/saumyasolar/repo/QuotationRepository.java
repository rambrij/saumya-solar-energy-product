package com.saumyasolar.repo;

import com.saumyasolar.domain.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    List<Quotation> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<Quotation> findByIdAndTenantId(UUID id, UUID tenantId);
}
