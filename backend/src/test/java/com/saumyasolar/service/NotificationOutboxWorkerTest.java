package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationOutboxWorkerTest {
    @Test
    void marksDeliveryAsDeliveredAfterSuccessfulSmtpSend() {
        var repo=mock(NotificationDeliveryRepository.class);
        var sender=mock(JavaMailSender.class);
        var record=record();
        when(repo.findDueForUpdate(any(),eq(6),any(PageRequest.class))).thenReturn(List.of(record));
        var worker=new NotificationOutboxWorker(repo,sender,"no-reply@example.test",10);
        worker.dispatchDue();
        assertEquals("DELIVERED",record.getStatus());
        assertNotNull(record.getSentAt());
        assertNull(record.getNextAttemptAt());
        verify(sender).send(any(SimpleMailMessage.class));
        verify(repo).save(record);
    }

    @Test
    void schedulesBoundedRetryWhenSmtpIsUnavailable() {
        var repo=mock(NotificationDeliveryRepository.class);
        var sender=mock(JavaMailSender.class);
        var record=record();
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        when(repo.findDueForUpdate(any(),eq(6),any(PageRequest.class))).thenReturn(List.of(record));
        var worker=new NotificationOutboxWorker(repo,sender,"no-reply@example.test",10);
        worker.dispatchDue();
        assertEquals("PENDING",record.getStatus());
        assertEquals(1,record.getAttemptCount());
        assertNotNull(record.getNextAttemptAt());
        assertTrue(record.getNextAttemptAt().isAfter(Instant.now()));
        assertNotNull(record.getLastError());
        verify(repo).save(record);
    }

    private NotificationDelivery record() {
        var record=new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setEventType("QUOTE_SENT");
        record.setRecipient("customer@example.test");
        record.setSubject("Quotation");
        record.setBody("Your quotation is ready");
        record.setStatus("PENDING");
        record.setAttemptCount(0);
        record.setCreatedAt(Instant.now());
        record.setNextAttemptAt(Instant.now());
        return record;
    }
}
