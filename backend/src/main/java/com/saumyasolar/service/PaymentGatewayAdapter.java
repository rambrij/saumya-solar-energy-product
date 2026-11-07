package com.saumyasolar.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Provider-neutral gateway port. Implementations are registered only after the business
 * selects a gateway and provisions its credentials. Browser redirects never confirm payment.
 */
public interface PaymentGatewayAdapter {
    String providerCode();
    GatewayPayment createPayment(BigDecimal amount, String currency, String internalReference, Map<String,String> metadata);
    boolean verifyWebhook(String rawPayload, String signature);
    GatewayPaymentStatus fetchStatus(String providerReference);
    GatewayRefund refund(String providerReference, BigDecimal amount, String idempotencyKey, String reason);

    record GatewayPayment(String providerReference, String checkoutUrl, String status) {}
    record GatewayPaymentStatus(String providerReference, String status, BigDecimal amount, String currency) {}
    record GatewayRefund(String providerRefundReference, String status) {}
}
