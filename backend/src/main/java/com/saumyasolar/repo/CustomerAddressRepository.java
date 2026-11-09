package com.saumyasolar.repo;

import com.saumyasolar.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    @Query("select a from CustomerAddress a where a.tenantId=:tenantId and a.customerId=:customerId order by a.isDefault desc,a.createdAt desc")
    List<CustomerAddress> findOwnedAddresses(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);
    
    Optional<CustomerAddress> findByIdAndTenantIdAndCustomerId(UUID id, UUID tenantId, UUID customerId);
    
    long countByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
