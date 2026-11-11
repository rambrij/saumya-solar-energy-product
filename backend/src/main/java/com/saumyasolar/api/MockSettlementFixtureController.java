package com.saumyasolar.api;

import com.saumyasolar.domain.Payment;
import com.saumyasolar.repo.PaymentRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PaymentReconciliationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@RestController
@Profile({"dev", "test"})
@RequestMapping("/api/v1/payment-gateways/mock")
public class MockSettlementFixtureController {
    private final PaymentRepository payments;
    private final PaymentReconciliationService reconciliation;

    public MockSettlementFixtureController(PaymentRepository payments, PaymentReconciliationService reconciliation) {
        this.payments = payments;
        this.reconciliation = reconciliation;
    }

    @PostMapping("/settlement-fixtures")
    public Object createFixture(@RequestBody FixtureRequest request, Authentication authentication) {
        Principal actor = (Principal) authentication.getPrincipal();
        if (actor == null || !"VENDOR".equals(actor.accountType()) || actor.permissions() == null
                || !actor.permissions().contains("PAYMENT_VIEW"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String reportRef = request.reportReference() == null || request.reportReference().isBlank()
                ? "mock_settlement_" + UUID.randomUUID() : request.reportReference();
        List<Payment> mockPayments = payments.findByTenantIdOrderByCreatedAtDesc(actor.tenantId()).stream()
                .filter(p -> "MOCK_GATEWAY".equals(p.getProvider())).toList();
        List<PaymentReconciliationService.SettlementLine> rows = new ArrayList<>();
        if (!mockPayments.isEmpty()) {
            Payment first = mockPayments.get(0);
            rows.add(new PaymentReconciliationService.SettlementLine(first.getProviderReference(), first.getAmount(), first.getCurrency()));
            rows.add(new PaymentReconciliationService.SettlementLine(first.getProviderReference(), first.getAmount(), first.getCurrency()));
        }
        if (mockPayments.size() > 1) {
            Payment second = mockPayments.get(1);
            rows.add(new PaymentReconciliationService.SettlementLine(second.getProviderReference(),
                    second.getAmount().add(new BigDecimal("0.01")), second.getCurrency()));
        }
        if (mockPayments.size() > 2) {
            Payment third = mockPayments.get(2);
            String wrongCurrency = "INR".equals(third.getCurrency()) ? "USD" : "INR";
            rows.add(new PaymentReconciliationService.SettlementLine(third.getProviderReference(), third.getAmount(), wrongCurrency));
        }
        rows.add(new PaymentReconciliationService.SettlementLine("mock_missing_" + reportRef,
                new BigDecimal("1.00"), "INR"));
        return reconciliation.reconcile("MOCK_GATEWAY", reportRef, "INR", rows, actor);
    }

    public record FixtureRequest(String reportReference) { }
}
