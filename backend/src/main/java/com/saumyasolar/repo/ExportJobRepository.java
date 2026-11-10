package com.saumyasolar.repo;

import com.saumyasolar.domain.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
    Optional<ExportJob> findByIdAndTenantIdAndRequestedBy(UUID id, UUID tenantId, UUID requestedBy);
    Optional<ExportJob> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select distinct j.tenantId from ExportJob j where j.status = 'PENDING' or (j.status = 'PROCESSING' and j.updatedAt <= :staleBefore)")
    List<UUID> findDistinctTenantIdsForRecovery(@Param("staleBefore") Instant staleBefore);

    @Query("select j from ExportJob j where j.tenantId = :tenantId and (j.status = 'PENDING' or (j.status = 'PROCESSING' and j.updatedAt <= :staleBefore)) order by j.createdAt asc")
    List<ExportJob> findRecoverableByTenant(@Param("tenantId") UUID tenantId,
            @Param("staleBefore") Instant staleBefore, org.springframework.data.domain.Pageable pageable);

    @Modifying @Transactional
    @Query("update ExportJob j set j.status = 'PROCESSING', j.updatedAt = :now where j.id = :id and j.tenantId = :tenantId and (j.status = 'PENDING' or (j.status = 'PROCESSING' and j.updatedAt <= :staleBefore))")
    int claim(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("now") Instant now,
              @Param("staleBefore") Instant staleBefore);

    List<ExportJob> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(String status, Instant before);
}
