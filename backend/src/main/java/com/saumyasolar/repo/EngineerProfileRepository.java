package com.saumyasolar.repo;

import com.saumyasolar.domain.EngineerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;
public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, UUID> {
    Optional<EngineerProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EngineerProfile e where e.tenantId=:tenantId and e.userId=:userId")
    Optional<EngineerProfile> findForUpdate(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
    
    Optional<EngineerProfile> findByIdAndTenantId(UUID id, UUID tenantId);
    
    List<EngineerProfile> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(UUID tenantId);
}
