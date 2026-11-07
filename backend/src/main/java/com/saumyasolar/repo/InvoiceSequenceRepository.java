package com.saumyasolar.repo;
import com.saumyasolar.domain.InvoiceSequence;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;import java.util.UUID;
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence,UUID>{
 @Modifying @Query(value="INSERT INTO invoice_sequence(id,tenant_id,fiscal_year,next_number) VALUES(UUID_TO_BIN(UUID()),:tenantId,:year,1) ON DUPLICATE KEY UPDATE next_number=next_number+1",nativeQuery=true) int next(@Param("tenantId") UUID tenantId,@Param("year") String year);
 Optional<InvoiceSequence> findByTenantIdAndFiscalYear(UUID tenantId,String fiscalYear);
}
