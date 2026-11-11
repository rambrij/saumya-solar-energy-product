package com.saumyasolar.service;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGatewayAdapter {
    String providerCode();
    GatewayPayment createPayment(BigDecimal amount, String currency, String internalReference, Map<String, String> metadata) throws Exception;
    boolean verifyWebhook(String rawPayload, String signature);
    GatewayWebhookEvent parseWebhook(String rawPayload) throws Exception;
    GatewayPaymentStatus fetchStatus(String providerReference) throws Exception;
    GatewayRefund refund(String providerReference, BigDecimal amount, String idempotencyKey, String reason) throws Exception;

    record GatewayPayment(String providerReference, String checkoutUrl, String status) { }
    record GatewayPaymentStatus(String providerReference, String status, BigDecimal amount, String currency) { }

    record GatewayWebhookEvent(String eventId, String providerReference, BigDecimal amount,
            String currency, String status, String providerRefundReference, String tenantId) {
        public GatewayWebhookEvent(String eventId, String providerReference, BigDecimal amount, String currency, String status) {
            this(eventId, providerReference, amount, currency, status, null, null);
        }
        public GatewayWebhookEvent(String eventId, String providerReference, BigDecimal amount, String currency,
                String status, String providerRefundReference) {
            this(eventId, providerReference, amount, currency, status, providerRefundReference, null);
        }
    }
    record GatewayRefund(String providerRefundReference, String status) { }
}
