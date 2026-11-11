package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.domain.NotificationOutbox;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.NotificationOutboxRepository;
import com.saumyasolar.repo.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationOutboxDispatcherTest {
 @Test void claimsAndCompletesDeliveryWithinItsTenantScope(){
  var outbox=mock(NotificationOutboxRepository.class);var deliveries=mock(NotificationDeliveryRepository.class);var service=mock(NotificationService.class);var prefs=mock(NotificationPreferenceRepository.class);
  UUID tenant=UUID.randomUUID(),deliveryId=UUID.randomUUID();var row=new NotificationOutbox();row.setId(UUID.randomUUID());row.setTenantId(tenant);row.setDeliveryId(deliveryId);row.setStatus("PENDING");row.setCreatedAt(Instant.now());
  var delivery=new NotificationDelivery();delivery.setId(deliveryId);delivery.setTenantId(tenant);delivery.setUserId(UUID.randomUUID());delivery.setChannel("EMAIL");delivery.setStatus("PENDING");
  when(outbox.findStaleProcessingTenantIds(any())).thenReturn(List.of());when(outbox.findDueTenantIds(any())).thenReturn(List.of(tenant));when(outbox.findDueByTenant(eq(tenant),anyList(),any())).thenReturn(List.of(row));when(outbox.claim(eq(row.getId()),eq(tenant),any())).thenReturn(1);
  when(deliveries.findByIdAndTenantId(deliveryId,tenant)).thenReturn(Optional.of(delivery));when(prefs.findByTenantIdAndUserIdAndChannel(eq(tenant),any(),eq("EMAIL"))).thenReturn(Optional.empty());
  doAnswer(inv->{delivery.setStatus("DELIVERED");delivery.setAttemptCount(1);return null;}).when(service).processOutboxDelivery(deliveryId,tenant);
  new NotificationOutboxDispatcher(outbox,deliveries,service,prefs).dispatch();
  assertEquals("SUCCEEDED",row.getStatus());assertEquals(1,row.getAttemptCount());verify(service).processOutboxDelivery(deliveryId,tenant);verify(outbox).save(row);
 }
 @Test void suppressesOptedOutChannelBeforeCallingAdapter(){
  var outbox=mock(NotificationOutboxRepository.class);var deliveries=mock(NotificationDeliveryRepository.class);var service=mock(NotificationService.class);var prefs=mock(NotificationPreferenceRepository.class);
  UUID tenant=UUID.randomUUID(),deliveryId=UUID.randomUUID(),user=UUID.randomUUID();var row=new NotificationOutbox();row.setId(UUID.randomUUID());row.setTenantId(tenant);row.setDeliveryId(deliveryId);row.setStatus("PENDING");row.setCreatedAt(Instant.now());
  var delivery=new NotificationDelivery();delivery.setId(deliveryId);delivery.setTenantId(tenant);delivery.setUserId(user);delivery.setChannel("EMAIL");delivery.setStatus("PENDING");
  var pref=new com.saumyasolar.domain.NotificationPreference();pref.setEnabled(false);
  when(outbox.findStaleProcessingTenantIds(any())).thenReturn(List.of());when(outbox.findDueTenantIds(any())).thenReturn(List.of(tenant));when(outbox.findDueByTenant(eq(tenant),anyList(),any())).thenReturn(List.of(row));when(outbox.claim(eq(row.getId()),eq(tenant),any())).thenReturn(1);
  when(deliveries.findByIdAndTenantId(deliveryId,tenant)).thenReturn(Optional.of(delivery));when(prefs.findByTenantIdAndUserIdAndChannel(tenant,user,"EMAIL")).thenReturn(Optional.of(pref));
  new NotificationOutboxDispatcher(outbox,deliveries,service,prefs).dispatch();
  assertEquals("SUPPRESSED",delivery.getStatus());assertEquals("SUCCEEDED",row.getStatus());verifyNoInteractions(service);verify(deliveries).save(delivery);
 }
}