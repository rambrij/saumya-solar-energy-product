package com.saumyasolar.repo;

import com.saumyasolar.domain.Quotation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    List<Quotation> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<Quotation> findByIdAndTenantId(UUID id, UUID tenantId);
 long countByTenantIdAndBranchId(UUID tenantId,UUID branchId);
 long countByTenantIdAndBranchIdAndStatus(UUID tenantId,UUID branchId,String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.id = :id and q.tenantId = :tenantId")
    Optional<Quotation> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<Quotation> findByQuoteGroupIdAndTenantIdOrderByRevisionAsc(UUID quoteGroupId, UUID tenantId);
}
