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
    private final InvoiceRepository invoices; private final PaymentRepository payments;
    private final RefundRepository refunds; private final CustomerProfileRepository customers;
    private final CustomerOrderRepository orders; private final IntegrationConnectionRepository connections;
    private final PaymentGatewayRegistry adapters; private final EnterpriseSaasService entitlements; private final AuditEventRepository audits;
    public PaymentGatewayCheckoutService(InvoiceRepository invoices,PaymentRepository payments,RefundRepository refunds,
            CustomerProfileRepository customers,CustomerOrderRepository orders,IntegrationConnectionRepository connections,
            PaymentGatewayRegistry adapters,EnterpriseSaasService entitlements,AuditEventRepository audits) {
        this.invoices=invoices;this.payments=payments;this.refunds=refunds;this.customers=customers;this.orders=orders;
        this.connections=connections;this.adapters=adapters;this.entitlements=entitlements;this.audits=audits;
    }
    @Transactional
    public CheckoutResult create(UUID invoiceId,String providerCode,String idempotencyKey,Principal actor) {
        if(actor==null||actor.tenantId()==null||actor.userId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(idempotencyKey==null||!idempotencyKey.matches("[A-Za-z0-9._:-]{8,160}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must contain 8 to 160 safe characters");
        Invoice invoice=invoices.findForUpdate(invoiceId,actor.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));
        authorizeInvoice(invoice,actor);
        if(!Set.of("ISSUED","PARTIALLY_PAID").contains(invoice.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice is not open for online payment");
        entitlements.assertFeatureEnabled(actor.tenantId(),"paymentGateway");
        List<IntegrationConnection> active=connections.findByTenantIdAndIntegrationTypeAndStatus(actor.tenantId(),"PAYMENT_GATEWAY","ACTIVE");
        if(active.isEmpty())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No active payment gateway connection is configured for this tenant");
        IntegrationConnection connection;
        if(providerCode==null||providerCode.isBlank()){
            if(active.size()!=1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"providerCode is required when multiple payment gateways are configured");
            connection=active.get(0);
        }else connection=active.stream().filter(x->x.getProviderCode().equalsIgnoreCase(providerCode.trim())).findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Active payment gateway connection not found"));
        Optional<Payment> previous=payments.findByTenantIdAndIdempotencyKey(actor.tenantId(),idempotencyKey);
        if(previous.isPresent()){
            Payment p=previous.get();
            if(!invoiceId.equals(p.getInvoiceId())||!connection.getProviderCode().equalsIgnoreCase(p.getProvider()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key was already used for another payment");
            return view(p);
        }
        BigDecimal outstanding=invoice.getGrandTotal().subtract(netPaid(actor.tenantId(),invoiceId));
        if(outstanding.signum()<=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice has no outstanding balance");
        PaymentGatewayAdapter adapter=adapters.require(connection.getProviderCode());
        UUID paymentId=UUID.nameUUIDFromBytes((actor.tenantId()+":"+idempotencyKey).getBytes(StandardCharsets.UTF_8));
        Map<String,String> metadata=Map.of("tenantId",actor.tenantId().toString(),"invoiceId",invoiceId.toString(),
                "paymentId",paymentId.toString(),"idempotencyKey",idempotencyKey);
        PaymentGatewayAdapter.GatewayPayment gateway;
        try{gateway=adapter.createPayment(outstanding,invoice.getCurrency(),paymentId.toString(),metadata);}
        catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment provider could not create checkout");}
        if(gateway==null||gateway.providerReference()==null||gateway.providerReference().isBlank()
                ||gateway.checkoutUrl()==null||!gateway.checkoutUrl().startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment provider returned an invalid checkout response");
        Payment payment=new Payment();payment.setId(paymentId);payment.setTenantId(actor.tenantId());payment.setInvoiceId(invoiceId);
        payment.setOrderId(invoice.getOrderId());payment.setAmount(outstanding);payment.setCurrency(invoice.getCurrency());
        payment.setMethod("ONLINE");payment.setSource("GATEWAY");payment.setStatus("PENDING");payment.setProvider(connection.getProviderCode());
        payment.setProviderReference(gateway.providerReference());payment.setCheckoutUrl(gateway.checkoutUrl());
        payment.setIdempotencyKey(idempotencyKey);payment.setCreatedAt(Instant.now());payment.setRecordedBy(actor.userId());
        payments.save(payment);
        AuditEvent audit=new AuditEvent();audit.setId(UUID.randomUUID());audit.setTenantId(actor.tenantId());audit.setActorId(actor.userId());
        audit.setActorType(actor.accountType());audit.setAction("PAYMENT_GATEWAY_CHECKOUT_CREATED");audit.setEntityType("PAYMENT");
        audit.setEntityId(paymentId.toString());audit.setOutcome("SUCCESS");
        audit.setAfterJson("{\\"providerCode\\":\\"" + connection.getProviderCode() + "\\",\\"invoiceId\\":\\"" + invoiceId
                + "\\",\\"amount\\":\\"" + outstanding.toPlainString() + "\\",\\"currency\\":\\"" + invoice.getCurrency() + "\\"}");
        audit.setCreatedAt(Instant.now());audits.save(audit);
        return view(payment);
    }
    private void authorizeInvoice(Invoice invoice,Principal actor){
        if("VENDOR".equals(actor.accountType())){
            if(actor.permissions()==null||!actor.permissions().contains("FINANCE_MANAGE"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Finance permission required");
            return;
        }
        if(!"CUSTOMER".equals(actor.accountType()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        CustomerProfile customer=customers.findByTenantIdAndUserId(actor.tenantId(),actor.userId()).filter(CustomerProfile::isActive)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer profile not found"));
        CustomerOrder order=orders.findByIdAndTenantId(invoice.getOrderId(),actor.tenantId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));
        if(!customer.getId().equals(order.getCustomerId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found");
    }
    private BigDecimal netPaid(UUID tenantId,UUID invoiceId){
        BigDecimal total=BigDecimal.ZERO;
        for(Payment payment:payments.findByTenantIdAndInvoiceId(tenantId,invoiceId)){
            if(!Set.of("CONFIRMED","PARTIALLY_REFUNDED").contains(payment.getStatus()))continue;
            BigDecimal refunded=refunds.findByTenantIdAndPaymentId(tenantId,payment.getId()).stream()
                    .filter(r->"RECORDED".equals(r.getStatus())).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            total=total.add(payment.getAmount().subtract(refunded));
        }
        return total;
    }
    private CheckoutResult view(Payment p){return new CheckoutResult(p.getId(),p.getProvider(),p.getProviderReference(),p.getCheckoutUrl(),p.getAmount(),p.getCurrency(),p.getStatus());}
    public record CheckoutResult(UUID paymentId,String providerCode,String providerReference,String checkoutUrl,BigDecimal amount,String currency,String status){}
}
