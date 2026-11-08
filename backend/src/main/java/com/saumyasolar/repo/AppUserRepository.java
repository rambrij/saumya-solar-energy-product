package com.saumyasolar.repo;

import com.saumyasolar.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    
    List<AppUser> findByTenantId(UUID tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id=:id and u.tenantId=:tenantId")
    Optional<AppUser> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
