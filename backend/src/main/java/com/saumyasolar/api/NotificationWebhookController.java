package com.saumyasolar.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.saumyasolar.domain.NotificationProviderEvent;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.NotificationProviderEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Profile({"dev","test"})
@RestController @RequestMapping("/api/v1/notification-webhooks/mock")
public class NotificationWebhookController {
  private final NotificationDeliveryRepository deliveries; private final NotificationProviderEventRepository events; private final ObjectMapper mapper; private final String secret;
  public NotificationWebhookController(NotificationDeliveryRepository d,NotificationProviderEventRepository e,ObjectMapper m,@Value("${app.messaging.mock.webhook-secret:local-mock-notification-secret-change-me}") String s){deliveries=d;events=e;mapper=m;secret=s;}
  @PostMapping @Transactional public Map<String,String> receive(@RequestHeader(value="X-Mock-Signature",required=false) String signature,@RequestBody String rawBody){
    try { byte[] raw=rawBody.getBytes(StandardCharsets.UTF_8); if(!verify(raw,signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
      Map<String,Object> payload=mapper.readValue(rawBody,new TypeReference<Map<String,Object>>(){});
      UUID tenant=UUID.fromString(String.valueOf(payload.get("tenantId")));UUID deliveryId=UUID.fromString(String.valueOf(payload.get("deliveryId")));String eventId=String.valueOf(payload.get("eventId"));String status=String.valueOf(payload.get("status")).toUpperCase();
      if(!java.util.Set.of("DELIVERED","FAILED","BOUNCED").contains(status)||eventId.length()>160)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid provider event");
      String payloadHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
      var prior=events.findByProviderCodeAndProviderEventId("MOCK",eventId);
      if(prior.isPresent()){if(!prior.get().getPayloadHash().equals(payloadHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Provider event replay payload mismatch");return Map.of("status","duplicate");}
      var delivery=deliveries.findByIdAndTenantId(deliveryId,tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery not found"));
      var event=new NotificationProviderEvent();event.setId(UUID.randomUUID());event.setTenantId(tenant);event.setDeliveryId(deliveryId);event.setProviderCode("MOCK");event.setProviderEventId(eventId);event.setEventType(status);event.setDeliveryStatus(status);event.setPayloadHash(payloadHash);event.setReceivedAt(Instant.now());events.save(event);
      delivery.setStatus("DELIVERED".equals(status)?"DELIVERED":"FAILED".equals(status)?"FAILED":"DEAD_LETTER");if("DELIVERED".equals(status))delivery.setSentAt(Instant.now());deliveries.save(delivery);return Map.of("status","accepted");
    } catch(ResponseStatusException ex){throw ex;} catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid webhook payload");}
  }
  private boolean verify(byte[] raw,String supplied){if(supplied==null||!supplied.matches("[0-9a-fA-F]{64}"))return false;try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] expected=mac.doFinal(raw);return MessageDigest.isEqual(expected,HexFormat.of().parseHex(supplied));}catch(Exception ex){return false;}}
}