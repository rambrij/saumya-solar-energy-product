package com.saumyasolar.repo;

import com.saumyasolar.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameAndTenantId(String name, UUID tenantId);

    Optional<Role> findByIdAndTenantId(UUID id, UUID tenantId);
    
    Optional<Role> findByNameAndTenantIdIsNull(String name);
    
    List<Role> findByTenantIdOrderByNameAsc(UUID tenantId);
}
