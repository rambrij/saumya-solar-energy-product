package com.saumyasolar.repo;
import com.saumyasolar.domain.ServiceRequest;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest,UUID>{List<ServiceRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);Optional<ServiceRequest> findByIdAndTenantId(UUID id,UUID tenantId);
 long countByTenantIdAndBranchId(UUID tenantId,UUID branchId);Optional<ServiceRequest> findByTicketNumberAndTenantId(String ticketNumber,UUID tenantId);}
