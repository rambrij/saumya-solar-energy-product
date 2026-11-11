package com.saumyasolar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic, network-free payment provider for local development and tests.
 * Never activate this adapter in production. All event signatures use HMAC-SHA256.
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "app.payment.mock", name = "enabled", havingValue = "true")
public class MockPaymentGatewayAdapter implements PaymentGatewayAdapter {
    private final ObjectMapper mapper;
    private final byte[] signingSecret;
    private final String checkoutOutcome;
    private final String frontendBaseUrl;
    private final Map<String, GatewayPaymentStatus> payments = new ConcurrentHashMap<>();
    private final Map<String, GatewayRefund> refunds = new ConcurrentHashMap<>();
    private final Map<String, String> outcomes = new ConcurrentHashMap<>();
    private final Map<String, String> tenants = new ConcurrentHashMap<>();

    public MockPaymentGatewayAdapter(
            ObjectMapper mapper,
            @Value("${app.payment.mock.signing-secret:}") String signingSecret,
            @Value("${app.payment.mock.checkout-outcome:PENDING}") String checkoutOutcome,
            @Value("${app.payment.mock.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.mapper = mapper;
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalStateException("app.payment.mock.signing-secret must contain at least 32 characters");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (!java.util.Set.of("SUCCESS", "FAILURE", "PENDING", "CANCELLED").contains(checkoutOutcome)) {
            throw new IllegalStateException("app.payment.mock.checkout-outcome must be SUCCESS, FAILURE, PENDING or CANCELLED");
        }
        this.checkoutOutcome = checkoutOutcome;
        if (frontendBaseUrl == null || !frontendBaseUrl.startsWith("http://") && !frontendBaseUrl.startsWith("https://")) {
            throw new IllegalStateException("app.payment.mock.frontend-base-url must be an absolute HTTP URL");
        }
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String providerCode() {
        return "MOCK_GATEWAY";
    }

    @Override
    public GatewayPayment createPayment(BigDecimal amount, String currency, String internalReference,
                                        Map<String, String> metadata) {
        if (amount == null || amount.signum() <= 0 || currency == null || !currency.matches("[A-Z]{3}")
                || internalReference == null || internalReference.isBlank()
                || metadata == null || metadata.get("idempotencyKey") == null
                || metadata.get("tenantId") == null || metadata.get("paymentId") == null) {
            throw new IllegalArgumentException("Mock checkout requires amount, currency, reference and idempotency metadata");
        }
        String reference = "mock_pay_" + sha256(metadata.get("tenantId") + ":" + metadata.get("idempotencyKey")).substring(0, 32);
        GatewayPaymentStatus status = new GatewayPaymentStatus(reference, "PENDING", amount, currency);
        payments.putIfAbsent(reference, status);
        outcomes.putIfAbsent(reference, checkoutOutcome);
        tenants.putIfAbsent(reference, metadata.get("tenantId"));
        return new GatewayPayment(reference, frontendBaseUrl + "/?mockCheckoutPaymentId=" + metadata.get("paymentId"), outcomes.get(reference));
    }

    @Override
    public boolean verifyWebhook(String rawPayload, String signature) {
        if (rawPayload == null || signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
        byte[] expected = hmac(rawPayload);
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, supplied);
    }

    @Override
    public GatewayWebhookEvent parseWebhook(String rawPayload) throws Exception {
        JsonNode json = mapper.readTree(rawPayload);
        return new GatewayWebhookEvent(
                requiredText(json, "eventId"),
                requiredText(json, "providerReference"),
                new BigDecimal(requiredText(json, "amount")),
                requiredText(json, "currency"),
                requiredText(json, "status"),
                json.hasNonNull("providerRefundReference") ? json.get("providerRefundReference").asText() : null,
                requiredText(json, "tenantId")
        );
    }

    @Override
    public GatewayPaymentStatus fetchStatus(String providerReference) {
        GatewayPaymentStatus status = payments.get(providerReference);
        if (status == null) throw new IllegalArgumentException("Unknown mock payment reference");
        String outcome = outcomes.get(providerReference);
        if ("SUCCESS".equals(outcome)) return new GatewayPaymentStatus(providerReference, "CONFIRMED", status.amount(), status.currency());
        if ("FAILURE".equals(outcome)) return new GatewayPaymentStatus(providerReference, "FAILED", status.amount(), status.currency());
        if ("CANCELLED".equals(outcome)) return new GatewayPaymentStatus(providerReference, "CANCELLED", status.amount(), status.currency());
        return status;
    }

    @Override
    public GatewayRefund refund(String providerReference, BigDecimal amount, String idempotencyKey, String reason) {
        GatewayPaymentStatus payment = fetchStatus(providerReference);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(payment.amount()) > 0
                || idempotencyKey == null || idempotencyKey.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Invalid mock refund request");
        }
        String key = sha256(providerReference + ":" + idempotencyKey);
        return refunds.computeIfAbsent(key, ignored -> new GatewayRefund("mock_ref_" + key.substring(0, 32), "PENDING"));
    }

    /** Used only by the dev/test simulator to produce provider-authentic signed webhook bodies. */
    public String signedEvent(String eventId, String providerReference, BigDecimal amount, String currency,
                              String status, String providerRefundReference, String tenantId) throws Exception {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("Tenant metadata is required for signed mock events");
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("providerReference", providerReference);
        payload.put("amount", amount.toPlainString());
        payload.put("currency", currency);
        payload.put("status", status);
        GatewayPaymentStatus current = payments.get(providerReference);
        if (current != null) {
            String paymentStatus = switch (status) {
                case "PAYMENT_CAPTURED" -> "CONFIRMED";
                case "PAYMENT_FAILED" -> "FAILED";
                case "PAYMENT_CANCELLED" -> "CANCELLED";
                default -> current.status();
            };
            payments.put(providerReference, new GatewayPaymentStatus(providerReference, paymentStatus,
                    current.amount(), current.currency()));
        }
        if (providerRefundReference != null) {
            String refundStatus = "REFUND_PROCESSED".equals(status) ? "PROCESSED"
                    : "REFUND_FAILED".equals(status) ? "FAILED" : "PENDING";
            refunds.replaceAll((key, value) -> value.providerRefundReference().equals(providerRefundReference)
                    ? new GatewayRefund(providerRefundReference, refundStatus) : value);
            payload.put("providerRefundReference", providerRefundReference);
        }
        payload.put("tenantId", tenantId);
        return mapper.writeValueAsString(payload);
    }

    private String tenantFor(String providerReference) {
        String tenant = tenants.get(providerReference);
        if (tenant == null) throw new IllegalArgumentException("Unknown mock payment reference");
        return tenant;
    }

    public String checkoutOutcome(String providerReference) {
        String outcome = outcomes.get(providerReference);
        if (outcome == null) throw new IllegalArgumentException("Unknown mock payment reference");
        return outcome;
    }

    public GatewayRefund refundStatus(String providerRefundReference) {
        return refunds.values().stream()
                .filter(refund -> refund.providerRefundReference().equals(providerRefundReference))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown mock refund reference"));
    }

    public String sign(String rawPayload) {
        return HexFormat.of().formatHex(hmac(rawPayload));
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign mock provider event", ex);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing mock event field: " + field);
        }
        return value.asText();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
