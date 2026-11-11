package com.saumyasolar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentReconciliationService {
    private final PaymentReconciliationReportRepository reports;
    private final PaymentReconciliationLineRepository lines;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final ObjectMapper mapper;
    private final EnterpriseSaasService entitlements;

    public PaymentReconciliationService(PaymentReconciliationReportRepository reports,
            PaymentReconciliationLineRepository lines, PaymentRepository payments,
            InvoiceRepository invoices, ObjectMapper mapper, EnterpriseSaasService entitlements) {
        this.reports = reports;
        this.lines = lines;
        this.payments = payments;
        this.invoices = invoices;
        this.mapper = mapper;
        this.entitlements = entitlements;
    }

    @Transactional
    public PaymentReconciliationReport reconcile(String provider, String reportReference, String currency,
            List<SettlementLine> input, Principal actor) {
        if (actor == null || !"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("PAYMENT_VIEW"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment view permission required");
        if (provider == null || !provider.matches("[A-Za-z0-9_-]{2,60}")
                || reportReference == null || !reportReference.matches("[A-Za-z0-9._:-]{1,160}")
                || currency == null || !currency.matches("[A-Z]{3}") || input == null || input.isEmpty() || input.size() > 10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid settlement report");
        for (SettlementLine line : input) {
            if (line.providerReference() == null || line.providerReference().isBlank() || line.providerReference().length() > 150
                    || line.amount() == null || line.amount().signum() <= 0 || line.currency() == null
                    || !line.currency().matches("[A-Z]{3}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement line is invalid");
        }
        String code = provider.toUpperCase(Locale.ROOT);
        String payloadHash = hash(provider + "|" + reportReference + "|" + currency + "|" + input.toString());
        Optional<PaymentReconciliationReport> previous = reports.findByTenantIdAndProviderAndReportReference(
                actor.tenantId(), code, reportReference);
        if (previous.isPresent()) {
            if (!previous.get().getPayloadHash().equals(payloadHash))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Report reference was already processed with different content");
            return previous.get();
        }

        PaymentReconciliationReport report = new PaymentReconciliationReport();
        report.setId(UUID.randomUUID());
        report.setTenantId(actor.tenantId());
        report.setProvider(code);
        report.setReportReference(reportReference);
        report.setCurrency(currency);
        report.setReportedTotal(input.stream().map(SettlementLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setMatchedTotal(BigDecimal.ZERO);
        report.setMismatchCount(0);
        report.setStatus("PROCESSING");
        report.setPayloadHash(payloadHash);
        report.setCreatedAt(Instant.now());
        reports.saveAndFlush(report);

        Set<String> seen = new HashSet<>();
        BigDecimal matchedTotal = BigDecimal.ZERO;
        int mismatches = 0;
        for (SettlementLine row : input) {
            Payment payment = payments.findByTenantIdAndProviderAndProviderReference(
                    actor.tenantId(), code, row.providerReference()).orElse(null);
            String outcome;
            String details = null;
            if (!seen.add(row.providerReference())) {
                outcome = "DUPLICATE";
                details = "Provider reference occurs more than once in this report";
            } else if (payment == null || !visibleToActor(payment, actor)) {
                outcome = "MISSING_PAYMENT";
                details = "No tenant/branch-visible payment matches this provider reference";
            } else if (!payment.getCurrency().equalsIgnoreCase(row.currency())
                    || !currency.equalsIgnoreCase(row.currency())) {
                outcome = "CURRENCY_MISMATCH";
                details = "Settlement currency does not match the recorded payment/report";
            } else if (payment.getAmount().compareTo(row.amount()) != 0) {
                outcome = "AMOUNT_MISMATCH";
                details = "Settlement amount does not match the recorded payment";
            } else if (!Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED").contains(payment.getStatus())) {
                outcome = "PAYMENT_NOT_CAPTURED";
                details = "Payment is not in a captured state";
            } else {
                outcome = "MATCHED";
                matchedTotal = matchedTotal.add(row.amount());
            }
            if (!"MATCHED".equals(outcome)) mismatches++;
            PaymentReconciliationLine result = new PaymentReconciliationLine();
            result.setId(UUID.randomUUID());
            result.setTenantId(actor.tenantId());
            result.setReportId(report.getId());
            result.setProviderReference(row.providerReference());
            result.setAmount(row.amount());
            result.setCurrency(row.currency());
            result.setOutcome(outcome);
            result.setPaymentId(payment != null && visibleToActor(payment, actor) ? payment.getId() : null);
            result.setDetails(details);
            lines.save(result);
        }
        report.setMatchedTotal(matchedTotal);
        report.setMismatchCount(mismatches);
        report.setStatus(mismatches == 0 ? "RECONCILED" : "MISMATCHED");
        report.setProcessedAt(Instant.now());
        return reports.save(report);
    }

    public List<PaymentReconciliationReport> list(Principal actor) {
        if (actor == null || !"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("PAYMENT_VIEW"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return reports.findByTenantIdOrderByCreatedAtDesc(actor.tenantId());
    }

    public List<PaymentReconciliationLine> lines(UUID reportId, Principal actor) {
        PaymentReconciliationReport report = reports.findById(reportId)
                .filter(r -> r.getTenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation report not found"));
        if (!"VENDOR".equals(actor.accountType()) || actor.permissions() == null
                || !actor.permissions().contains("PAYMENT_VIEW"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return lines.findByTenantIdAndReportIdOrderByProviderReference(actor.tenantId(), report.getId());
    }

    private boolean visibleToActor(Payment payment, Principal actor) {
        if (entitlements == null) return true;
        Invoice invoice = invoices.findByIdAndTenantId(payment.getInvoiceId(), actor.tenantId()).orElse(null);
        if (invoice == null) return false;
        try { entitlements.assertBranchAccess(invoice.getBranchId(), actor); return true; }
        catch (ResponseStatusException ex) { return false; }
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public record SettlementLine(String providerReference, BigDecimal amount, String currency) { }
}
