package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class NotificationOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);
    private static final int MAX_ATTEMPTS = 6;
    private final NotificationDeliveryRepository deliveries;
    private final JavaMailSender mailSender;
    private final String from;
    private final int batchSize;

    public NotificationOutboxWorker(NotificationDeliveryRepository deliveries,
                                    JavaMailSender mailSender,
                                    @Value("${app.mail.from:no-reply@saumya.local}") String from,
                                    @Value("${app.notifications.batch-size:25}") int batchSize) {
        this.deliveries = deliveries;
        this.mailSender = mailSender;
        this.from = from;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:5000}")
    @Transactional
    public void dispatchDue() {
        List<NotificationDelivery> due = deliveries.findDueForUpdate(
                Instant.now(), MAX_ATTEMPTS, PageRequest.of(0, batchSize));
        for (NotificationDelivery delivery : due) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(delivery.getRecipient());
                message.setSubject(delivery.getSubject());
                message.setText(delivery.getBody());
                mailSender.send(message);
                delivery.setStatus("DELIVERED");
                delivery.setSentAt(Instant.now());
                delivery.setLastError(null);
                delivery.setNextAttemptAt(null);
                delivery.setBody(null);
            } catch (Exception ex) {
                int attempts = delivery.getAttemptCount() + 1;
                delivery.setAttemptCount(attempts);
                delivery.setLastError(safeError(ex));
                if (attempts >= MAX_ATTEMPTS) {
                    delivery.setStatus("DEAD");
                    delivery.setNextAttemptAt(null);
                    delivery.setBody(null);
                } else {
                    delivery.setStatus("PENDING");
                    long baseDelay = "MFA_CODE".equals(delivery.getEventType()) ? 5L : 30L;
                    long delaySeconds = Math.min(3600L, baseDelay * (1L << Math.min(attempts - 1, 7)));
                    delivery.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(delaySeconds)));
                }
                log.warn("Notification delivery {} failed on attempt {} of {}", delivery.getId(),
                        attempts, MAX_ATTEMPTS);
            }
            deliveries.save(delivery);
        }
    }

    private String safeError(Exception ex) {
        String message = ex.getClass().getSimpleName() + ": " +
                (ex.getMessage() == null ? "SMTP delivery failed" : ex.getMessage());
        return message.length() > 950 ? message.substring(0, 950) : message;
    }
}
