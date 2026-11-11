package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.stereotype.Component;

/**
 * Deliberately network-free provider seam. The Twilio SDK/client implementation is not
 * included in the mock-only phase. Selecting it fails closed instead of silently sending.
 */
@Component
public class TwilioMessagingNotificationAdapter implements NotificationChannelAdapter {
    @Override public boolean supports(String channel) {
        return "SMS".equals(channel) || "WHATSAPP".equals(channel);
    }
    @Override public String providerCode() { return "TWILIO"; }
    @Override public void dispatch(NotificationDelivery delivery) {
        throw new IllegalStateException("Twilio adapter is a reserved integration seam; live provider calls are disabled");
    }
}
