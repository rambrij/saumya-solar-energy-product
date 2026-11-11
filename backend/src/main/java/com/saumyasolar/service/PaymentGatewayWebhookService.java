package com.saumyasolar.service;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentGatewayWebhookService {
    private final PaymentGatewayRegistry adapters;
    private final PaymentRepository payments;
    private final PaymentWebhookEventRepository events;
    private final InvoiceRepository invoices;
    private final RefundRepository refunds;
    private final AuditEventRepository audits;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public PaymentGatewayWebhookService(PaymentGatewayRegistry adapters, PaymentRepository payments,
            PaymentWebhookEventRepository events, InvoiceRepository invoices, RefundRepository refunds,
            AuditEventRepository audits, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.adapters = adapters;
        this.payments = payments;
        this.events = events;
        this.invoices = invoices;
        this.refunds = refunds;
        this.audits = audits;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> accept(String provider, String raw, String signature) {
        if (provider == null || !provider.matches("[A-Za-z0-9_-]{2,60}") || raw == null || raw.length() > 65536)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment webhook request");
        PaymentGatewayAdapter adapter = adapters.require(provider);
        if (!adapter.verifyWebhook(raw, signature))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment provider signature");
        PaymentGatewayAdapter.GatewayWebhookEvent event;
        try { event = adapter.parseWebhook(raw); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment provider event"); }
        Set<String> paymentEvents = Set.of("PAYMENT_CAPTURED", "PAYMENT_FAILED", "PAYMENT_CANCELLED", "SUCCEEDED", "FAILED");
        Set<String> refundEvents = Set.of("REFUND_PROCESSED", "REFUND_FAILED");
        if (event == null || event.eventId() == null || event.eventId().isBlank() || event.eventId().length() > 180
                || event.providerReference() == null || event.providerReference().isBlank()
                || event.amount() == null || event.amount().signum() <= 0
                || event.currency() == null || !event.currency().matches("[A-Z]{3}")
                || event.tenantId() == null || event.tenantId().isBlank()
                || !(paymentEvents.contains(event.status()) || refundEvents.contains(event.status())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment provider event is incomplete or unsupported");

        String code = provider.toUpperCase(Locale.ROOT);
        UUID eventTenant;
        try { eventTenant = UUID.fromString(event.tenantId()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment tenant metadata"); }
        Payment payment = payments.findByTenantIdAndProviderAndProviderReference(eventTenant, code, event.providerReference()).orElse(null);
        if (payment == null) return Map.of("accepted", false, "matched", false, "retryable", true);
        if (!payment.getCurrency().equalsIgnoreCase(event.currency()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment provider currency does not match the tenant payment");
        if (events.findByTenantIdAndProviderAndEventId(payment.getTenantId(), code, event.eventId()).isPresent())
            return Map.of("accepted", true, "duplicate", true);

        Refund refund = null;
        boolean isRefund = refundEvents.contains(event.status());
        if (isRefund) {
            if (event.providerRefundReference() == null || event.providerRefundReference().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider refund reference is required");
            refund = refunds.findByTenantIdAndProviderAndProviderRefundReference(
                    payment.getTenantId(), code, event.providerRefundReference()).orElse(null);
            if (refund == null || !refund.getPaymentId().equals(payment.getId()))
                return Map.of("accepted", false, "matched", false, "retryable", true);
            if (refund.getAmount().compareTo(event.amount()) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund amount does not match the recorded provider refund");
        } else if (payment.getAmount().compareTo(event.amount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment amount does not match the recorded provider payment");
        }

        PaymentWebhookEvent record = new PaymentWebhookEvent();
        record.setId(UUID.randomUUID());
        record.setTenantId(payment.getTenantId());
        record.setProvider(code);
        record.setEventId(event.eventId());
        record.setPayloadHash(hash(raw));
        record.setProcessingStatus("RECEIVED");
        record.setProviderRefundReference(event.providerRefundReference());
        record.setReceivedAt(Instant.now());
        events.saveAndFlush(record);

        if (isRefund) {
            refund.setStatus("REFUND_PROCESSED".equals(event.status()) ? "PROCESSED" : "FAILED");
            refund.setProcessedAt(Instant.now());
            refunds.save(refund);
            refreshPaymentAndInvoice(payment);
        } else {
            if ("PAYMENT_CAPTURED".equals(event.status()) || "SUCCEEDED".equals(event.status())) {
                if (!Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED").contains(payment.getStatus())) {
                    payment.setStatus("CONFIRMED");
                    payment.setPaidAt(Instant.now());
                }
            } else if ("PAYMENT_CANCELLED".equals(event.status()) && "PENDING".equals(payment.getStatus())) {
                payment.setStatus("CANCELLED");
            } else if (("PAYMENT_FAILED".equals(event.status()) || "FAILED".equals(event.status()))
                    && "PENDING".equals(payment.getStatus())) {
                payment.setStatus("FAILED");
            }
            payments.save(payment);
            refreshPaymentAndInvoice(payment);
        }

        record.setProcessingStatus("PROCESSED");
        record.setProcessedAt(Instant.now());
        events.save(record);
        AuditEvent audit = new AuditEvent();
        audit.setId(UUID.randomUUID());
        audit.setTenantId(payment.getTenantId());
        audit.setActorType("SYSTEM");
        audit.setAction("PAYMENT_GATEWAY_WEBHOOK_" + event.status());
        audit.setEntityType(isRefund ? "REFUND" : "PAYMENT");
        audit.setEntityId(isRefund ? refund.getId().toString() : payment.getId().toString());
        audit.setOutcome("SUCCESS");
        try {
            audit.setAfterJson(mapper.writeValueAsString(Map.of("providerCode", code,
                    "eventIdHash", hash(event.eventId()), "paymentStatus", payment.getStatus(),
                    "eventStatus", event.status())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payment webhook audit event", ex);
        }
        audit.setCreatedAt(Instant.now());
        audits.save(audit);
        return Map.of("accepted", true, "matched", true, "duplicate", false);
    }

    private void refreshPaymentAndInvoice(Payment payment) {
        List<Refund> rows = refunds.findByTenantIdAndPaymentId(payment.getTenantId(), payment.getId());
        BigDecimal processed = rows.stream().filter(r -> Set.of("RECORDED", "PROCESSED").contains(r.getStatus()))
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = rows.stream().filter(r -> "PENDING".equals(r.getStatus()))
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (processed.compareTo(payment.getAmount()) >= 0) payment.setStatus("REFUNDED");
        else if (processed.signum() > 0 || reserved.signum() > 0) payment.setStatus("PARTIALLY_REFUNDED");
        else if (Set.of("PARTIALLY_REFUNDED", "REFUNDED").contains(payment.getStatus())) payment.setStatus("CONFIRMED");
        payments.save(payment);
        Invoice invoice = invoices.findForUpdate(payment.getInvoiceId(), payment.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        BigDecimal net = BigDecimal.ZERO;
        for (Payment p : payments.findByTenantIdAndInvoiceId(payment.getTenantId(), invoice.getId())) {
            if (!Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED").contains(p.getStatus())) continue;
            BigDecimal refunded = refunds.findByTenantIdAndPaymentId(payment.getTenantId(), p.getId()).stream()
                    .filter(r -> Set.of("RECORDED", "PROCESSED").contains(r.getStatus()))
                    .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            net = net.add(p.getAmount().subtract(refunded));
        }
        invoice.setStatus(net.compareTo(invoice.getGrandTotal()) >= 0 ? "PAID" : net.signum() > 0 ? "PARTIALLY_PAID" : "ISSUED");
        invoices.save(invoice);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
