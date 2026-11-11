package com.saumyasolar.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.domain.NotificationProviderEvent;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.NotificationProviderEventRepository;
import com.saumyasolar.repo.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationWebhookControllerTest {
 private static final String SECRET="unit-test-webhook-secret";
 private final NotificationDeliveryRepository deliveries=mock(NotificationDeliveryRepository.class);
 private final NotificationProviderEventRepository events=mock(NotificationProviderEventRepository.class);
 private final NotificationWebhookController controller=new NotificationWebhookController(deliveries,events,new ObjectMapper(),SECRET);
 private String payload(UUID tenant,UUID delivery){return "{\"tenantId\":\""+tenant+"\",\"deliveryId\":\""+delivery+"\",\"eventId\":\"evt-100\",\"status\":\"DELIVERED\"}";}
 private String signature(String body)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));}
 @Test void acceptsSignedTenantBoundDeliveryEventAndPersistsIt()throws Exception{
  UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();String body=payload(tenant,id);var delivery=new NotificationDelivery();delivery.setId(id);delivery.setTenantId(tenant);delivery.setStatus("PENDING");
  when(deliveries.findByIdAndTenantId(id,tenant)).thenReturn(Optional.of(delivery));when(events.findByProviderCodeAndProviderEventId("MOCK","evt-100")).thenReturn(Optional.empty());
  assertEquals("accepted",controller.receive(signature(body),body).get("status"));assertEquals("DELIVERED",delivery.getStatus());verify(events).save(any(NotificationProviderEvent.class));verify(deliveries).save(delivery);
 }
 @Test void rejectsInvalidSignatureBeforeLookingUpDelivery(){UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();String body=payload(tenant,id);assertThrows(ResponseStatusException.class,()->controller.receive("00",body));verifyNoInteractions(deliveries,events);}
 @Test void treatsIdenticalProviderEventReplayAsIdempotent()throws Exception{UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();String body=payload(tenant,id);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));var previous=new NotificationProviderEvent();previous.setPayloadHash(hash);when(events.findByProviderCodeAndProviderEventId("MOCK","evt-100")).thenReturn(Optional.of(previous));assertEquals("duplicate",controller.receive(signature(body),body).get("status"));verifyNoInteractions(deliveries);}
 @Test void providerDeliveryCallbackUpdatesTenantScopedOutboxState()throws Exception{
  UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();String body=payload(tenant,id);
  var delivery=new NotificationDelivery();delivery.setId(id);delivery.setTenantId(tenant);delivery.setStatus("SENT");
  var row=new com.saumyasolar.domain.NotificationOutbox();row.setId(UUID.randomUUID());row.setTenantId(tenant);row.setDeliveryId(id);row.setStatus("SUCCEEDED");
  var outbox=mock(NotificationOutboxRepository.class);
  when(deliveries.findByIdAndTenantId(id,tenant)).thenReturn(Optional.of(delivery));
  when(events.findByProviderCodeAndProviderEventId("MOCK","evt-100")).thenReturn(Optional.empty());
  when(outbox.findByDeliveryIdAndTenantId(id,tenant)).thenReturn(Optional.of(row));
  var controller=new NotificationWebhookController(deliveries,events,outbox,new ObjectMapper(),SECRET);
  assertEquals("accepted",controller.receive(signature(body),body).get("status"));
  assertEquals("DELIVERED",delivery.getStatus());assertEquals("SUCCEEDED",row.getStatus());
  verify(outbox).save(row);
 }

}