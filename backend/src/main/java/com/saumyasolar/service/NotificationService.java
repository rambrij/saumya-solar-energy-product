package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationDelivery;
import com.saumyasolar.repo.NotificationDeliveryRepository;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.domain.CustomerProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final JavaMailSender mailSender;
    private final NotificationDeliveryRepository deliveries;
    private final String from;
    private final String publicBaseUrl;
    private final CustomerProfileRepository customers;
    private final String twilioSid, twilioToken, smsFrom, whatsappFrom;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ApplicationEventPublisher eventPublisher;
    private final UserNotificationService inbox;
    
    public NotificationService(
            JavaMailSender mailSender,
            NotificationDeliveryRepository deliveries,
            String from,
            String publicBaseUrl
    ) {
        this(mailSender, deliveries, null, from, publicBaseUrl, "", "", "", "", null);
    }
    
    public NotificationService(
            JavaMailSender mailSender, NotificationDeliveryRepository deliveries, CustomerProfileRepository customers,
            String from, String publicBaseUrl, String twilioSid, String twilioToken,
            String smsFrom, String whatsappFrom, ApplicationEventPublisher eventPublisher
    ) {
        this(mailSender, deliveries, customers, from, publicBaseUrl, twilioSid, twilioToken,
                smsFrom, whatsappFrom, eventPublisher, null);
    }

    @Autowired
    public NotificationService(
            JavaMailSender mailSender, NotificationDeliveryRepository deliveries, CustomerProfileRepository customers,
            @Value("${app.mail.from:no-reply@saumya.local}") String from,
            @Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl,
            @Value("${app.messaging.twilio-account-sid:}") String twilioSid,
            @Value("${app.messaging.twilio-auth-token:}") String twilioToken,
            @Value("${app.messaging.sms-from:}") String smsFrom,
            @Value("${app.messaging.whatsapp-from:}") String whatsappFrom,
            ApplicationEventPublisher eventPublisher,
            UserNotificationService inbox
    ) {
        this.mailSender = mailSender;
        this.deliveries = deliveries;
        this.customers = customers;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.twilioSid = twilioSid;
        this.twilioToken = twilioToken;
        this.smsFrom = smsFrom;
        this.whatsappFrom = whatsappFrom;
        this.eventPublisher = eventPublisher;
        this.inbox = inbox;
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
    
    /**
     * Queues a tenant-scoped business notification for asynchronous delivery.
     * Authentication recovery and MFA messages deliberately remain synchronous.
     */
    public boolean sendBusinessNotification(
            UUID userId, UUID tenantId, String eventType,
            String recipient, String subject, String body
    ) {
        if (tenantId == null) throw new IllegalArgumentException("Business notifications require a tenant");
        if (inbox != null) {
            try {
                inbox.createBusinessNotification(userId, tenantId, eventType, subject, body);
            } catch (RuntimeException ex) {
                log.error("Unable to persist in-app notification for tenant {} and event {}", tenantId, eventType, ex);
            }
        }
        if (eventPublisher == null) {
            return deliverBusinessNotification(userId, tenantId, eventType, recipient, subject, body);
        }

        UUID emailId = queueDelivery(userId, tenantId, eventType, "EMAIL", recipient, subject, body);
        List<UUID> queuedIds = new java.util.ArrayList<>();
        if (emailId != null) queuedIds.add(emailId);
        queueConsentedChannels(userId, tenantId, eventType, subject, body, queuedIds);
        queuedIds.forEach(this::publishAfterCommit);
        return emailId != null;
    }

    boolean deliverBusinessNotification(
            UUID userId, UUID tenantId, String eventType,
            String recipient, String subject, String body
    ) {
        boolean emailDelivered = deliver(userId, tenantId, eventType, recipient, subject, body);
        sendConsentedChannels(userId, tenantId, eventType, subject, body);
        return emailDelivered;
    }

    private UUID queueDelivery(
            UUID userId, UUID tenantId, String eventType, String channel,
            String recipient, String subject, String body
    ) {
        if (recipient == null || recipient.isBlank()) return null;
        String normalizedRecipient = recipient.trim();
        String normalizedSubject = subject == null || subject.isBlank() ? eventType : subject;
        String idempotencyKey = UUID.nameUUIDFromBytes(
                (tenantId + ":" + userId + ":" + eventType + ":" + channel + ":"
                        + normalizedRecipient + ":" + normalizedSubject + ":" + body)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        if (deliveries.existsByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)) return null;

        NotificationDelivery record = new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setUserId(userId);
        record.setEventType(eventType);
        record.setChannel(channel);
        record.setRecipient(normalizedRecipient);
        record.setSubject(normalizedSubject);
        record.setMessageBody(body);
        record.setIdempotencyKey(idempotencyKey);
        record.setStatus("PENDING");
        record.setAttemptCount(0);
        record.setCreatedAt(Instant.now());
        deliveries.save(record);
        return record.getId();
    }

    private void queueConsentedChannels(
            UUID userId, UUID tenantId, String eventType, String subject, String body, List<UUID> queuedIds
    ) {
        if (customers == null || userId == null) return;
        CustomerProfile customer = customers.findByTenantIdAndUserId(tenantId, userId)
                .filter(CustomerProfile::isActive).orElse(null);
        if (customer == null || customer.getPhone() == null
                || !customer.getPhone().matches("^\\+[1-9][0-9]{7,14}$")) return;
        if (customer.isSmsConsent() && !smsFrom.isBlank()) {
            UUID id = queueDelivery(userId, tenantId, eventType, "SMS", customer.getPhone(), eventType, body);
            if (id != null) queuedIds.add(id);
        }
        if (customer.isWhatsappConsent() && !whatsappFrom.isBlank()) {
            String to = "whatsapp:" + customer.getPhone();
            String message = (subject == null ? eventType : subject) + "\n" + body;
            UUID id = queueDelivery(userId, tenantId, eventType, "WHATSAPP", to, eventType, message);
            if (id != null) queuedIds.add(id);
        }
    }

    private void publishAfterCommit(UUID deliveryId) {
        NotificationDelivery persisted = deliveries.findById(deliveryId).orElseThrow();
        BusinessNotificationRequestedEvent event =
                new BusinessNotificationRequestedEvent(this, deliveryId, persisted.getTenantId());
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eventPublisher.publishEvent(event); }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    public void processPendingDelivery(UUID deliveryId, UUID tenantId) {
        Instant now = Instant.now();
        if (deliveries.claimPendingDelivery(deliveryId, tenantId, now, now.minusSeconds(60)) != 1) return;
        NotificationDelivery record = deliveries.findByIdAndTenantId(deliveryId, tenantId).orElse(null);
        if (record == null || !"PENDING".equals(record.getStatus())) return;
        try {
            if ("EMAIL".equals(record.getChannel())) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(record.getRecipient());
                message.setSubject(record.getSubject());
                message.setText(record.getMessageBody());
                mailSender.send(message);
            } else {
                sendProviderDelivery(record);
            }
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setStatus("DELIVERED");
            record.setSentAt(Instant.now());
            record.setNextAttemptAt(null);
            record.setLastError(null);
        } catch (Exception ex) {
            int attempts = record.getAttemptCount() + 1;
            record.setAttemptCount(attempts);
            record.setStatus("FAILED");
            record.setLastError("Delivery attempt failed");
            record.setNextAttemptAt(attempts >= 5 ? null
                    : Instant.now().plusSeconds(Math.min(3600, 60L * (1L << Math.min(attempts, 5)))));
        }
        deliveries.save(record);
    }

    private void sendProviderDelivery(NotificationDelivery record) throws Exception {
        if (twilioSid.isBlank() || twilioToken.isBlank()) throw new IllegalStateException("Messaging provider is not configured");
        String sender = "SMS".equals(record.getChannel()) ? smsFrom
                : (whatsappFrom.startsWith("whatsapp:") ? whatsappFrom : "whatsapp:" + whatsappFrom);
        String form = "To=" + encForm(record.getRecipient()) + "&From=" + encForm(sender)
                + "&Body=" + encForm(record.getMessageBody());
        String auth = Base64.getEncoder().encodeToString((twilioSid + ":" + twilioToken).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Messaging provider returned HTTP " + response.statusCode());
    }

    private boolean deliver(
            UUID userId, UUID tenantId, String eventType,
            String recipient, String subject, String body
    ) {
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
        record.setIdempotencyKey(UUID.nameUUIDFromBytes(( tenantId + ":" + userId + ":" + eventType + ":EMAIL:" + recipient.trim() + ":" + subject + ":" + body ).getBytes(StandardCharsets.UTF_8)).toString());
        if (tenantId != null && deliveries.existsByTenantIdAndIdempotencyKey(tenantId, record.getIdempotencyKey()))
            return false;
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
            record.setNextAttemptAt(Instant.now().plusSeconds(60));
            record.setLastError("SMTP delivery failed");
            deliveries.save(record);
            return false;
        }
    }
    
    private void sendConsentedChannels(UUID userId, UUID tenantId, String eventType, String subject, String body) {
        if (customers == null || userId == null || tenantId == null || twilioSid.isBlank() || twilioToken.isBlank())
            return;
        CustomerProfile customer = customers.findByTenantIdAndUserId(tenantId, userId).filter(CustomerProfile::isActive).orElse(null);
        if (customer == null || customer.getPhone() == null || !customer.getPhone().matches("^\\+[1-9][0-9]{7,14}$"))
            return;
        if (customer.isSmsConsent() && !smsFrom.isBlank())
            sendProviderMessage(tenantId, userId, eventType, "SMS", customer.getPhone(), smsFrom, body);
        if (customer.isWhatsappConsent() && !whatsappFrom.isBlank())
            sendProviderMessage(tenantId, userId, eventType, "WHATSAPP", "whatsapp:" + customer.getPhone(), whatsappFrom.startsWith("whatsapp:") ? whatsappFrom : "whatsapp:" + whatsappFrom, subject + "\n" + body);
    }
    
    private void sendProviderMessage(
            UUID tenantId,
            UUID userId,
            String eventType,
            String channel,
            String to,
            String from,
            String body
    ) {
        NotificationDelivery record = new NotificationDelivery();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setUserId(userId);
        record.setEventType(eventType);
        record.setChannel(channel);
        record.setRecipient(to);
        record.setSubject(eventType);
        record.setMessageBody(body);
        record.setStatus("PENDING");
        record.setAttemptCount(0);
        record.setCreatedAt(Instant.now());
        record.setIdempotencyKey(UUID.nameUUIDFromBytes(( tenantId + ":" + userId + ":" + eventType + ":" + channel + ":" + body ).getBytes(StandardCharsets.UTF_8)).toString());
        if (deliveries.existsByTenantIdAndIdempotencyKey(tenantId, record.getIdempotencyKey())) return;
        deliveries.save(record);
        try {
            String form = "To=" + encForm(to) + "&From=" + encForm(from) + "&Body=" + encForm(body);
            String auth = Base64.getEncoder().encodeToString(( twilioSid + ":" + twilioToken ).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json")).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + auth).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            record.setAttemptCount(1);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                record.setStatus("DELIVERED");
                record.setSentAt(Instant.now());
            } else {
                record.setStatus("FAILED");
                record.setNextAttemptAt(Instant.now().plusSeconds(60));
                record.setLastError("Messaging provider returned HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            record.setAttemptCount(1);
            record.setStatus("FAILED");
            record.setNextAttemptAt(Instant.now().plusSeconds(60));
            record.setLastError("Messaging provider request failed");
        }
        deliveries.save(record);
    }
    
    @Scheduled(fixedDelayString = "${app.messaging.retry-interval-ms:60000}")
    public void retryFailedDeliveries() {
        Instant now = Instant.now();
        recoverPendingDeliveries(now);
        for (UUID tenantId : deliveries.findDistinctTenantIdsForRetry("FAILED", now)) {
            try (var scope = com.saumyasolar.security.TenantExecutionContext.open(tenantId)) {
                List<NotificationDelivery> batch = deliveries
                        .findTop50ByTenantIdAndStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(tenantId, "FAILED", now);
                for (NotificationDelivery record : batch) {
                    if (record.getAttemptCount() >= 5) continue;
                    if (deliveries.claimFailedDelivery(record.getId(), tenantId, Instant.now()) != 1) continue;
                    retryFailedDelivery(record);
                }
            }
        }
    }

    private void recoverPendingDeliveries(Instant now) {
        Instant staleBefore = now.minusSeconds(60);
        for (UUID tenantId : deliveries.findDistinctTenantIdsForPending(staleBefore)) {
            try (var scope = com.saumyasolar.security.TenantExecutionContext.open(tenantId)) {
                List<NotificationDelivery> pending = deliveries
                        .findTop50ByTenantIdAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(tenantId, "PENDING", staleBefore);
                for (NotificationDelivery record : pending) processPendingDelivery(record.getId(), tenantId);
            }
        }
    }

    private void retryFailedDelivery(NotificationDelivery record) {
        record.setStatus("PENDING");
        try {
            if ("EMAIL".equals(record.getChannel())) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(record.getRecipient());
                message.setSubject(record.getSubject());
                message.setText(record.getMessageBody());
                mailSender.send(message);
            } else {
                if (twilioSid.isBlank() || twilioToken.isBlank())
                    throw new IllegalStateException("Messaging provider is not configured");
                String to = record.getRecipient();
                String sender = "SMS".equals(record.getChannel()) ? smsFrom : ( whatsappFrom.startsWith("whatsapp:") ? whatsappFrom : "whatsapp:" + whatsappFrom );
                String form = "To=" + encForm(to) + "&From=" + encForm(sender) + "&Body=" + encForm(record.getMessageBody());
                String auth = Base64.getEncoder().encodeToString(( twilioSid + ":" + twilioToken ).getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json")).timeout(Duration.ofSeconds(10)).header("Authorization", "Basic " + auth).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IllegalStateException("Messaging provider returned HTTP " + response.statusCode());
            }
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setStatus("DELIVERED");
            record.setSentAt(Instant.now());
            record.setNextAttemptAt(null);
            record.setLastError(null);
        } catch (Exception ex) {
            int attempts = record.getAttemptCount() + 1;
            record.setAttemptCount(attempts);
            record.setStatus("FAILED");
            record.setLastError("Delivery retry failed");
            record.setNextAttemptAt(attempts >= 5 ? null : Instant.now().plusSeconds(Math.min(3600, 60L * ( 1L << Math.min(attempts, 5) ))));
        }
        deliveries.save(record);
    }
    
    private String encForm(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    private String safeName(String name) {
        return name == null || name.isBlank() ? "there" : name.replaceAll("[\\r\\n]", " ").trim();
    }
}
