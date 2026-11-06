package com.saumyasolar.repo;
import com.saumyasolar.domain.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
 List<CustomerProfile> findByTenantIdOrderByIdDesc(UUID tenantId);
 Optional<CustomerProfile> findByIdAndTenantId(UUID id, UUID tenantId);
 Optional<CustomerProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}