package com.saumyasolar.service;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentGatewayCheckoutService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final CustomerProfileRepository customers;
    private final CustomerOrderRepository orders;
    private final IntegrationConnectionRepository connections;
    private final PaymentGatewayRegistry adapters;
    private final EnterpriseSaasService entitlements;
    private final AuditEventRepository audits;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final boolean mockEnabled;
    
    public PaymentGatewayCheckoutService(
            InvoiceRepository invoices, PaymentRepository payments, RefundRepository refunds,
            CustomerProfileRepository customers, CustomerOrderRepository orders,
            IntegrationConnectionRepository connections, PaymentGatewayRegistry adapters,
            EnterpriseSaasService entitlements, AuditEventRepository audits,
            com.fasterxml.jackson.databind.ObjectMapper mapper
    ) {
        this(invoices, payments, refunds, customers, orders, connections, adapters, entitlements, audits, mapper, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentGatewayCheckoutService(
            InvoiceRepository invoices,
            PaymentRepository payments,
            RefundRepository refunds,
            CustomerProfileRepository customers,
            CustomerOrderRepository orders,
            IntegrationConnectionRepository connections,
            PaymentGatewayRegistry adapters,
            EnterpriseSaasService entitlements,
            AuditEventRepository audits,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            @org.springframework.beans.factory.annotation.Value("${app.payment.mock.enabled:false}") boolean mockEnabled
    ) {
        this.invoices = invoices;
        this.payments = payments;
        this.refunds = refunds;
        this.customers = customers;
        this.orders = orders;
        this.connections = connections;
        this.adapters = adapters;
        this.entitlements = entitlements;
        this.audits = audits;
        this.mapper = mapper;
        this.mockEnabled = mockEnabled;
    }
    
    public Map<String, Object> availability(Principal actor) {
        if (actor == null || actor.tenantId() == null || !Set.of("VENDOR", "CUSTOMER").contains(actor.accountType()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        List<String> available = connections.findByTenantIdAndIntegrationTypeAndStatus(actor.tenantId(), "PAYMENT_GATEWAY", "ACTIVE")
                .stream().map(IntegrationConnection::getProviderCode).filter(code -> adapters.find(code).isPresent()).distinct().toList();
        if (mockEnabled && adapters.find("MOCK_GATEWAY").isPresent()) {
            available = new java.util.ArrayList<>(available);
            available.add("MOCK_GATEWAY");
        }
        return Map.of("available", !available.isEmpty(), "providers", available);
    }
    
    @Transactional
    public CheckoutResult create(UUID invoiceId, String providerCode, String idempotencyKey, Principal actor) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,160}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 8 to 160 safe characters");
        Invoice invoice = invoices.findForUpdate(invoiceId, actor.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        authorizeInvoice(invoice, actor);
        if (!Set.of("ISSUED", "PARTIALLY_PAID").contains(invoice.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice is not open for online payment");
        entitlements.assertFeatureEnabled(actor.tenantId(), "paymentGateway");
        List<IntegrationConnection> active = connections.findByTenantIdAndIntegrationTypeAndStatus(actor.tenantId(), "PAYMENT_GATEWAY", "ACTIVE");
        String selectedProvider;
        boolean useMock = mockEnabled && adapters.find("MOCK_GATEWAY").isPresent()
                && (providerCode == null || providerCode.isBlank() || "MOCK_GATEWAY".equalsIgnoreCase(providerCode));
        if (useMock) {
            selectedProvider = "MOCK_GATEWAY";
        } else {
            if (active.isEmpty())
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active payment gateway connection is configured for this tenant");
            if (providerCode == null || providerCode.isBlank()) {
                if (active.size() != 1)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerCode is required when multiple payment gateways are configured");
                selectedProvider = active.get(0).getProviderCode();
            } else {
                selectedProvider = active.stream().map(IntegrationConnection::getProviderCode)
                        .filter(code -> code.equalsIgnoreCase(providerCode.trim())).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active payment gateway connection not found"));
            }
        }
        Optional<Payment> previous = payments.findByTenantIdAndIdempotencyKey(actor.tenantId(), idempotencyKey);
        if (previous.isPresent()) {
            Payment p = previous.get();
            if (!invoiceId.equals(p.getInvoiceId()) || !selectedProvider.equalsIgnoreCase(p.getProvider()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used for another payment");
            return view(p);
        }
        BigDecimal outstanding = invoice.getGrandTotal().subtract(netPaid(actor.tenantId(), invoiceId));
        if (outstanding.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice has no outstanding balance");
        PaymentGatewayAdapter adapter = adapters.require(selectedProvider);
        UUID paymentId = UUID.nameUUIDFromBytes(( actor.tenantId() + ":" + idempotencyKey ).getBytes(StandardCharsets.UTF_8));
        Map<String, String> metadata = Map.of(
                "tenantId", actor.tenantId().toString(), "invoiceId", invoiceId.toString(),
                "paymentId", paymentId.toString(), "idempotencyKey", idempotencyKey
        );
        PaymentGatewayAdapter.GatewayPayment gateway;
        try {
            gateway = adapter.createPayment(outstanding, invoice.getCurrency(), paymentId.toString(), metadata);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider could not create checkout");
        }
        boolean localMockCheckout = "MOCK_GATEWAY".equalsIgnoreCase(selectedProvider) && mockEnabled
                && gateway != null && gateway.checkoutUrl() != null
                && (gateway.checkoutUrl().startsWith("http://localhost:")
                    || gateway.checkoutUrl().startsWith("http://127.0.0.1:"));
        if (gateway == null || gateway.providerReference() == null || gateway.providerReference().isBlank()
                || gateway.checkoutUrl() == null || (!gateway.checkoutUrl().startsWith("https://") && !localMockCheckout))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid checkout response");
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setTenantId(actor.tenantId());
        payment.setInvoiceId(invoiceId);
        payment.setOrderId(invoice.getOrderId());
        payment.setAmount(outstanding);
        payment.setCurrency(invoice.getCurrency());
        payment.setMethod("ONLINE");
        payment.setSource("GATEWAY");
        payment.setStatus("PENDING");
        payment.setProvider(selectedProvider);
        payment.setProviderReference(gateway.providerReference());
        payment.setCheckoutUrl(gateway.checkoutUrl());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCreatedAt(Instant.now());
        payment.setRecordedBy(actor.userId());
        payments.save(payment);
        AuditEvent audit = new AuditEvent();
        audit.setId(UUID.randomUUID());
        audit.setTenantId(actor.tenantId());
        audit.setActorId(actor.userId());
        audit.setActorType(actor.accountType());
        audit.setAction("PAYMENT_GATEWAY_CHECKOUT_CREATED");
        audit.setEntityType("PAYMENT");
        audit.setEntityId(paymentId.toString());
        audit.setOutcome("SUCCESS");
        try {
            audit.setAfterJson(mapper.writeValueAsString(Map.of(
                    "providerCode", selectedProvider,
                    "invoiceId", invoiceId.toString(), "amount", outstanding.toPlainString(),
                    "currency", invoice.getCurrency()
            )));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payment audit event", ex);
        }
        audit.setCreatedAt(Instant.now());
        audits.save(audit);
        return view(payment);
    }
    
    private void authorizeInvoice(Invoice invoice, Principal actor) {
        if ("VENDOR".equals(actor.accountType())) {
            if (actor.permissions() == null || !actor.permissions().contains("PAYMENT_RECORD"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Finance permission required");
            entitlements.assertBranchAccess(invoice.getBranchId(), actor);
            return;
        }
        if (!"CUSTOMER".equals(actor.accountType())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        CustomerProfile customer = customers.findByTenantIdAndUserId(actor.tenantId(), actor.userId()).filter(CustomerProfile::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        CustomerOrder order = orders.findByIdAndTenantId(invoice.getOrderId(), actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (!customer.getId().equals(order.getCustomerId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
    }
    
    private BigDecimal netPaid(UUID tenantId, UUID invoiceId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payment payment : payments.findByTenantIdAndInvoiceId(tenantId, invoiceId)) {
            if (!Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED").contains(payment.getStatus())) continue;
            BigDecimal refunded = refunds.findByTenantIdAndPaymentId(tenantId, payment.getId()).stream()
                    .filter(r -> Set.of("RECORDED", "PROCESSED").contains(r.getStatus())).map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(payment.getAmount().subtract(refunded));
        }
        return total;
    }
    
    private CheckoutResult view(Payment p) {
        return new CheckoutResult(p.getId(), p.getProvider(), p.getProviderReference(), p.getCheckoutUrl(), p.getAmount(), p.getCurrency(), p.getStatus());
    }
    
    public record CheckoutResult(
            UUID paymentId, String providerCode, String providerReference, String checkoutUrl, BigDecimal amount,
            String currency, String status
    ) {
    }
}
