package com.saumyasolar.repo;
import com.saumyasolar.domain.TenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface TenantDomainRepository extends JpaRepository<TenantDomain,UUID> {
 List<TenantDomain> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
 Optional<TenantDomain> findByIdAndTenantId(UUID id,UUID tenantId);
 Optional<TenantDomain> findFirstByDomainIgnoreCaseAndStatus(String domain,String status);
 boolean existsByDomainIgnoreCase(String domain);
}
