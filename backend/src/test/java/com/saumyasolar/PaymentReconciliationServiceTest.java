package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PaymentReconciliationService;
import com.saumyasolar.service.EnterpriseSaasService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentReconciliationServiceTest {
    @Test
    void classifiesMatchedMissingAmountCurrencyAndDuplicateRowsAndReplaysIdempotently() {
        UUID tenant = UUID.randomUUID();
        Payment p1 = payment(tenant, "p1", "100.00", "INR");
        Payment p2 = payment(tenant, "p2", "200.00", "INR");
        Payment p3 = payment(tenant, "p3", "300.00", "INR");
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "p1")).thenReturn(Optional.of(p1));
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "p2")).thenReturn(Optional.of(p2));
        when(payments.findByTenantIdAndProviderAndProviderReference(tenant, "MOCK_GATEWAY", "p3")).thenReturn(Optional.of(p3));
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        when(invoices.findByIdAndTenantId(any(), eq(tenant))).thenAnswer(inv -> Optional.of(invoice(tenant)));
        PaymentReconciliationReportRepository reports = mock(PaymentReconciliationReportRepository.class);
        when(reports.findByTenantIdAndProviderAndReportReference(tenant, "MOCK_GATEWAY", "report-1")).thenReturn(Optional.empty());
        when(reports.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PaymentReconciliationLineRepository lines = mock(PaymentReconciliationLineRepository.class);
        when(lines.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PaymentReconciliationService service = new PaymentReconciliationService(reports, lines, payments, invoices,
                new ObjectMapper(), mock(EnterpriseSaasService.class));
        Principal actor = new Principal(UUID.randomUUID(), tenant, "finance@example.test", "VENDOR",
                Set.of("ACCOUNTANT"), Set.of("PAYMENT_VIEW"), 0);
        List<PaymentReconciliationService.SettlementLine> input = List.of(
                new PaymentReconciliationService.SettlementLine("p1", new BigDecimal("100.00"), "INR"),
                new PaymentReconciliationService.SettlementLine("p1", new BigDecimal("100.00"), "INR"),
                new PaymentReconciliationService.SettlementLine("p2", new BigDecimal("201.00"), "INR"),
                new PaymentReconciliationService.SettlementLine("p3", new BigDecimal("300.00"), "USD"),
                new PaymentReconciliationService.SettlementLine("missing", new BigDecimal("1.00"), "INR")
        );

        PaymentReconciliationReport report = service.reconcile("MOCK_GATEWAY", "report-1", "INR", input, actor);

        assertEquals("MISMATCHED", report.getStatus());
        assertEquals(4, report.getMismatchCount());
        assertEquals(new BigDecimal("100.00"), report.getMatchedTotal());
        verify(lines, times(5)).save(any());
        when(reports.findByTenantIdAndProviderAndReportReference(tenant, "MOCK_GATEWAY", "report-1"))
                .thenReturn(Optional.of(report));
        PaymentReconciliationReport replay = service.reconcile("MOCK_GATEWAY", "report-1", "INR", input, actor);
        assertEquals(report.getId(), replay.getId());
        verify(lines, times(5)).save(any());
        List<PaymentReconciliationService.SettlementLine> changed = new ArrayList<>(input);
        changed.set(0, new PaymentReconciliationService.SettlementLine("p1", new BigDecimal("101.00"), "INR"));
        assertThrows(ResponseStatusException.class, () -> service.reconcile("MOCK_GATEWAY", "report-1", "INR", changed, actor));

    }

    @Test
    void rejectsCrossTenantReportAccessAndInvalidInput() {
        PaymentReconciliationService service = new PaymentReconciliationService(
                mock(PaymentReconciliationReportRepository.class), mock(PaymentReconciliationLineRepository.class),
                mock(PaymentRepository.class), mock(InvoiceRepository.class), new ObjectMapper(), mock(EnterpriseSaasService.class));
        Principal customer = new Principal(UUID.randomUUID(), UUID.randomUUID(), "customer@example.test", "CUSTOMER", Set.of(), Set.of(), 0);
        assertThrows(ResponseStatusException.class, () -> service.reconcile("MOCK_GATEWAY", "report", "INR", List.of(), customer));
    }

    private Payment payment(UUID tenant, String reference, String amount, String currency) {
        Payment p = new Payment(); p.setId(UUID.randomUUID()); p.setTenantId(tenant); p.setInvoiceId(UUID.randomUUID());
        p.setProvider("MOCK_GATEWAY"); p.setProviderReference(reference); p.setAmount(new BigDecimal(amount));
        p.setCurrency(currency); p.setStatus("CONFIRMED"); return p;
    }
    private Invoice invoice(UUID tenant) {
        Invoice i = new Invoice(); i.setId(UUID.randomUUID()); i.setTenantId(tenant); return i;
    }
}
