package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentGatewayCheckoutServiceTest {
    @Test
    void refusesCheckoutWhenNoProviderAdapterIsInstalled() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTenantId(tenant);
        invoice.setOrderId(UUID.randomUUID());
        invoice.setStatus("ISSUED");
        invoice.setGrandTotal(new BigDecimal("1250.00"));
        invoice.setCurrency("INR");
        IntegrationConnection connection = new IntegrationConnection();
        connection.setId(UUID.randomUUID());
        connection.setTenantId(tenant);
        connection.setProviderCode("UNAVAILABLE_PROVIDER");
        connection.setIntegrationType("PAYMENT_GATEWAY");
        connection.setStatus("ACTIVE");
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        when(invoices.findForUpdate(invoiceId, tenant)).thenReturn(Optional.of(invoice));
        IntegrationConnectionRepository connections = mock(IntegrationConnectionRepository.class);
        when(connections.findByTenantIdAndIntegrationTypeAndStatus(tenant, "PAYMENT_GATEWAY", "ACTIVE")).thenReturn(List.of(connection));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndIdempotencyKey(tenant, "checkout-key-123")).thenReturn(Optional.empty());
        when(payments.findByTenantIdAndInvoiceId(tenant, invoiceId)).thenReturn(List.of());
        EnterpriseSaasService entitlements = mock(EnterpriseSaasService.class);
        PaymentGatewayCheckoutService service = new PaymentGatewayCheckoutService(
                invoices, payments, mock(RefundRepository.class),
                mock(CustomerProfileRepository.class), mock(CustomerOrderRepository.class), connections,
                new PaymentGatewayRegistry(List.of()), entitlements, mock(AuditEventRepository.class), new ObjectMapper()
        );
        Principal actor = new Principal(user, tenant, "vendor@example.test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("FINANCE_MANAGE"), 0);
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.create(invoiceId, "UNAVAILABLE_PROVIDER", "checkout-key-123", actor)
        );
        assertEquals(503, ex.getStatusCode().value());
        verify(payments, never()).save(any());
    }
}
