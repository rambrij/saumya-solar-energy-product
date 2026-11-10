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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void sendsAndRecordsPasswordResetEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationService service = new NotificationService(sender, repository, "no-reply@example.test", "http://localhost:3000");
        
        boolean sent = service.sendPasswordReset(
                UUID.randomUUID(), UUID.randomUUID(),
                "customer@example.test", "Solar Customer", "random-one-time-token"
        );
        
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
        
        boolean sent = service.sendBusinessNotification(
                UUID.randomUUID(), UUID.randomUUID(),
                "QUOTE_SENT", "customer@example.test", "Your quotation", "Quotation details"
        );
        
        assertFalse(sent);
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository, times(2)).save(saved.capture());
        NotificationDelivery failed = saved.getAllValues().get(1);
        assertEquals("FAILED", failed.getStatus());
        assertEquals(1, failed.getAttemptCount());
        assertNotNull(failed.getNextAttemptAt());
        assertTrue(failed.getNextAttemptAt().isAfter(Instant.now()));
    }
    @Test
    void scheduledRetryRunsInsideTheDeliveryTenantContextAndClearsItAfterward() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        UUID tenantId = UUID.randomUUID();
        NotificationDelivery record = new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setUserId(UUID.randomUUID());
        record.setChannel("EMAIL");
        record.setRecipient("customer@example.test");
        record.setSubject("Retry test");
        record.setMessageBody("Retry body");
        record.setStatus("FAILED");
        record.setAttemptCount(1);
        record.setNextAttemptAt(Instant.now().minusSeconds(5));
        when(repository.findDistinctTenantIdsForRetry(eq("FAILED"), any()))
                .thenReturn(java.util.List.of(tenantId));
        when(repository.findTop50ByTenantIdAndStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(eq(tenantId), eq("FAILED"), any()))
                .thenReturn(java.util.List.of(record));
        when(repository.claimFailedDelivery(eq(record.getId()), eq(tenantId), any())).thenReturn(1);
        when(repository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertEquals(tenantId, com.saumyasolar.security.TenantExecutionContext.requireTenantId());
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));

        NotificationService service = new NotificationService(
                sender, repository, "no-reply@example.test", "http://localhost:3000"
        );
        service.retryFailedDeliveries();

        verify(sender).send(any(SimpleMailMessage.class));
        assertEquals("DELIVERED", record.getStatus());
        assertThrows(
                IllegalStateException.class,
                com.saumyasolar.security.TenantExecutionContext::requireTenantId
        );
    }

    @Test
    void scheduledRetrySkipsDeliveryClaimedByAnotherWorker() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        NotificationDelivery record = new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setTenantId(UUID.randomUUID());
        record.setChannel("EMAIL");
        record.setRecipient("customer@example.test");
        record.setSubject("Concurrent retry");
        record.setMessageBody("Retry body");
        record.setStatus("FAILED");
        record.setAttemptCount(1);
        record.setNextAttemptAt(Instant.now().minusSeconds(5));
        when(repository.findDistinctTenantIdsForRetry(eq("FAILED"), any()))
                .thenReturn(java.util.List.of(record.getTenantId()));
        when(repository.findTop50ByTenantIdAndStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(eq(record.getTenantId()), eq("FAILED"), any()))
                .thenReturn(java.util.List.of(record));
        when(repository.claimFailedDelivery(eq(record.getId()), eq(record.getTenantId()), any())).thenReturn(0);

        new NotificationService(sender, repository, "no-reply@example.test", "http://localhost:3000")
                .retryFailedDeliveries();

        verifyNoInteractions(sender);
        verify(repository, never()).save(any(NotificationDelivery.class));
    }

}
