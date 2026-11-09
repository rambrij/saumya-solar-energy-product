package com.saumyasolar;

import com.saumyasolar.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProviderAdapterRegistryTest {
    @Test
    void emptyPaymentRegistryFailsClosedUntilProviderAdapterIsInstalled() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of());
        assertTrue(registry.providers().isEmpty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> registry.require("RAZORPAY"));
        assertEquals(503, ex.getStatusCode().value());
    }
    
    @Test
    void emptyAccountingRegistryReportsNoInstalledProviders() {
        AccountingAdapterRegistry registry = new AccountingAdapterRegistry(List.of());
        assertTrue(registry.providers().isEmpty());
        assertTrue(registry.find("ZOHO_BOOKS").isEmpty());
    }
    
    @Test
    void rejectsDuplicatePaymentProviderCodes() {
        PaymentGatewayAdapter one = adapter("provider-x"), two = adapter("PROVIDER-X");
        assertThrows(IllegalStateException.class, () -> new PaymentGatewayRegistry(List.of(one, two)));
    }
    
    private PaymentGatewayAdapter adapter(String code) {
        return new PaymentGatewayAdapter() {
            public String providerCode() {
                return code;
            }
            
            public GatewayPayment createPayment(BigDecimal a, String c, String r, Map<String, String> m) {
                return null;
            }
            
            public boolean verifyWebhook(String p, String s) {
                return false;
            }
            
            public GatewayWebhookEvent parseWebhook(String p) {
                return null;
            }
            
            public GatewayPaymentStatus fetchStatus(String r) {
                return null;
            }
            
            public GatewayRefund refund(String r, BigDecimal a, String k, String reason) {
                return null;
            }
        };
    }
}
