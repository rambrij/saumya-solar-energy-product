package com.saumyasolar.api;

import com.saumyasolar.domain.Payment;
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
@RequestMapping("/api/v1/finance/reconciliations")
public class PaymentReconciliationController {
    private final PaymentReconciliationService reconciliation;
    public PaymentReconciliationController(PaymentReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping
    public Object reconcile(@RequestBody ReconciliationRequest request, Authentication authentication) {
        Principal actor = (Principal) authentication.getPrincipal();
        List<PaymentReconciliationService.SettlementLine> rows = request.lines() == null ? null
                : request.lines().stream().map(x -> new PaymentReconciliationService.SettlementLine(
                        x.providerReference(), x.amount(), x.currency())).toList();
        return reconciliation.reconcile(request.provider(), request.reportReference(), request.currency(), rows, actor);
    }

    @GetMapping
    public Object list(Authentication authentication) {
        return reconciliation.list((Principal) authentication.getPrincipal());
    }

    @GetMapping("/{reportId}/lines")
    public Object lines(@PathVariable UUID reportId, Authentication authentication) {
        return reconciliation.lines(reportId, (Principal) authentication.getPrincipal());
    }

    public record ReconciliationRequest(String provider, String reportReference, String currency,
            List<SettlementLineRequest> lines) { }
    public record SettlementLineRequest(String providerReference, BigDecimal amount, String currency) { }
}
