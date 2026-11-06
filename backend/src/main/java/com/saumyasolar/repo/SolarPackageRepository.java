package com.saumyasolar.repo;

import com.saumyasolar.domain.SolarPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface SolarPackageRepository extends JpaRepository<SolarPackage, UUID> {
    List<SolarPackage> findByTenantIdOrderByIdDesc(UUID tenantId);
    
    Optional<SolarPackage> findByIdAndTenantId(UUID id, UUID tenantId);
}
