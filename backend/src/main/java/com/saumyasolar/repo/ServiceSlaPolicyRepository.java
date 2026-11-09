package com.saumyasolar.repo;

import com.saumyasolar.domain.ServiceSlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.util.UUID;
public interface ServiceSlaPolicyRepository extends JpaRepository<ServiceSlaPolicy, UUID> {
    List<ServiceSlaPolicy> findByTenantIdAndActiveTrue(UUID tenantId);
    
    Optional<ServiceSlaPolicy> findByTenantIdAndPriority(UUID tenantId, String priority);
}
