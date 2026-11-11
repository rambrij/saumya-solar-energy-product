package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.service.MockPaymentGatewayAdapter;
import com.saumyasolar.service.PaymentGatewayAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MockPaymentGatewayAdapterTest {
    private static final String SECRET = "unit-test-mock-signing-secret-at-least-32-characters";

    @Test
    void createsDeterministicPendingCheckoutAndSignsTenantBoundEvents() throws Exception {
        MockPaymentGatewayAdapter adapter = new MockPaymentGatewayAdapter(new ObjectMapper(), SECRET,
                "PENDING", "http://localhost:3000");
        Map<String, String> metadata = Map.of("tenantId", "tenant-a", "paymentId", "payment-a", "idempotencyKey", "checkout-key-001");
        PaymentGatewayAdapter.GatewayPayment first = adapter.createPayment(new BigDecimal("1200.00"), "INR", "payment-a", metadata);
        PaymentGatewayAdapter.GatewayPayment retry = adapter.createPayment(new BigDecimal("1200.00"), "INR", "payment-a", metadata);
        assertEquals(first.providerReference(), retry.providerReference());
        assertEquals(first.checkoutUrl(), retry.checkoutUrl());
        assertTrue(first.checkoutUrl().contains("mockCheckoutPaymentId=payment-a"));

        String raw = adapter.signedEvent("evt-1", first.providerReference(), new BigDecimal("1200.00"),
                "INR", "PAYMENT_CAPTURED", null, "tenant-a");
        assertTrue(adapter.verifyWebhook(raw, adapter.sign(raw)));
        assertFalse(adapter.verifyWebhook(raw, "0".repeat(64)));
        PaymentGatewayAdapter.GatewayWebhookEvent event = adapter.parseWebhook(raw);
        assertEquals("tenant-a", event.tenantId());
        assertEquals("PAYMENT_CAPTURED", event.status());
    }

    @Test
    void supportsConfiguredCheckoutOutcomesAndIdempotentRefundRequests() throws Exception {
        for (String outcome : new String[]{"SUCCESS", "FAILURE", "PENDING", "CANCELLED"}) {
            MockPaymentGatewayAdapter adapter = new MockPaymentGatewayAdapter(new ObjectMapper(), SECRET,
                    outcome, "http://localhost:3000");
            PaymentGatewayAdapter.GatewayPayment payment = adapter.createPayment(new BigDecimal("100.00"), "INR",
                    "payment", Map.of("tenantId", "tenant", "paymentId", "payment", "idempotencyKey", "key-12345678"));
            assertEquals(outcome, payment.status());
        }
        MockPaymentGatewayAdapter adapter = new MockPaymentGatewayAdapter(new ObjectMapper(), SECRET,
                "SUCCESS", "http://localhost:3000");
        PaymentGatewayAdapter.GatewayPayment payment = adapter.createPayment(new BigDecimal("100.00"), "INR",
                "payment", Map.of("tenantId", "tenant", "paymentId", "payment", "idempotencyKey", "key-12345678"));
        var first = adapter.refund(payment.providerReference(), new BigDecimal("25.00"), "refund-key-123", "Customer request");
        var retry = adapter.refund(payment.providerReference(), new BigDecimal("25.00"), "refund-key-123", "Customer request");
        assertEquals(first.providerRefundReference(), retry.providerRefundReference());
        assertEquals("PENDING", first.status());
    }

    @Test
    void mockComponentsAreRestrictedToDevelopmentAndTestProfiles() {
        assertArrayEquals(new String[]{"dev", "test"},
                MockPaymentGatewayAdapter.class.getAnnotation(Profile.class).value());
        assertArrayEquals(new String[]{"dev", "test"},
                com.saumyasolar.api.MockPaymentGatewaySimulatorController.class.getAnnotation(Profile.class).value());
        assertArrayEquals(new String[]{"dev", "test"},
                com.saumyasolar.api.MockSettlementFixtureController.class.getAnnotation(Profile.class).value());
    }
}
