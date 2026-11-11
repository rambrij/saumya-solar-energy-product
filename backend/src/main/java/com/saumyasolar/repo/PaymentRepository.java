package com.saumyasolar.repo;

import com.saumyasolar.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.*;
import java.util.UUID;
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    List<Payment> findByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);
    
    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);
    
    Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id=:id and p.tenantId=:tenantId")
    Optional<Payment> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
    
    Optional<Payment> findByProviderAndProviderReference(String provider, String reference);

    Optional<Payment> findByTenantIdAndProviderAndProviderReference(UUID tenantId, String provider, String reference);
}
