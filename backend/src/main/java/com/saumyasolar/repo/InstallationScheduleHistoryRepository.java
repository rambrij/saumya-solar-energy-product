package com.saumyasolar.repo;

import com.saumyasolar.domain.InstallationScheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
public interface InstallationScheduleHistoryRepository extends JpaRepository<InstallationScheduleHistory, UUID> {
    List<InstallationScheduleHistory> findByTenantIdAndInstallationIdOrderByCreatedAtAsc(
            UUID tenantId,
            UUID installationId
    );
}
