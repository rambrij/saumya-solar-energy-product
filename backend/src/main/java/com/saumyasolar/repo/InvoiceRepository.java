package com.saumyasolar.repo;
import com.saumyasolar.domain.Invoice;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface InvoiceRepository extends JpaRepository<Invoice,UUID>{List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);Optional<Invoice> findByIdAndTenantId(UUID id,UUID tenantId);Optional<Invoice> findByOrderIdAndTenantId(UUID orderId,UUID tenantId);long countByTenantIdAndFiscalYear(UUID tenantId,String fiscalYear);}
