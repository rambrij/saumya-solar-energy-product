package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class MockMessagingNotificationAdapter implements NotificationChannelAdapter {
 private final boolean enabled;
 public MockMessagingNotificationAdapter(@Value("${app.messaging.mock.enabled:true}") boolean enabled){this.enabled=enabled;}
 public String channel(){return "SMS,WHATSAPP";} public String providerCode(){return "MOCK-MESSAGING";}
 public boolean supports(String c){return "SMS".equals(c)||"WHATSAPP".equals(c);}
 public void dispatch(NotificationDelivery d){if(!enabled)throw new IllegalStateException("Mock messaging adapter is disabled");if(d.getMessageBody()!=null&&d.getMessageBody().contains("[[MOCK_FAIL]]"))throw new IllegalStateException("Mock provider simulated transient failure");}
}