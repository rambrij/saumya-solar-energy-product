package com.saumyasolar.repo;

import com.saumyasolar.domain.VendorInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorInvitationRepository extends JpaRepository<VendorInvitation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from VendorInvitation i where i.tokenHash = :tokenHash")
    Optional<VendorInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<VendorInvitation> findByTenantIdAndUserIdAndAcceptedAtIsNullAndRevokedAtIsNull(UUID tenantId, UUID userId);
    List<VendorInvitation> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);
}
