package com.saumyasolar;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationChannelAdapterRegistryTest {
    @Test void mockPropertySelectsOnlyMockMessagingAdapter() throws Exception {
        var mailpit=mock(NotificationChannelAdapter.class);var mockMessaging=mock(NotificationChannelAdapter.class);var twilio=mock(NotificationChannelAdapter.class);
        when(mailpit.supports("EMAIL")).thenReturn(true);when(mailpit.providerCode()).thenReturn("MAILPIT");
        when(mockMessaging.supports("SMS")).thenReturn(true);when(mockMessaging.providerCode()).thenReturn("MOCK-MESSAGING");
        when(twilio.supports("SMS")).thenReturn(true);when(twilio.providerCode()).thenReturn("TWILIO");
        var registry=new NotificationChannelAdapterRegistry(List.of(mailpit,mockMessaging,twilio),"mock");
        var delivery=new NotificationDelivery();delivery.setChannel("SMS");
        registry.dispatch(delivery);
        verify(mockMessaging).dispatch(delivery);verifyNoInteractions(twilio,mailpit);
    }

    @Test void twilioPropertySelectsReservedAdapterButCannotMakeNetworkCalls() {
        var mockMessaging=mock(NotificationChannelAdapter.class);var twilio= new TwilioMessagingNotificationAdapter();
        when(mockMessaging.supports("SMS")).thenReturn(true);when(mockMessaging.providerCode()).thenReturn("MOCK-MESSAGING");
        var registry=new NotificationChannelAdapterRegistry(List.of(mockMessaging,twilio),"twilio");
        var delivery=new NotificationDelivery();delivery.setChannel("SMS");
        assertThrows(IllegalStateException.class,()->registry.dispatch(delivery));
        verifyNoInteractions(mockMessaging);
    }

    @Test void rejectsUnknownProviderConfiguration() {
        assertThrows(IllegalStateException.class,()->new NotificationChannelAdapterRegistry(List.of(),"send-real"));
    }
}
