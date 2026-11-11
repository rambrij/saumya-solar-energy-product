package com.saumyasolar.repo;

import com.saumyasolar.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.util.UUID;
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    List<Refund> findByTenantIdAndPaymentId(UUID tenantId, UUID paymentId);

    Optional<Refund> findByTenantIdAndProviderAndProviderRefundReference(UUID tenantId, String provider, String reference);

    Optional<Refund> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
