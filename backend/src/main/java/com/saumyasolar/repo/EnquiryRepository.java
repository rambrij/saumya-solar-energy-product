package com.saumyasolar.repo;
import com.saumyasolar.domain.Enquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EnquiryRepository extends JpaRepository<Enquiry, UUID> {
 List<Enquiry> findByTenantIdOrderByIdDesc(UUID tenantId);
 List<Enquiry> findByTenantIdAndCustomerIdOrderByIdDesc(UUID tenantId, UUID customerId);
 Optional<Enquiry> findByIdAndTenantId(UUID id, UUID tenantId);
}