package com.saumyasolar.service;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private NotificationService service(JavaMailSender mail, NotificationDeliveryRepository deliveries,
            CustomerProfileRepository customers, NotificationOutboxRepository outbox,
            NotificationChannelAdapterRegistry adapters, NotificationPreferenceRepository preferences,
            UserNotificationService inbox) {
        return new NotificationService(mail, deliveries, customers, outbox, adapters, preferences,
                inbox, "no-reply@saumya.local", "http://localhost:3000");
    }

    @Test void persistsDeliveryAndOutboxTogetherWithoutSendingInBusinessTransaction() {
        var mail=mock(JavaMailSender.class);var deliveries=mock(NotificationDeliveryRepository.class);
        var outbox=mock(NotificationOutboxRepository.class);var customers=mock(CustomerProfileRepository.class);
        var adapters=mock(NotificationChannelAdapterRegistry.class);var preferences=mock(NotificationPreferenceRepository.class);
        var inbox=mock(UserNotificationService.class);
        when(deliveries.existsByTenantIdAndIdempotencyKey(any(),anyString())).thenReturn(false);
        UUID tenant=UUID.randomUUID(),user=UUID.randomUUID();
        assertTrue(service(mail,deliveries,customers,outbox,adapters,preferences,inbox)
                .sendBusinessNotification(user,tenant,"ORDER_CONFIRMED","customer@example.test","Order confirmed","Your order is confirmed"));
        var delivery=org.mockito.ArgumentCaptor.forClass(NotificationDelivery.class);
        var item=org.mockito.ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(deliveries).save(delivery.capture());verify(outbox).save(item.capture());
        assertEquals("PENDING",delivery.getValue().getStatus());
        assertEquals("PENDING",item.getValue().getStatus());
        assertEquals(tenant,item.getValue().getTenantId());
        assertEquals(delivery.getValue().getId(),item.getValue().getDeliveryId());
        verifyNoInteractions(mail,adapters);
    }

    @Test void duplicateBusinessMessageDoesNotCreateAnotherOutboxRow() {
        var deliveries=mock(NotificationDeliveryRepository.class);var outbox=mock(NotificationOutboxRepository.class);
        when(deliveries.existsByTenantIdAndIdempotencyKey(any(),anyString())).thenReturn(true);
        var svc=service(mock(JavaMailSender.class),deliveries,mock(CustomerProfileRepository.class),outbox,
                mock(NotificationChannelAdapterRegistry.class),mock(NotificationPreferenceRepository.class),mock(UserNotificationService.class));
        assertFalse(svc.sendBusinessNotification(UUID.randomUUID(),UUID.randomUUID(),"ORDER_CONFIRMED",
                "customer@example.test","Order confirmed","Same payload"));
        verify(deliveries,never()).save(any());verifyNoInteractions(outbox);
    }

    @Test void smsAndWhatsappAreNotQueuedWithoutPersistedExplicitConsent() {
        var deliveries=mock(NotificationDeliveryRepository.class);var customers=mock(CustomerProfileRepository.class);
        var prefs=mock(NotificationPreferenceRepository.class);var outbox=mock(NotificationOutboxRepository.class);
        when(deliveries.existsByTenantIdAndIdempotencyKey(any(),anyString())).thenReturn(false);
        UUID tenant=UUID.randomUUID(),user=UUID.randomUUID();var customer=new CustomerProfile();
        customer.setTenantId(tenant);customer.setUserId(user);customer.setPhone("+919876543210");
        when(customers.findByTenantIdAndUserId(tenant,user)).thenReturn(Optional.of(customer));
        var svc=service(mock(JavaMailSender.class),deliveries,customers,outbox,
                mock(NotificationChannelAdapterRegistry.class),prefs,mock(UserNotificationService.class));
        assertTrue(svc.sendBusinessNotification(user,tenant,"ORDER_CONFIRMED","customer@example.test","Order","Body"));
        var captured=org.mockito.ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries).save(captured.capture());
        assertEquals("EMAIL",captured.getValue().getChannel());
        verify(deliveries,times(1)).save(any(NotificationDelivery.class));
        verify(outbox,times(1)).save(any(NotificationOutbox.class));
    }

    @Test void consentedSmsIsQueuedOnlyWhenPreferenceIsEnabledAndConsented() {
        var deliveries=mock(NotificationDeliveryRepository.class);var customers=mock(CustomerProfileRepository.class);
        var prefs=mock(NotificationPreferenceRepository.class);var outbox=mock(NotificationOutboxRepository.class);
        when(deliveries.existsByTenantIdAndIdempotencyKey(any(),anyString())).thenReturn(false);
        UUID tenant=UUID.randomUUID(),user=UUID.randomUUID();var customer=new CustomerProfile();
        customer.setTenantId(tenant);customer.setUserId(user);customer.setPhone("+919876543210");
        when(customers.findByTenantIdAndUserId(tenant,user)).thenReturn(Optional.of(customer));
        var consent=new NotificationPreference();consent.setEnabled(true);consent.setConsented(true);
        when(prefs.findByTenantIdAndUserIdAndChannel(tenant,user,"SMS")).thenReturn(Optional.of(consent));
        var svc=service(mock(JavaMailSender.class),deliveries,customers,outbox,
                mock(NotificationChannelAdapterRegistry.class),prefs,mock(UserNotificationService.class));
        assertTrue(svc.sendBusinessNotification(user,tenant,"ORDER_CONFIRMED","customer@example.test","Order","Body"));
        var captured=org.mockito.ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries,times(2)).save(captured.capture());
        assertTrue(captured.getAllValues().stream().anyMatch(d->"SMS".equals(d.getChannel())));
        verify(outbox,times(2)).save(any(NotificationOutbox.class));
    }

    @Test void failedAdapterAttemptPersistsBackoffAndNeverLogsProviderException() throws Exception {
        var deliveries=mock(NotificationDeliveryRepository.class);var adapters=mock(NotificationChannelAdapterRegistry.class);
        UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();var delivery=new NotificationDelivery();
        delivery.setId(id);delivery.setTenantId(tenant);delivery.setChannel("EMAIL");delivery.setStatus("PENDING");delivery.setAttemptCount(0);
        when(deliveries.claimPendingDelivery(eq(id),eq(tenant),any(),any())).thenReturn(1);
        when(deliveries.findByIdAndTenantId(id,tenant)).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("secret and PII must not leak")).when(adapters).dispatch(delivery);
        service(mock(JavaMailSender.class),deliveries,null,mock(NotificationOutboxRepository.class),adapters,
                mock(NotificationPreferenceRepository.class),null).processOutboxDelivery(id,tenant);
        assertEquals("FAILED",delivery.getStatus());assertEquals(1,delivery.getAttemptCount());
        assertNotNull(delivery.getNextAttemptAt());assertEquals("Notification provider delivery failed",delivery.getLastError());
    }

    @Test void securityRecoveryEmailUsesOnlyConfiguredLocalSmtpSink() {
        var mail=mock(JavaMailSender.class);var deliveries=mock(NotificationDeliveryRepository.class);
        when(deliveries.save(any())).thenAnswer(inv->inv.getArgument(0));
        assertTrue(new NotificationService(mail,deliveries,"no-reply@saumya.local","http://localhost:3000")
                .sendPasswordReset(UUID.randomUUID(),UUID.randomUUID(),"user@example.test","User","one-time-token"));
        verify(mail).send(any(SimpleMailMessage.class));
    }
}
