package com.saumyasolar.service;

import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.TenantExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Tenant-scoped reservation expiry and inventory reconciliation. */
@Component
public class ReservationMaintenanceScheduler {
    private static final Logger log=LoggerFactory.getLogger(ReservationMaintenanceScheduler.class);
    private final TenantRepository tenants;
    private final OperationsService operations;
    public ReservationMaintenanceScheduler(TenantRepository tenants,OperationsService operations){this.tenants=tenants;this.operations=operations;}

    @Scheduled(cron="${app.inventory.reservation-expiry-cron:0 */5 * * * *}")
    public void expireStaleReservations(){
        for(Tenant tenant:tenants.findAll()) try(var scope=TenantExecutionContext.open(tenant.getId())){
            int released=operations.expireReservationsForTenant(tenant.getId(),Instant.now());
            if(released>0)log.info("Expired {} inventory reservations for tenant {}",released,tenant.getId());
        }catch(RuntimeException ex){log.error("Reservation expiry failed for tenant {}",tenant.getId(),ex);}
    }

    @Scheduled(cron="${app.inventory.reservation-reconciliation-cron:0 30 * * * *}")
    public void reconcileReservedBalances(){
        for(Tenant tenant:tenants.findAll()) try(var scope=TenantExecutionContext.open(tenant.getId())){
            int corrected=operations.reconcileReservationsForTenant(tenant.getId());
            if(corrected>0)log.warn("Reconciled {} inventory reserved balances for tenant {}",corrected,tenant.getId());
        }catch(RuntimeException ex){log.error("Inventory reservation reconciliation failed for tenant {}",tenant.getId(),ex);}
    }
}