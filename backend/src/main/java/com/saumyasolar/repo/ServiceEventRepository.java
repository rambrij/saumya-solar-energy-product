package com.saumyasolar.repo;
import com.saumyasolar.domain.ServiceEvent;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface ServiceEventRepository extends JpaRepository<ServiceEvent,UUID>{List<ServiceEvent> findByTenantIdAndServiceRequestIdOrderByOccurredAtAsc(UUID tenantId,UUID requestId);}
