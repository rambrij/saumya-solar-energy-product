package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {
    private final JavaMailSender mailSender;
    private final NotificationDeliveryRepository deliveries;
    private final String from;
    private final String publicBaseUrl;

    public NotificationService(JavaMailSender mailSender,
                               NotificationDeliveryRepository deliveries,
                               @Value("${app.mail.from:no-reply@saumya.local}") String from,
                               @Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.deliveries = deliveries;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public boolean sendPasswordReset(UUID userId, UUID tenantId, String recipient, String displayName, String token) {
        String link = publicBaseUrl + "/?resetToken=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String body = "Hello " + safeName(displayName) + ",\n\n"
                + "We received a request to reset your Soumya Solar account password. "
                + "Use the secure link below within 30 minutes. The link can be used only once.\n\n"
                + link + "\n\n"
                + "If you did not request this change, you can ignore this email. "
                + "Your password will not change unless the link is used.\n";
        return deliver(userId, tenantId, "PASSWORD_RESET", recipient, "Reset your Soumya Solar password", body);
    }

    public boolean sendMfaCode(UUID userId, UUID tenantId, String recipient, String displayName, String code) {
        String body = "Hello " + safeName(displayName) + ",\n\n"
                + "Your Soumya Solar sign-in verification code is " + code
                + ". It expires in 5 minutes. Do not share this code with anyone.\n";
        return deliver(userId, tenantId, "MFA_CODE", recipient, "Your sign-in verification code", body);
    }

    public boolean sendBusinessNotification(UUID userId, UUID tenantId, String eventType,
                                             String recipient, String subject, String body) {
        return deliver(userId, tenantId, eventType, recipient, subject, body);
    }

    private boolean deliver(UUID userId, UUID tenantId, String eventType,
                            String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) return false;
        NotificationDelivery record = new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setUserId(userId);
        record.setEventType(eventType);
        record.setRecipient(recipient.trim());
        record.setSubject(subject);
        record.setStatus("PENDING");
        record.setAttemptCount(0);
        record.setCreatedAt(Instant.now());
        deliveries.save(record);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient.trim());
            message.setSubject(subject);
            message.setText(body);
            record.setAttemptCount(1);
            mailSender.send(message);
            record.setStatus("DELIVERED");
            record.setSentAt(Instant.now());
            record.setLastError(null);
            deliveries.save(record);
            return true;
        } catch (MailException ex) {
            record.setStatus("FAILED");
            record.setLastError("SMTP delivery failed");
            deliveries.save(record);
            return false;
        }
    }

    private String safeName(String name) {
        return name == null || name.isBlank() ? "there" : name.replaceAll("[\\r\\n]", " ").trim();
    }
}
