package com.saumyasolar.service;

import com.saumyasolar.domain.Warranty;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.WarrantyRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.Set;

/** Daily tenant-scoped warranty expiry transitions and opt-in customer alerts. */
@Component
public class WarrantyExpiryScheduler {
    private final WarrantyRepository warranties;
    private final CustomerProfileRepository customers;
    private final NotificationService notifications;
    private final java.time.ZoneId zone;
    
    public WarrantyExpiryScheduler(
            WarrantyRepository warranties,
            CustomerProfileRepository customers,
            NotificationService notifications,
            @Value("${app.warranty.expiry-alert-zone:Asia/Kolkata}") String zone
    ) {
        this.warranties = warranties;
        this.customers = customers;
        this.notifications = notifications;
        this.zone = java.time.ZoneId.of(zone);
    }
    
    @Scheduled(
            cron = "${app.warranty.expiry-alert-cron:0 15 8 * * *}",
            zone = "${app.warranty.expiry-alert-zone:Asia/Kolkata}"
    )
    public void evaluateWarrantyExpiry() {
        LocalDate today = LocalDate.now(zone);
        for (Warranty expired : warranties.findByStatusAndEndDateBefore("ACTIVE", today)) {
            expired.setStatus("EXPIRED");
            warranties.save(expired);
        }
        for (long days : Set.of(30L, 7L, 1L)) {
            for (Warranty warranty : warranties.findByStatusAndEndDate("ACTIVE", today.plusDays(days))) {
                customers.findByIdAndTenantId(warranty.getCustomerId(), warranty.getTenantId()).filter(c -> c.isActive()).ifPresent(customer -> {
                    String event = "WARRANTY_EXPIRY_" + days + "_DAYS";
                    String body = "Your product warranty expires in " + days + " day(s), on " + warranty.getEndDate() + ". Warranty reference: " + warranty.getId() + ". Contact your vendor for assistance.";
                    notifications.sendBusinessNotification(customer.getUserId(), warranty.getTenantId(), event, customer.getEmail(), "Warranty expiry reminder", body);
                });
            }
        }
    }
}
