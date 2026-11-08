package com.saumyasolar.repo;

import com.saumyasolar.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    List<CustomerOrder> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<CustomerOrder> findByIdAndTenantId(UUID id, UUID tenantId);
 long countByTenantIdAndBranchId(UUID tenantId,UUID branchId);
 @Query("select coalesce(sum(o.total),0) from CustomerOrder o where o.tenantId=:tenantId and o.branchId=:branchId")
 java.math.BigDecimal sumTotalByTenantAndBranch(@Param("tenantId") UUID tenantId,@Param("branchId") UUID branchId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id=:id and o.tenantId=:tenantId")
    Optional<CustomerOrder> findForUpdate(@Param("id") UUID id,@Param("tenantId") UUID tenantId);
}
