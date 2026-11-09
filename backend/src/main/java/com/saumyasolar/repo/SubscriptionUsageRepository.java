package com.saumyasolar.repo;

import com.saumyasolar.domain.SubscriptionUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, UUID> {
    Optional<SubscriptionUsage> findByTenantIdAndMeterCodeAndPeriodStartAndPeriodEnd(
            UUID tenantId,
            String meterCode,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
