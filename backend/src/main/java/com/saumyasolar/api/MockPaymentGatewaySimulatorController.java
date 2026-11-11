package com.saumyasolar.api;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Development/test-only signed mock-provider event simulator. Not registered in production profiles. */
@RestController
@Profile({"dev", "test"})
@RequestMapping("/api/v1/payment-gateways/mock")
public class MockPaymentGatewaySimulatorController {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final CustomerProfileRepository customers;
    private final CustomerOrderRepository orders;
    private final PaymentGatewayRegistry gateways;
    private final PaymentGatewayWebhookService webhooks;

    public MockPaymentGatewaySimulatorController(PaymentRepository payments, RefundRepository refunds,
            CustomerProfileRepository customers, CustomerOrderRepository orders,
            PaymentGatewayRegistry gateways, PaymentGatewayWebhookService webhooks) {
        this.payments = payments;
        this.refunds = refunds;
        this.customers = customers;
        this.orders = orders;
        this.gateways = gateways;
        this.webhooks = webhooks;
    }

    @PostMapping("/payments/{paymentId}/complete")
    public Map<String, Object> completeCheckout(@PathVariable UUID paymentId,
            @RequestBody OutcomeRequest request, Authentication authentication) throws Exception {
        Principal actor = (Principal) authentication.getPrincipal();
        Payment payment = payments.findByIdAndTenantId(paymentId, actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        authorize(payment, actor);
        if (!"MOCK_GATEWAY".equals(payment.getProvider()) || !"GATEWAY".equals(payment.getSource()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only mock gateway payments can be simulated");
        String eventStatus = switch (request.outcome()) {
            case "SUCCESS" -> "PAYMENT_CAPTURED";
            case "FAILURE" -> "PAYMENT_FAILED";
            case "CANCELLED" -> "PAYMENT_CANCELLED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Outcome must be SUCCESS, FAILURE or CANCELLED");
        };
        MockPaymentGatewayAdapter adapter = (MockPaymentGatewayAdapter) gateways.require("MOCK_GATEWAY");
        String eventId = "mock_checkout_" + payment.getId() + "_" + eventStatus;
        String raw = adapter.signedEvent(eventId, payment.getProviderReference(), payment.getAmount(),
                payment.getCurrency(), eventStatus, null, payment.getTenantId().toString());
        return webhooks.accept("MOCK_GATEWAY", raw, adapter.sign(raw));
    }

    @PostMapping("/refunds/{refundId}/complete")
    public Map<String, Object> completeRefund(@PathVariable UUID refundId,
            @RequestBody OutcomeRequest request, Authentication authentication) throws Exception {
        Principal actor = (Principal) authentication.getPrincipal();
        Refund refund = refunds.findByIdAndTenantId(refundId, actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
        Payment payment = payments.findByIdAndTenantId(refund.getPaymentId(), actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        authorize(payment, actor);
        if (!"MOCK_GATEWAY".equals(refund.getProvider()) || refund.getProviderRefundReference() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only mock provider refunds can be simulated");
        String eventStatus = switch (request.outcome()) {
            case "SUCCESS" -> "REFUND_PROCESSED";
            case "FAILURE" -> "REFUND_FAILED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund outcome must be SUCCESS or FAILURE");
        };
        MockPaymentGatewayAdapter adapter = (MockPaymentGatewayAdapter) gateways.require("MOCK_GATEWAY");
        String eventId = "mock_refund_" + refund.getId() + "_" + eventStatus;
        String raw = adapter.signedEvent(eventId, payment.getProviderReference(), refund.getAmount(),
                payment.getCurrency(), eventStatus, refund.getProviderRefundReference(), payment.getTenantId().toString());
        return webhooks.accept("MOCK_GATEWAY", raw, adapter.sign(raw));
    }

    private void authorize(Payment payment, Principal actor) {
        if ("VENDOR".equals(actor.accountType())) {
            if (actor.permissions() == null || !(actor.permissions().contains("PAYMENT_RECORD")
                    || actor.permissions().contains("PAYMENT_VIEW") || actor.permissions().contains("PAYMENT_REFUND")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return;
        }
        if (!"CUSTOMER".equals(actor.accountType()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        CustomerProfile customer = customers.findByTenantIdAndUserId(actor.tenantId(), actor.userId())
                .filter(CustomerProfile::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CustomerOrder order = orders.findByIdAndTenantId(payment.getOrderId(), actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!customer.getId().equals(order.getCustomerId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public record OutcomeRequest(String outcome) { }
}
