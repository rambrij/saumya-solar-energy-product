package com.saumyasolar.repo;
import com.saumyasolar.domain.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;
import java.time.Instant; import java.util.List; import java.util.Optional; import java.util.UUID;
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
List<InventoryReservation> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
List<InventoryReservation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
List<InventoryReservation> findByTenantIdAndStatus(UUID tenantId,String status);
List<InventoryReservation> findTop500ByTenantIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(UUID tenantId,String status,Instant now);
boolean existsByTenantIdAndOrderIdAndStatus(UUID tenantId,UUID orderId,String status);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from InventoryReservation r where r.id=:id and r.tenantId=:tenantId")
Optional<InventoryReservation> findByIdAndTenantIdForUpdate(@Param("id") UUID id,@Param("tenantId") UUID tenantId);
}