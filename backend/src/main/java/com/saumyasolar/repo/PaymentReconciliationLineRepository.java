package com.saumyasolar.repo;

import com.saumyasolar.domain.PaymentReconciliationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentReconciliationLineRepository extends JpaRepository<PaymentReconciliationLine, UUID> {
    List<PaymentReconciliationLine> findByTenantIdAndReportIdOrderByProviderReference(UUID tenantId, UUID reportId);
}
