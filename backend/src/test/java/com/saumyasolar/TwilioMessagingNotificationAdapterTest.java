package com.saumyasolar;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.service.TwilioMessagingNotificationAdapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwilioMessagingNotificationAdapterTest {
    @Test void providerSeamSupportsSmsAndWhatsappButNeverMakesLiveCallsInMockOnlyPhase() {
        var adapter = new TwilioMessagingNotificationAdapter();
        var delivery = new NotificationDelivery();
        delivery.setChannel("SMS");
        assertTrue(adapter.supports("SMS"));
        assertTrue(adapter.supports("WHATSAPP"));
        assertEquals("TWILIO", adapter.providerCode());
        assertThrows(IllegalStateException.class, () -> adapter.dispatch(delivery));
    }
}
