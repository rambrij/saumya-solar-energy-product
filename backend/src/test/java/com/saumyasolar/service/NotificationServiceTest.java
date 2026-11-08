package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;
import java.time.Instant;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void sendsAndRecordsPasswordResetEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationService service = new NotificationService(sender, repository, "no-reply@example.test", "http://localhost:3000");

        boolean sent = service.sendPasswordReset(UUID.randomUUID(), UUID.randomUUID(),
                "customer@example.test", "Solar Customer", "random-one-time-token");

        assertTrue(sent);
        verify(sender).send(any(SimpleMailMessage.class));
        verify(repository, times(2)).save(any(NotificationDelivery.class));
    }

    @Test
    void recordsFailedSmtpDeliveryWithoutThrowingToCaller() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        NotificationService service = new NotificationService(sender, repository, "no-reply@example.test", "http://localhost:3000");

        boolean sent = service.sendBusinessNotification(UUID.randomUUID(), UUID.randomUUID(),
                "QUOTE_SENT", "customer@example.test", "Your quotation", "Quotation details");

        assertFalse(sent);
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository, times(2)).save(saved.capture());
        NotificationDelivery failed = saved.getAllValues().get(1);
        assertEquals("FAILED", failed.getStatus());
        assertEquals(1, failed.getAttemptCount());
        assertNotNull(failed.getNextAttemptAt());
        assertTrue(failed.getNextAttemptAt().isAfter(Instant.now()));
    }
}
