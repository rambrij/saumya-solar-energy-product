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
        Principal actor = new Principal(user, tenant, "vendor@example.test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("PAYMENT_RECORD"), 0);
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.create(invoiceId, "UNAVAILABLE_PROVIDER", "checkout-key-123", actor)
        );
        assertEquals(503, ex.getStatusCode().value());
        verify(payments, never()).save(any());
    }

    @Test
    void createsPendingCheckoutThroughMockAdapterOnlyWithoutContactingARealProvider() throws Exception {
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
        connection.setProviderCode("MOCK_GATEWAY");
        connection.setIntegrationType("PAYMENT_GATEWAY");
        connection.setStatus("ACTIVE");

        InvoiceRepository invoices = mock(InvoiceRepository.class);
        when(invoices.findForUpdate(invoiceId, tenant)).thenReturn(Optional.of(invoice));
        IntegrationConnectionRepository connections = mock(IntegrationConnectionRepository.class);
        when(connections.findByTenantIdAndIntegrationTypeAndStatus(tenant, "PAYMENT_GATEWAY", "ACTIVE"))
                .thenReturn(List.of(connection));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndIdempotencyKey(tenant, "adapter-only-key-123")).thenReturn(Optional.empty());
        when(payments.findByTenantIdAndInvoiceId(tenant, invoiceId)).thenReturn(List.of());
        EnterpriseSaasService entitlements = mock(EnterpriseSaasService.class);
        PaymentGatewayAdapter adapter = mock(PaymentGatewayAdapter.class);
        when(adapter.providerCode()).thenReturn("MOCK_GATEWAY");
        when(adapter.createPayment(any(BigDecimal.class), eq("INR"), anyString(), anyMap()))
                .thenReturn(new PaymentGatewayAdapter.GatewayPayment(
                        "mock-provider-reference", "https://checkout.example.test/mock-session", "PENDING"));

        PaymentGatewayCheckoutService service = new PaymentGatewayCheckoutService(
                invoices, payments, mock(RefundRepository.class),
                mock(CustomerProfileRepository.class), mock(CustomerOrderRepository.class), connections,
                new PaymentGatewayRegistry(List.of(adapter)), entitlements, mock(AuditEventRepository.class),
                new ObjectMapper()
        );
        Principal actor = new Principal(user, tenant, "vendor@example.test", "VENDOR",
                Set.of("VENDOR_ADMIN"), Set.of("PAYMENT_RECORD"), 0);

        PaymentGatewayCheckoutService.CheckoutResult result =
                service.create(invoiceId, "MOCK_GATEWAY", "adapter-only-key-123", actor);

        assertEquals("PENDING", result.status());
        assertEquals("MOCK_GATEWAY", result.providerCode());
        assertEquals("mock-provider-reference", result.providerReference());
        verify(adapter, times(1)).createPayment(any(BigDecimal.class), eq("INR"), anyString(), anyMap());
        org.mockito.ArgumentCaptor<Payment> saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertEquals("PENDING", saved.getValue().getStatus());
        assertEquals("GATEWAY", saved.getValue().getSource());
        assertEquals(tenant, saved.getValue().getTenantId());
        verifyNoMoreInteractions(adapter);
    }
}
