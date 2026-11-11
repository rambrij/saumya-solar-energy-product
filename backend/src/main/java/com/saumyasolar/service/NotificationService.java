package com.saumyasolar.service;

import com.saumyasolar.domain.CustomerProfile;
import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.domain.NotificationOutbox;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.NotificationOutboxRepository;
import com.saumyasolar.repo.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final JavaMailSender mailSender;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationOutboxRepository outbox;
    private final NotificationChannelAdapterRegistry adapters;
    private final CustomerProfileRepository customers;
    private final NotificationPreferenceRepository preferences;
    private final UserNotificationService inbox;
    private final String from;
    private final String publicBaseUrl;

    @Autowired
    public NotificationService(JavaMailSender mailSender,
            NotificationDeliveryRepository deliveries,
            CustomerProfileRepository customers,
            NotificationOutboxRepository outbox,
            NotificationChannelAdapterRegistry adapters,
            NotificationPreferenceRepository preferences,
            UserNotificationService inbox,
            @Value("${app.mail.from:no-reply@saumya.local}") String from,
            @Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.deliveries = deliveries;
        this.customers = customers;
        this.outbox = outbox;
        this.adapters = adapters;
        this.preferences = preferences;
        this.inbox = inbox;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    // Lightweight constructor retained for isolated password-reset/MFA unit tests.
    NotificationService(JavaMailSender mailSender, NotificationDeliveryRepository deliveries,
            String from, String publicBaseUrl) {
        this(mailSender, deliveries, null, null, null, null, null, from, publicBaseUrl);
    }

    public boolean sendPasswordReset(UUID userId, UUID tenantId, String recipient, String displayName, String token) {
        String link = publicBaseUrl + "/?resetToken=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String body = "Hello " + safeName(displayName) + ",\n\n"
                + "We received a request to reset your Soumya Solar account password. "
                + "Use the secure link below within 30 minutes. The link can be used only once.\n\n"
                + link + "\n\nIf you did not request this change, you can ignore this email.\n";
        return deliverSecurityEmail(userId, tenantId, "PASSWORD_RESET", recipient,
                "Reset your Soumya Solar password", body);
    }

    public boolean sendMfaCode(UUID userId, UUID tenantId, String recipient, String displayName, String code) {
        String body = "Hello " + safeName(displayName) + ",\n\nYour Soumya Solar sign-in verification code is "
                + code + ". It expires in 5 minutes. Do not share this code with anyone.\n";
        return deliverSecurityEmail(userId, tenantId, "MFA_CODE", recipient,
                "Your sign-in verification code", body);
    }

    /**
     * Creates the channel delivery and outbox row in the caller's business transaction.
     * No channel adapter is called here: the scheduler dispatches only after commit.
     */
    @Transactional
    public boolean sendBusinessNotification(UUID userId, UUID tenantId, String eventType,
            String recipient, String subject, String body) {
        if (tenantId == null) throw new IllegalArgumentException("Business notifications require a tenant");
        if (outbox == null || adapters == null) throw new IllegalStateException("Transactional notification outbox is not configured");

        if (inbox != null && userId != null) {
            try {
                inbox.createBusinessNotification(userId, tenantId, eventType, subject, body);
            } catch (RuntimeException ex) {
                // Inbox is an independent projection; the durable channel outbox remains the delivery source.
                log.warn("In-app notification projection failed for business event {}", safeEvent(eventType));
            }
        }

        int queued = 0;
        if (recipient != null && !recipient.isBlank()) {
            if (queueDelivery(userId, tenantId, eventType, "EMAIL", recipient, subject, body)) queued++;
        }
        queued += queueConsentedChannels(userId, tenantId, eventType, subject, body);
        return queued > 0;
    }

    private boolean queueDelivery(UUID userId, UUID tenantId, String eventType, String channel,
            String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) return false;
        String normalizedRecipient = recipient.trim();
        String normalizedSubject = subject == null || subject.isBlank() ? safeEvent(eventType) : subject.trim();
        String material = tenantId + ":" + userId + ":" + safeEvent(eventType) + ":" + channel + ":"
                + normalizedRecipient + ":" + normalizedSubject + ":" + (body == null ? "" : body);
        String idempotencyKey = UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
        if (deliveries.existsByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)) return false;

        Instant now = Instant.now();
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setTenantId(tenantId);
        delivery.setUserId(userId);
        delivery.setEventType(safeEvent(eventType));
        delivery.setChannel(channel);
        delivery.setRecipient(normalizedRecipient);
        delivery.setSubject(normalizedSubject);
        delivery.setMessageBody(body == null ? "" : body);
        delivery.setIdempotencyKey(idempotencyKey);
        delivery.setStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(now);
        deliveries.save(delivery);

        NotificationOutbox item = new NotificationOutbox();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setDeliveryId(delivery.getId());
        item.setStatus("PENDING");
        item.setAttemptCount(0);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        outbox.save(item);
        return true;
    }

    private int queueConsentedChannels(UUID userId, UUID tenantId, String eventType, String subject, String body) {
        if (customers == null || preferences == null || userId == null) return 0;
        CustomerProfile customer = customers.findByTenantIdAndUserId(tenantId, userId)
                .filter(CustomerProfile::isActive).orElse(null);
        if (customer == null || customer.getPhone() == null
                || !customer.getPhone().matches("^\\+[1-9][0-9]{7,14}$")) return 0;

        int queued = 0;
        for (String channel : List.of("SMS", "WHATSAPP")) {
            var preference = preferences.findByTenantIdAndUserIdAndChannel(tenantId, userId, channel);
            if (preference.isEmpty() || !preference.get().isEnabled() || !preference.get().isConsented()) continue;
            String destination = "WHATSAPP".equals(channel) ? "whatsapp:" + customer.getPhone() : customer.getPhone();
            String message = "WHATSAPP".equals(channel)
                    ? (subject == null ? safeEvent(eventType) : subject) + "\n" + (body == null ? "" : body)
                    : (body == null ? "" : body);
            if (queueDelivery(userId, tenantId, eventType, channel, destination, eventType, message)) queued++;
        }
        return queued;
    }

    /** Called only by the outbox dispatcher while a tenant execution scope is active. */
    public void processOutboxDelivery(UUID deliveryId, UUID tenantId) {
        NotificationDelivery record = deliveries.findByIdAndTenantId(deliveryId, tenantId).orElse(null);
        if (record == null) return;
        Instant now = Instant.now();
        if ("FAILED".equals(record.getStatus())) {
            if (record.getAttemptCount() >= MAX_ATTEMPTS) {
                record.setStatus("DEAD_LETTER");
                record.setNextAttemptAt(null);
                deliveries.save(record);
                return;
            }
            if (record.getNextAttemptAt() != null && record.getNextAttemptAt().isAfter(now)) return;
            if (deliveries.claimFailedDelivery(deliveryId, tenantId, now) != 1) return;
            dispatchClaimed(record);
            return;
        }
        if (!"PENDING".equals(record.getStatus())) return;
        if (deliveries.claimPendingDelivery(deliveryId, tenantId, now, now.minusSeconds(60)) != 1) return;
        NotificationDelivery claimed = deliveries.findByIdAndTenantId(deliveryId, tenantId).orElse(null);
        if (claimed != null && "PENDING".equals(claimed.getStatus())) dispatchClaimed(claimed);
    }

    private void dispatchClaimed(NotificationDelivery record) {
        try {
            adapters.dispatch(record);
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setStatus(("SMS".equals(record.getChannel()) || "WHATSAPP".equals(record.getChannel())) ? "SENT" : "DELIVERED");
            record.setSentAt(Instant.now());
            record.setNextAttemptAt(null);
            record.setLastError(null);
        } catch (Exception ex) {
            int attempts = record.getAttemptCount() + 1;
            record.setAttemptCount(attempts);
            record.setStatus(attempts >= MAX_ATTEMPTS ? "DEAD_LETTER" : "FAILED");
            record.setNextAttemptAt(attempts >= MAX_ATTEMPTS ? null : Instant.now().plusSeconds(backoffSeconds(attempts)));
            // Never persist exception messages: provider exceptions can contain recipient or credential data.
            record.setLastError("Notification provider delivery failed");
        }
        deliveries.save(record);
    }

    private long backoffSeconds(int attempt) {
        return Math.min(3600L, 60L * (1L << Math.min(Math.max(attempt - 1, 0), 5)));
    }

    private boolean deliverSecurityEmail(UUID userId, UUID tenantId, String eventType,
            String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) return false;
        NotificationDelivery record = new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setUserId(userId);
        record.setEventType(eventType);
        record.setRecipient(recipient.trim());
        record.setSubject(subject);
        record.setChannel("EMAIL");
        record.setMessageBody(body);
        record.setIdempotencyKey(UUID.randomUUID().toString());
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
            mailSender.send(message); // Local Mailpit SMTP sink; never a public mail provider.
            record.setAttemptCount(1);
            record.setStatus("DELIVERED");
            record.setSentAt(Instant.now());
            record.setLastError(null);
            deliveries.save(record);
            return true;
        } catch (MailException ex) {
            record.setAttemptCount(1);
            record.setStatus("FAILED");
            record.setNextAttemptAt(null);
            record.setLastError("Local Mailpit delivery failed");
            deliveries.save(record);
            return false;
        }
    }

    private String safeEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) return "BUSINESS_EVENT";
        String value = eventType.trim();
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private String safeName(String name) {
        return name == null || name.isBlank() ? "there" : name.replaceAll("[\\r\\n]", " ").trim();
    }
}
