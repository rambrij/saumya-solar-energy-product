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
    private final Map<String, GatewayPaymentStatus> payments = new ConcurrentHashMap<>();
    private final Map<String, GatewayRefund> refunds = new ConcurrentHashMap<>();

    public MockPaymentGatewayAdapter(
            ObjectMapper mapper,
            @Value("${app.payment.mock.signing-secret:}") String signingSecret
    ) {
        this.mapper = mapper;
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalStateException("app.payment.mock.signing-secret must contain at least 32 characters");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
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
                || metadata == null || metadata.get("idempotencyKey") == null) {
            throw new IllegalArgumentException("Mock checkout requires amount, currency, reference and idempotency metadata");
        }
        String reference = "mock_pay_" + sha256(metadata.get("tenantId") + ":" + metadata.get("idempotencyKey")).substring(0, 32);
        GatewayPaymentStatus status = new GatewayPaymentStatus(reference, "PENDING", amount, currency);
        payments.putIfAbsent(reference, status);
        return new GatewayPayment(reference, "https://mock-checkout.saumyasolar.invalid/session/" + reference, "PENDING");
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
                requiredText(json, "status")
        );
    }

    @Override
    public GatewayPaymentStatus fetchStatus(String providerReference) {
        GatewayPaymentStatus status = payments.get(providerReference);
        if (status == null) throw new IllegalArgumentException("Unknown mock payment reference");
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

    /** Used only by the dev/test simulator to produce a provider-authentic signed webhook body. */
    public String signedEvent(String eventId, String providerReference, BigDecimal amount, String currency, String status)
            throws Exception {
        String payload = mapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "providerReference", providerReference,
                "amount", amount.toPlainString(),
                "currency", currency,
                "status", status
        ));
        return payload;
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
