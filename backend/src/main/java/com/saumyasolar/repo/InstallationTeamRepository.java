package com.saumyasolar.repo;

import com.saumyasolar.domain.InstallationTeam;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface InstallationTeamRepository extends JpaRepository<InstallationTeam, UUID> {
    List<InstallationTeam> findByTenantIdOrderByNameAsc(UUID tenantId);
    
    Optional<InstallationTeam> findByIdAndTenantId(UUID id, UUID tenantId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InstallationTeam t where t.id=:id and t.tenantId=:tenantId and t.active=true")
    Optional<InstallationTeam> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
