package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class NotificationChannelAdapterRegistry {
 private final List<NotificationChannelAdapter> adapters;
 public NotificationChannelAdapterRegistry(List<NotificationChannelAdapter> adapters){this.adapters=List.copyOf(adapters);}
 public String providerCode(String channel){return adapters.stream().filter(a->a.supports(channel)).map(NotificationChannelAdapter::providerCode).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported notification channel"));}
 public void dispatch(NotificationDelivery delivery) throws Exception {adapters.stream().filter(a->a.supports(delivery.getChannel())).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported notification channel")).dispatch(delivery);}
}