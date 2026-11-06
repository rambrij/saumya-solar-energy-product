package com.saumyasolar.repo;

import com.saumyasolar.domain.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface QuotationItemRepository extends JpaRepository<QuotationItem, UUID> {
    List<QuotationItem> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<QuotationItem> findByIdAndTenantId(UUID id, UUID tenantId);
}
