package com.saumyasolar.repo;

import com.saumyasolar.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.*;
import java.util.UUID;
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    List<Refund> findByTenantIdAndPaymentId(UUID tenantId, UUID paymentId);

    Optional<Refund> findByTenantIdAndProviderAndProviderRefundReference(UUID tenantId, String provider, String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.tenantId=:tenantId and r.provider=:provider and r.providerRefundReference=:reference")
    Optional<Refund> findForUpdateByTenantAndProviderReference(@Param("tenantId") UUID tenantId,
            @Param("provider") String provider, @Param("reference") String reference);

    Optional<Refund> findByTenantIdAndPaymentIdAndIdempotencyKey(UUID tenantId, UUID paymentId, String idempotencyKey);

    Optional<Refund> findByIdAndTenantId(UUID id, UUID tenantId);
}
