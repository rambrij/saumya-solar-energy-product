package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentGatewayWebhookServiceTest {
    @Test
    void capturesPaymentOnlyAfterValidSignedTenantBoundEvent() throws Exception {
        UUID tenant = UUID.randomUUID(), paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
        Payment payment = payment(tenant, paymentId, invoiceId);
        Invoice invoice = invoice(tenant, invoiceId);
        PaymentGatewayAdapter adapter = mock(PaymentGatewayAdapter.class);
        when(adapter.providerCode()).thenReturn("MOCK_GATEWAY");
        when(adapter.verifyWebhook("signed-payload", "signature")).thenReturn(true);
        when(adapter.parseWebhook("signed-payload")).thenReturn(new PaymentGatewayAdapter.GatewayWebhookEvent(
                "evt-capture", "provider-ref", new BigDecimal("100.00"), "INR", "PAYMENT_CAPTURED", null, tenant.toString()));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "provider-ref")).thenReturn(Optional.of(payment));
        when(payments.findByTenantIdAndInvoiceId(tenant, invoiceId)).thenReturn(List.of(payment));
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        when(invoices.findForUpdate(invoiceId, tenant)).thenReturn(Optional.of(invoice));
        RefundRepository refunds = mock(RefundRepository.class);
        when(refunds.findByTenantIdAndPaymentId(tenant, paymentId)).thenReturn(List.of());
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        when(events.findByTenantIdAndProviderAndEventId(tenant, "MOCK_GATEWAY", "evt-capture")).thenReturn(Optional.empty());
        PaymentGatewayWebhookService service = new PaymentGatewayWebhookService(
                new PaymentGatewayRegistry(List.of(adapter)), payments, events, invoices, refunds,
                mock(AuditEventRepository.class), new ObjectMapper());

        Map<String, Object> result = service.accept("MOCK_GATEWAY", "signed-payload", "signature");

        assertEquals(true, result.get("accepted"));
        assertEquals("CONFIRMED", payment.getStatus());
        assertEquals("PAID", invoice.getStatus());
        verify(events).saveAndFlush(argThat(e -> tenant.equals(e.getTenantId())));
    }

    @Test
    void returnsDuplicateForPreviouslyProcessedTenantBoundEvent() throws Exception {
        UUID tenant = UUID.randomUUID(), paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
        Payment payment = payment(tenant, paymentId, invoiceId);
        PaymentWebhookEvent prior = new PaymentWebhookEvent();
        prior.setId(UUID.randomUUID()); prior.setTenantId(tenant); prior.setProvider("MOCK_GATEWAY");
        prior.setEventId("evt-duplicate"); prior.setProcessingStatus("PROCESSED");
        PaymentGatewayAdapter adapter = mock(PaymentGatewayAdapter.class);
        when(adapter.providerCode()).thenReturn("MOCK_GATEWAY");
        when(adapter.verifyWebhook("payload", "sig")).thenReturn(true);
        when(adapter.parseWebhook("payload")).thenReturn(new PaymentGatewayAdapter.GatewayWebhookEvent(
                "evt-duplicate", "provider-ref", new BigDecimal("100.00"), "INR", "PAYMENT_CAPTURED", null, tenant.toString()));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "provider-ref")).thenReturn(Optional.of(payment));
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        when(events.findByTenantIdAndProviderAndEventId(tenant, "MOCK_GATEWAY", "evt-duplicate")).thenReturn(Optional.of(prior));
        PaymentGatewayWebhookService service = new PaymentGatewayWebhookService(
                new PaymentGatewayRegistry(List.of(adapter)), payments, events, mock(InvoiceRepository.class),
                mock(RefundRepository.class), mock(AuditEventRepository.class), new ObjectMapper());

        Map<String, Object> result = service.accept("MOCK_GATEWAY", "payload", "sig");

        assertEquals(true, result.get("duplicate"));
        verify(events, never()).saveAndFlush(any());
        verify(payments, never()).save(any());
    }

    @Test
    void rejectsInvalidSignatureWithoutConsumingEventId() {
        PaymentGatewayAdapter adapter = mock(PaymentGatewayAdapter.class);
        when(adapter.providerCode()).thenReturn("MOCK_GATEWAY");
        when(adapter.verifyWebhook(anyString(), anyString())).thenReturn(false);
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        PaymentGatewayWebhookService service = new PaymentGatewayWebhookService(
                new PaymentGatewayRegistry(List.of(adapter)), mock(PaymentRepository.class), events,
                mock(InvoiceRepository.class), mock(RefundRepository.class), mock(AuditEventRepository.class), new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> service.accept("MOCK_GATEWAY", "bad", "bad"));
        verifyNoInteractions(events);
    }

    @Test
    void rejectsAmountMismatchBeforePersistingIdempotencyRecord() throws Exception {
        UUID tenant = UUID.randomUUID(), paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
        Payment payment = payment(tenant, paymentId, invoiceId);
        PaymentGatewayAdapter adapter = mock(PaymentGatewayAdapter.class);
        when(adapter.providerCode()).thenReturn("MOCK_GATEWAY");
        when(adapter.verifyWebhook("payload", "sig")).thenReturn(true);
        when(adapter.parseWebhook("payload")).thenReturn(new PaymentGatewayAdapter.GatewayWebhookEvent(
                "evt-mismatch", "provider-ref", new BigDecimal("101.00"), "INR", "PAYMENT_CAPTURED", null, tenant.toString()));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "provider-ref")).thenReturn(Optional.of(payment));
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        PaymentGatewayWebhookService service = new PaymentGatewayWebhookService(
                new PaymentGatewayRegistry(List.of(adapter)), payments, events, mock(InvoiceRepository.class),
                mock(RefundRepository.class), mock(AuditEventRepository.class), new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> service.accept("MOCK_GATEWAY", "payload", "sig"));
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void processesRefundWebhookAndRecalculatesPaymentBalance() throws Exception {
        UUID tenant = UUID.randomUUID(), paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
        Payment payment = payment(tenant, paymentId, invoiceId);
        payment.setStatus("CONFIRMED");
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID()); refund.setTenantId(tenant); refund.setPaymentId(paymentId);
        refund.setAmount(new BigDecimal("25.00")); refund.setStatus("PENDING");
        refund.setProvider("MOCK_GATEWAY"); refund.setProviderRefundReference("refund-ref");
        PaymentGatewayAdapter adapter = mock(PaymentGatewayAdapter.class);
        when(adapter.providerCode()).thenReturn("MOCK_GATEWAY");
        when(adapter.verifyWebhook("refund-payload", "sig")).thenReturn(true);
        when(adapter.parseWebhook("refund-payload")).thenReturn(new PaymentGatewayAdapter.GatewayWebhookEvent(
                "evt-refund", "provider-ref", new BigDecimal("25.00"), "INR", "REFUND_PROCESSED",
                "refund-ref", tenant.toString()));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "provider-ref")).thenReturn(Optional.of(payment));
        when(payments.findByTenantIdAndInvoiceId(tenant, invoiceId)).thenReturn(List.of(payment));
        Invoice invoice = invoice(tenant, invoiceId);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        when(invoices.findForUpdate(invoiceId, tenant)).thenReturn(Optional.of(invoice));
        RefundRepository refunds = mock(RefundRepository.class);
        when(refunds.findByTenantIdAndProviderAndProviderRefundReference(tenant, "MOCK_GATEWAY", "refund-ref")).thenReturn(Optional.of(refund));
        when(refunds.findByTenantIdAndPaymentId(tenant, paymentId)).thenReturn(List.of(refund));
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        when(events.findByTenantIdAndProviderAndEventId(tenant, "MOCK_GATEWAY", "evt-refund")).thenReturn(Optional.empty());
        PaymentGatewayWebhookService service = new PaymentGatewayWebhookService(
                new PaymentGatewayRegistry(List.of(adapter)), payments, events, invoices, refunds,
                mock(AuditEventRepository.class), new ObjectMapper());

        service.accept("MOCK_GATEWAY", "refund-payload", "sig");

        assertEquals("PROCESSED", refund.getStatus());
        assertEquals("PARTIALLY_REFUNDED", payment.getStatus());
        assertEquals("PARTIALLY_PAID", invoice.getStatus());
    }

    private Payment payment(UUID tenant, UUID id, UUID invoiceId) {
        Payment payment = new Payment();
        payment.setId(id); payment.setTenantId(tenant); payment.setInvoiceId(invoiceId);
        payment.setAmount(new BigDecimal("100.00")); payment.setCurrency("INR");
        payment.setProvider("MOCK_GATEWAY"); payment.setProviderReference("provider-ref");
        payment.setStatus("PENDING"); payment.setSource("GATEWAY");
        return payment;
    }

    private Invoice invoice(UUID tenant, UUID id) {
        Invoice invoice = new Invoice();
        invoice.setId(id); invoice.setTenantId(tenant); invoice.setGrandTotal(new BigDecimal("100.00"));
        invoice.setStatus("ISSUED"); return invoice;
    }
}
