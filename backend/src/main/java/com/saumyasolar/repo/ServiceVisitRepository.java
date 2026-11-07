package com.saumyasolar.repo;
import com.saumyasolar.domain.ServiceVisit;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface ServiceVisitRepository extends JpaRepository<ServiceVisit,UUID>{List<ServiceVisit> findByTenantIdAndServiceRequestIdOrderByScheduledAtAsc(UUID tenantId,UUID requestId);}
