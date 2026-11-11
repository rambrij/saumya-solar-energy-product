package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class NotificationChannelAdapterRegistry {
 private final MailpitEmailNotificationAdapter email; private final MockMessagingNotificationAdapter messaging;
 public NotificationChannelAdapterRegistry(MailpitEmailNotificationAdapter email,MockMessagingNotificationAdapter messaging){this.email=email;this.messaging=messaging;}
 public String providerCode(String channel){return "EMAIL".equals(channel)?email.providerCode():messaging.providerCode();}
 public void dispatch(NotificationDelivery delivery) throws Exception {if("EMAIL".equals(delivery.getChannel())){email.dispatch(delivery);return;}if(messaging.supports(delivery.getChannel())){messaging.dispatch(delivery);return;}throw new IllegalArgumentException("Unsupported notification channel");}
}