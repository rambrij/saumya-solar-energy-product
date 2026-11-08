package com.saumyasolar.repo;
import com.saumyasolar.domain.IntegrationConnection;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;
public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection,UUID>{
 List<IntegrationConnection> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
 Optional<IntegrationConnection> findByIdAndTenantId(UUID id,UUID tenantId);
}
