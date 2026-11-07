package com.saumyasolar.repo;
import com.saumyasolar.domain.Payment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface PaymentRepository extends JpaRepository<Payment,UUID>{List<Payment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);List<Payment> findByTenantIdAndInvoiceId(UUID tenantId,UUID invoiceId);Optional<Payment> findByIdAndTenantId(UUID id,UUID tenantId);Optional<Payment> findByProviderAndProviderReference(String provider,String reference);}
