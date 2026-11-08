package com.saumyasolar.repo;
import com.saumyasolar.domain.TenantStorageLedgerEntry;import org.springframework.data.jpa.repository.JpaRepository;import java.util.UUID;
public interface TenantStorageLedgerRepository extends JpaRepository<TenantStorageLedgerEntry,UUID> {
 boolean existsByTenantIdAndOperationId(UUID tenantId,String operationId);
 long sumByteDeltaByTenantId(UUID tenantId);
}