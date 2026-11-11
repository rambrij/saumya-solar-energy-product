package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class NotificationChannelAdapterRegistry {
    private final List<NotificationChannelAdapter> adapters;
    private final String messagingProvider;

    public NotificationChannelAdapterRegistry(List<NotificationChannelAdapter> adapters,
            @Value("${app.messaging.provider:mock}") String messagingProvider) {
        this.adapters = List.copyOf(adapters);
        this.messagingProvider = messagingProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("mock", "twilio").contains(this.messagingProvider)) {
            throw new IllegalStateException("app.messaging.provider must be mock or twilio");
        }
    }

    private NotificationChannelAdapter adapter(String channel) {
        String expected = "EMAIL".equals(channel) ? "MAILPIT"
                : "mock".equals(messagingProvider) ? "MOCK-MESSAGING" : "TWILIO";
        return adapters.stream().filter(a -> a.supports(channel) && expected.equals(a.providerCode()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "No configured adapter for channel " + channel + " and provider " + expected));
    }

    public String providerCode(String channel) { return adapter(channel).providerCode(); }

    public void dispatch(NotificationDelivery delivery) throws Exception {
        adapter(delivery.getChannel()).dispatch(delivery);
    }
}
