package com.saumyasolar;
import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.service.MockMessagingNotificationAdapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MockMessagingNotificationAdapterTest {
 @Test void acceptsSmsAndWhatsappWithoutExternalProvider(){var adapter=new MockMessagingNotificationAdapter(true);var d=new NotificationDelivery();d.setChannel("SMS");d.setMessageBody("Order update");assertDoesNotThrow(()->adapter.dispatch(d));d.setChannel("WHATSAPP");assertDoesNotThrow(()->adapter.dispatch(d));}
 @Test void deterministicFailureCanExerciseRetry(){var adapter=new MockMessagingNotificationAdapter(true);var d=new NotificationDelivery();d.setChannel("SMS");d.setMessageBody("[[MOCK_FAIL]]");assertThrows(IllegalStateException.class,()->adapter.dispatch(d));}
 @Test void disabledMockCannotSilentlySend(){var adapter=new MockMessagingNotificationAdapter(false);var d=new NotificationDelivery();d.setChannel("SMS");assertThrows(IllegalStateException.class,()->adapter.dispatch(d));}
}