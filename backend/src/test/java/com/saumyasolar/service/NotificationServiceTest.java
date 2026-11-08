package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void enqueuesPasswordResetEmailWithoutBlockingTheCaller() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationService service = new NotificationService(sender, repository, "no-reply@example.test", "http://localhost:3000");

        boolean queued = service.sendPasswordReset(UUID.randomUUID(), UUID.randomUUID(),
                "customer@example.test", "Solar Customer", "random-one-time-token");

        assertTrue(queued);
        var captor = org.mockito.ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("PASSWORD_RESET", captor.getValue().getEventType());
        assertTrue(captor.getValue().getBody().contains("random-one-time-token"));
        verifyNoInteractions(sender);
    }

    @Test
    void rejectsMissingRecipientWithoutCreatingAnOutboxRecord() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        NotificationService service = new NotificationService(sender, repository, "no-reply@example.test", "http://localhost:3000");

        assertFalse(service.sendBusinessNotification(UUID.randomUUID(), UUID.randomUUID(),
                "QUOTE_SENT", " ", "Your quotation", "Quotation details"));
        verifyNoInteractions(repository, sender);
    }
}
