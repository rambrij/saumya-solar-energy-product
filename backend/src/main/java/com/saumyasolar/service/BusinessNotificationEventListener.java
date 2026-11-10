package com.saumyasolar.service;

import com.saumyasolar.security.TenantExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Processes business notification events away from the request thread. */
@Component
public class BusinessNotificationEventListener implements ApplicationListener<BusinessNotificationRequestedEvent> {
    private static final Logger log = LoggerFactory.getLogger(BusinessNotificationEventListener.class);
    private final NotificationService notifications;

    public BusinessNotificationEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    @Async("applicationEventExecutor")
    public void onApplicationEvent(BusinessNotificationRequestedEvent event) {
        try (TenantExecutionContext.Scope ignored = TenantExecutionContext.open(event.tenantId())) {
            notifications.deliverBusinessNotification(
                    event.userId(), event.tenantId(), event.eventType(),
                    event.recipient(), event.subject(), event.body()
            );
        } catch (RuntimeException ex) {
            log.error("Business notification processing failed for tenant {} and event {}",
                    event.tenantId(), event.eventType(), ex);
        }
    }
}
