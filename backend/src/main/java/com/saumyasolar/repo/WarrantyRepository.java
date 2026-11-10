package com.saumyasolar.repo;

import com.saumyasolar.domain.Warranty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
public interface WarrantyRepository extends JpaRepository<Warranty, UUID> {
    java.util.Optional<Warranty> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Warranty> findByTenantIdAndStatusAndEndDateBefore(UUID tenantId, String status, LocalDate date);
    
    List<Warranty> findByTenantIdAndStatusAndEndDate(UUID tenantId, String status, LocalDate date);
    
    List<Warranty> findByTenantIdOrderByStartDateDesc(UUID tenantId);
    
    List<Warranty> findByTenantIdAndCustomerIdOrderByStartDateDesc(UUID tenantId, UUID customerId);
}
