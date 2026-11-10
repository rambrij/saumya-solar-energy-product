package com.saumyasolar.repo;

import com.saumyasolar.domain.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.*;
import java.util.UUID;
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    List<ServiceRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ServiceRequest request where request.tenantId = :tenantId order by request.createdAt desc")
    List<ServiceRequest> findForSlaEvaluationByTenantId(@Param("tenantId") UUID tenantId);
    
    Optional<ServiceRequest> findByIdAndTenantId(UUID id, UUID tenantId);
    
    long countByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    
    Optional<ServiceRequest> findByTicketNumberAndTenantId(String ticketNumber, UUID tenantId);
}
