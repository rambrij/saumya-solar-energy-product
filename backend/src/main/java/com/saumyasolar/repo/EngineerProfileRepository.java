package com.saumyasolar.repo;
import com.saumyasolar.domain.EngineerProfile;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface EngineerProfileRepository extends JpaRepository<EngineerProfile,UUID>{
 Optional<EngineerProfile> findByTenantIdAndUserId(UUID tenantId,UUID userId);
 Optional<EngineerProfile> findByIdAndTenantId(UUID id,UUID tenantId);
 List<EngineerProfile> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(UUID tenantId);
}