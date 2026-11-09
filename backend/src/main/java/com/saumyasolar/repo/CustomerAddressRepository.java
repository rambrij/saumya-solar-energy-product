package com.saumyasolar.repo;

import com.saumyasolar.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress,UUID> {
 List<CustomerAddress> findByTenantIdAndCustomerIdOrderByIsDefaultDescCreatedAtDesc(UUID tenantId,UUID customerId);
 Optional<CustomerAddress> findByIdAndTenantIdAndCustomerId(UUID id,UUID tenantId,UUID customerId);
 long countByTenantIdAndCustomerId(UUID tenantId,UUID customerId);
}
