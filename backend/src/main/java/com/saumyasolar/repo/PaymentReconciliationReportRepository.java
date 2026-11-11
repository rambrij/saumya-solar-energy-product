package com.saumyasolar.repo;

import com.saumyasolar.domain.PaymentReconciliationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentReconciliationReportRepository extends JpaRepository<PaymentReconciliationReport, UUID> {
    Optional<PaymentReconciliationReport> findByTenantIdAndProviderAndReportReference(UUID tenantId, String provider, String reportReference);
    List<PaymentReconciliationReport> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
