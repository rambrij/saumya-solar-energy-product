package com.saumyasolar.repo;
import com.saumyasolar.domain.TenantStorageLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
public interface TenantStorageLedgerRepository extends JpaRepository<TenantStorageLedgerEntry,UUID> {
 boolean existsByTenantIdAndOperationId(UUID tenantId,String operationId);
 @Query("select coalesce(sum(e.byteDelta),0) from TenantStorageLedgerEntry e where e.tenantId=:tenantId")
 long sumByteDeltaByTenantId(@Param("tenantId") UUID tenantId);
}
