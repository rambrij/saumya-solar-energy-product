package com.saumyasolar.repo;

import com.saumyasolar.domain.InvoiceSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSequence s where s.tenantId = :tenantId and s.fiscalYear = :year")
    Optional<InvoiceSequence> findByTenantIdAndFiscalYearForUpdate(
            @Param("tenantId") UUID tenantId, @Param("year") String year);

    Optional<InvoiceSequence> findByTenantIdAndFiscalYear(UUID tenantId, String fiscalYear);
}
