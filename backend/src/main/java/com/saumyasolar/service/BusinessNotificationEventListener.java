package com.saumyasolar.service;

import com.saumyasolar.security.TenantExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Processes persisted business notification deliveries away from the request thread. */
@Component
public class BusinessNotificationEventListener implements ApplicationListener<BusinessNotificationRequestedEvent> {
    private static final Logger log = LoggerFactory.getLogger(BusinessNotificationEventListener.class);
    private final NotificationService notifications;
    public BusinessNotificationEventListener(NotificationService notifications) { this.notifications = notifications; }
    @Override
    @Async("applicationEventExecutor")
    public void onApplicationEvent(BusinessNotificationRequestedEvent event) {
        try (TenantExecutionContext.Scope ignored = TenantExecutionContext.open(event.tenantId())) {
            notifications.processPendingDelivery(event.deliveryId(), event.tenantId());
        } catch (RuntimeException ex) {
            log.error("Persisted notification processing failed for tenant {} and delivery {}", event.tenantId(), event.deliveryId(), ex);
        }
    }
}
