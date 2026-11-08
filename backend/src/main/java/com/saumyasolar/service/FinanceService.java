package com.saumyasolar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.api.FinanceModels.*;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class FinanceService {
    private final InvoiceRepository invoices; private final InvoiceItemRepository invoiceItems;
    private final InvoiceSequenceRepository sequences; private final CreditNoteRepository creditNotes; private final PaymentRepository payments;
    private final PaymentWebhookEventRepository webhookEvents; private final RefundRepository refunds;
    private final CustomerOrderRepository orders; private final OrderItemRepository orderItems;
    private final CustomerProfileRepository customers; private final TenantRepository tenants;
    private final ProductRepository products; private final ServiceRequestRepository tickets;
    private final ServiceVisitRepository visits; private final ServiceSlaPolicyRepository slaPolicies;
    private final ServiceEventRepository serviceEvents; private final AppUserRepository users;
    private final WarrantyRepository warranties; private final InstallationJobRepository installations;
    private final InventoryBalanceRepository balances; private final WarehouseRepository warehouses;
    private final InventoryTransactionRepository inventoryTransactions;
    private final ObjectMapper mapper;
    private final String webhookSecret;
    private final NotificationService notifications;

    public FinanceService(InvoiceRepository invoices,InvoiceItemRepository invoiceItems,InvoiceSequenceRepository sequences,CreditNoteRepository creditNotes,
        PaymentRepository payments,PaymentWebhookEventRepository webhookEvents,RefundRepository refunds,
        CustomerOrderRepository orders,OrderItemRepository orderItems,CustomerProfileRepository customers,
        TenantRepository tenants,ProductRepository products,ServiceRequestRepository tickets,ServiceVisitRepository visits,
        ServiceSlaPolicyRepository slaPolicies,ServiceEventRepository serviceEvents,AppUserRepository users,
        WarrantyRepository warranties,InstallationJobRepository installations,InventoryBalanceRepository balances,
        WarehouseRepository warehouses,InventoryTransactionRepository inventoryTransactions,ObjectMapper mapper,
        @Value("${app.payment.webhook-secret:}") String webhookSecret, NotificationService notifications) {
        this.invoices=invoices;this.invoiceItems=invoiceItems;this.sequences=sequences;this.creditNotes=creditNotes;this.payments=payments;
        this.webhookEvents=webhookEvents;this.refunds=refunds;this.orders=orders;this.orderItems=orderItems;
        this.customers=customers;this.tenants=tenants;this.products=products;this.tickets=tickets;this.visits=visits;
        this.slaPolicies=slaPolicies;this.serviceEvents=serviceEvents;this.users=users;this.warranties=warranties;
        this.installations=installations;this.balances=balances;this.warehouses=warehouses;
        this.inventoryTransactions=inventoryTransactions;this.mapper=mapper;this.webhookSecret=webhookSecret;this.notifications=notifications;
    }
    private UUID vendorTenant(Principal p){if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p.tenantId();}
    private void permission(Principal p,String code){if(p.permissions()==null||!p.permissions().contains(code))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Missing permission: "+code);}
    private CustomerProfile customer(Principal p){if(!"CUSTOMER".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);return customers.findByTenantIdAndUserId(p.tenantId(),p.userId()).filter(CustomerProfile::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer profile not found"));}
    private Invoice invoice(UUID id,UUID tenant){return invoices.findByIdAndTenantId(id,tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));}
    private ServiceRequest ticket(UUID id,UUID tenant){return tickets.findByIdAndTenantId(id,tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Service ticket not found"));}
    private void financeAccess(Principal p){vendorTenant(p);permission(p,"FINANCE_MANAGE");}
    private String fiscalYear(LocalDate date){int year=date.getYear();return date.getMonthValue()>=4?year+"-"+String.valueOf(year+1).substring(2):year-1+"-"+String.valueOf(year).substring(2);}
    public List<CustomerOrder> billableOrders(Principal p){
        financeAccess(p);
        Set<String> eligible=Set.of("CONFIRMED","PAYMENT_PENDING","PAYMENT_PARTIAL","PAID","MATERIAL_RESERVED","INSTALLATION_SCHEDULED","INSTALLATION_IN_PROGRESS","INSTALLED","COMMISSIONED","CLOSED");
        return orders.findByTenantIdOrderByIdDesc(p.tenantId()).stream()
            .filter(o->eligible.contains(o.getStatus()))
            .filter(o->invoices.findByOrderIdAndTenantId(o.getId(),p.tenantId()).isEmpty())
            .toList();
    }
    @Transactional
    public Invoice createInvoice(InvoiceCreate input,Principal p){
        UUID tenant=vendorTenant(p);permission(p,"FINANCE_MANAGE");
        CustomerOrder order=orders.findForUpdate(input.orderId(),tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        if(!Set.of("CONFIRMED","PAYMENT_PENDING","PAYMENT_PARTIAL","PAID","MATERIAL_RESERVED","INSTALLATION_SCHEDULED","INSTALLATION_IN_PROGRESS","INSTALLED","COMMISSIONED","CLOSED").contains(order.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An invoice can only be issued for a confirmed order milestone");
        var existing=invoices.findByOrderIdAndTenantId(order.getId(),tenant);if(existing.isPresent())return existing.get();
        CustomerProfile cp=customers.findByIdAndTenantId(order.getCustomerId(),tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order customer not found"));
        Tenant t=tenants.findByIdForUpdate(tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
        if(input.taxTreatment()!=null&&!Set.of("IGST","CGST_SGST","UNSPECIFIED").contains(input.taxTreatment()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tax treatment must be IGST, CGST_SGST or UNSPECIFIED");

        List<OrderItem> lines=orderItems.findByTenantIdOrderByIdDesc(tenant).stream().filter(x->order.getId().equals(x.getOrderId())).toList();
        if(lines.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Order has no billable line items");
        BigDecimal grossSubtotal=lines.stream().map(line->line.getLineTotal()==null
                ?line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())):line.getLineTotal())
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal discountPercent=order.getDiscountPercent()==null?BigDecimal.ZERO:order.getDiscountPercent();
        BigDecimal discountAmount=order.getDiscountAmount()==null
                ?grossSubtotal.multiply(discountPercent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
                :order.getDiscountAmount();
        if(discountPercent.signum()<0||discountPercent.compareTo(new BigDecimal("100"))>0
                ||discountAmount.signum()<0||discountAmount.compareTo(grossSubtotal)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Order discount snapshot is invalid");
        BigDecimal subtotal=order.getSubtotal()==null?grossSubtotal.subtract(discountAmount):order.getSubtotal().subtract(discountAmount);
        if(subtotal.compareTo(grossSubtotal.subtract(discountAmount))!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Order subtotal and discount snapshots do not reconcile");

        BigDecimal taxTotal=order.getTaxTotal();
        boolean hasPerLineSnapshots=lines.stream().allMatch(line->line.getDiscountAmount()!=null
                &&line.getTaxableAmount()!=null&&line.getTaxAmount()!=null);
        BigDecimal allocatedDiscount=BigDecimal.ZERO,allocatedTax=BigDecimal.ZERO,allocatedTaxable=BigDecimal.ZERO;
        List<BigDecimal> taxableAmounts=new ArrayList<>(),taxAmounts=new ArrayList<>();
        for(int index=0;index<lines.size();index++){
            OrderItem line=lines.get(index);
            BigDecimal gross=line.getLineTotal()==null?line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())):line.getLineTotal();
            BigDecimal lineDiscount;
            BigDecimal taxable;
            BigDecimal tax;
            BigDecimal rate=line.getTaxPercent()==null?BigDecimal.ZERO:line.getTaxPercent();
            if(rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Order tax snapshot is invalid");
            if(hasPerLineSnapshots){
                lineDiscount=line.getDiscountAmount();
                taxable=line.getTaxableAmount();
                tax=line.getTaxAmount();
                if(lineDiscount.signum()<0||taxable.signum()<0||tax.signum()<0
                        ||gross.subtract(lineDiscount).compareTo(taxable)!=0
                        ||taxable.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP).compareTo(tax)!=0)
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"Order line financial snapshot is invalid");
            }else{
                lineDiscount=index==lines.size()-1?discountAmount.subtract(allocatedDiscount)
                        :gross.multiply(discountAmount).divide(grossSubtotal,2,RoundingMode.HALF_UP);
                taxable=gross.subtract(lineDiscount);
                if(taxable.signum()<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order line discount exceeds line value");
                tax=taxable.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
                if(index==lines.size()-1&&taxTotal!=null)tax=taxTotal.subtract(allocatedTax);
            }
            if(tax.signum()<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order tax snapshot does not reconcile");
            taxableAmounts.add(taxable);taxAmounts.add(tax);
            allocatedDiscount=allocatedDiscount.add(lineDiscount);
            allocatedTaxable=allocatedTaxable.add(taxable);
            allocatedTax=allocatedTax.add(tax);
        }
        if(allocatedDiscount.compareTo(discountAmount)!=0||allocatedTaxable.compareTo(subtotal)!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Order line discount/taxable snapshots do not reconcile");
        if(taxTotal==null)taxTotal=allocatedTax;
        if(allocatedTax.compareTo(taxTotal)!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Order line tax does not reconcile with accepted quotation tax");
        if(subtotal.add(taxTotal).compareTo(order.getTotal())!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice total does not reconcile with accepted order total");

        LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));String fy=fiscalYear(today);
        InvoiceSequence sequenceRecord=sequences.findByTenantIdAndFiscalYearForUpdate(tenant,fy).orElseGet(()->{
            InvoiceSequence created=new InvoiceSequence();created.setId(UUID.randomUUID());created.setTenantId(tenant);created.setFiscalYear(fy);created.setNextNumber(0);return created;
        });
        long sequence=sequenceRecord.getNextNumber()+1;
        sequenceRecord.setNextNumber(sequence);
        sequences.save(sequenceRecord);
        Invoice invoice=new Invoice();invoice.setId(UUID.randomUUID());invoice.setTenantId(tenant);invoice.setOrderId(order.getId());
        invoice.setFiscalYear(fy);invoice.setInvoiceNumber((t.getInvoicePrefix()==null?"INV":t.getInvoicePrefix())+"-"+fy+"-"+String.format(Locale.ROOT,"%06d",sequence));
        invoice.setStatus("ISSUED");invoice.setCustomerName(cp.getName());invoice.setCustomerEmail(cp.getEmail());invoice.setCustomerAddress(cp.getAddress());invoice.setCustomerGstin(cp.getGstin());
        invoice.setVendorName(t.getName());invoice.setVendorGstin(t.getGstin());invoice.setCurrency("INR");invoice.setTaxTreatment(input.taxTreatment());
        invoice.setDueDate(input.dueDate());invoice.setIssuedAt(Instant.now());invoice.setCreatedAt(Instant.now());invoice.setCreatedBy(p.userId());
        invoice.setSubtotal(subtotal);invoice.setTaxTotal(taxTotal);
        if("CGST_SGST".equals(invoice.getTaxTreatment())){BigDecimal half=taxTotal.divide(new BigDecimal("2"),2,RoundingMode.HALF_UP);invoice.setCgstAmount(half);invoice.setSgstAmount(taxTotal.subtract(half));}
        else if("IGST".equals(invoice.getTaxTreatment()))invoice.setIgstAmount(taxTotal);
        invoice.setGrandTotal(subtotal.add(taxTotal));
        invoices.save(invoice);
        for(int index=0;index<lines.size();index++){
            OrderItem line=lines.get(index);
            BigDecimal rate=line.getTaxPercent()==null?BigDecimal.ZERO:line.getTaxPercent();
            Product product=line.getProductId()==null?null:products.findByIdAndTenantId(line.getProductId(),tenant).orElse(null);
            InvoiceItem item=new InvoiceItem();item.setId(UUID.randomUUID());item.setTenantId(tenant);item.setInvoiceId(invoice.getId());item.setProductId(line.getProductId());
            item.setDescription(line.getDescription());item.setHsnCode(product==null?null:product.getHsnCode());item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());item.setTaxPercent(rate);item.setTaxableAmount(taxableAmounts.get(index));
            item.setTaxAmount(taxAmounts.get(index));item.setLineTotal(taxableAmounts.get(index).add(taxAmounts.get(index)));invoiceItems.save(item);
        }
        Invoice saved=invoices.save(invoice);
        notifications.sendBusinessNotification(cp.getUserId(),tenant,"INVOICE_ISSUED",cp.getEmail(),
                "Your Soumya Solar invoice "+invoice.getInvoiceNumber()+" is ready",
                "Hello "+cp.getName()+",\n\nInvoice "+invoice.getInvoiceNumber()+" has been issued for INR "+invoice.getGrandTotal()
                        +". Sign in to your customer portal to download the invoice.");
        return saved;
    }
    @Transactional public CreditNote createCreditNote(UUID invoiceId,CreditNoteCreate x,Principal p){
        financeAccess(p);Invoice i=invoices.findForUpdate(invoiceId,p.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));
        if(x.amount()==null||x.amount().signum()<=0||x.reason()==null||x.reason().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Credit note amount and reason are required");
        if("VOID".equals(i.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"A void invoice cannot receive a credit note");
        List<CreditNote> prior=creditNotes.findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(p.tenantId(),invoiceId);
        BigDecimal already=prior.stream().filter(c->"ISSUED".equals(c.getStatus())).map(CreditNote::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(already.add(x.amount()).compareTo(i.getGrandTotal())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Credit notes cannot exceed the invoice total");
        CreditNote c=new CreditNote();c.setId(UUID.randomUUID());c.setTenantId(p.tenantId());c.setInvoiceId(invoiceId);
        c.setCreditNoteNumber("CN-"+i.getInvoiceNumber()+"-"+String.format(Locale.ROOT,"%02d",prior.size()+1));
        c.setReason(x.reason().trim());c.setAmount(x.amount());c.setStatus("ISSUED");c.setCreatedBy(p.userId());c.setCreatedAt(Instant.now());
        creditNotes.save(c);i.setStatus(already.add(x.amount()).compareTo(i.getGrandTotal())==0?"CREDITED":"PARTIALLY_CREDITED");invoices.save(i);return c;
    }
    public List<CreditNote> creditNotes(UUID invoiceId,Principal p){
        Invoice i=invoice(invoiceId,p.tenantId());
        if("CUSTOMER".equals(p.accountType())){CustomerProfile cp=customer(p);if(!orders.findByIdAndTenantId(i.getOrderId(),p.tenantId()).map(o->cp.getId().equals(o.getCustomerId())).orElse(false))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        else financeAccess(p);
        return creditNotes.findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(p.tenantId(),invoiceId);
    }
    public List<Invoice> listInvoices(Principal p){
        if("CUSTOMER".equals(p.accountType())){CustomerProfile cp=customer(p);return invoices.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream().filter(i->orders.findByIdAndTenantId(i.getOrderId(),p.tenantId()).map(o->cp.getId().equals(o.getCustomerId())).orElse(false)).toList();}
        financeAccess(p);return invoices.findByTenantIdOrderByCreatedAtDesc(p.tenantId());
    }
    public List<InvoiceItem> invoiceLines(UUID id,Principal p){Invoice i=invoice(id,p.tenantId());if("CUSTOMER".equals(p.accountType())){CustomerProfile cp=customer(p);if(!orders.findByIdAndTenantId(i.getOrderId(),p.tenantId()).map(o->cp.getId().equals(o.getCustomerId())).orElse(false))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}else{financeAccess(p);}return invoiceItems.findByTenantIdAndInvoiceId(p.tenantId(),id);}
    @Transactional public Invoice voidInvoice(UUID id,String reason,Principal p){financeAccess(p);if(reason==null||reason.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Void reason is required");Invoice i=invoices.findForUpdate(id,p.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));if(!"ISSUED".equals(i.getStatus())&&! "PARTIALLY_PAID".equals(i.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an issued, unpaid or partially paid invoice can be voided");if(payments.findByTenantIdAndInvoiceId(p.tenantId(),id).stream().anyMatch(x->"CONFIRMED".equals(x.getStatus())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Record a refund before voiding an invoice with payments");i.setStatus("VOID");i.setVoidReason(reason.trim());return invoices.save(i);}
    @Transactional public Payment recordPayment(ManualPayment x,Principal p){financeAccess(p);if(x.paidAt()==null||x.paidAt().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment date cannot be in the future");Invoice i=invoices.findForUpdate(x.invoiceId(),p.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));if(!Set.of("ISSUED","PARTIALLY_PAID").contains(i.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice is not open for payment");BigDecimal paid=netPaid(p.tenantId(),i.getId());BigDecimal outstanding=i.getGrandTotal().subtract(paid);if(x.amount().compareTo(outstanding)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment exceeds the invoice outstanding amount of "+outstanding);Payment payment=new Payment();payment.setId(UUID.randomUUID());payment.setTenantId(p.tenantId());payment.setInvoiceId(i.getId());payment.setOrderId(i.getOrderId());payment.setAmount(x.amount());payment.setCurrency(i.getCurrency());payment.setMethod(x.method());payment.setSource("MANUAL");payment.setStatus("CONFIRMED");payment.setReference(x.reference());payment.setPaidAt(x.paidAt());payment.setRecordedBy(p.userId());payment.setCreatedAt(Instant.now());Payment saved=payments.save(payment);i.setStatus(paid.add(x.amount()).compareTo(i.getGrandTotal())>=0?"PAID":"PARTIALLY_PAID");invoices.save(i);
        orders.findByIdAndTenantId(i.getOrderId(),p.tenantId()).ifPresent(order->customers.findByIdAndTenantId(order.getCustomerId(),p.tenantId()).ifPresent(cp->
                notifications.sendBusinessNotification(cp.getUserId(),p.tenantId(),"PAYMENT_RECORDED",cp.getEmail(),
                        "Payment received for invoice "+i.getInvoiceNumber(),
                        "Hello "+cp.getName()+",\n\nA payment of INR "+x.amount()+" was recorded for invoice "+i.getInvoiceNumber()+". Current invoice status: "+i.getStatus()+".")));
        return saved;}
    private BigDecimal netPaid(UUID tenant,UUID invoiceId){BigDecimal total=BigDecimal.ZERO;for(Payment payment:payments.findByTenantIdAndInvoiceId(tenant,invoiceId)){if("CONFIRMED".equals(payment.getStatus()))total=total.add(payment.getAmount());else if("PARTIALLY_REFUNDED".equals(payment.getStatus())){BigDecimal refunded=refunds.findByTenantIdAndPaymentId(tenant,payment.getId()).stream().filter(r->"RECORDED".equals(r.getStatus())).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);total=total.add(payment.getAmount().subtract(refunded));}}return total;}
    public Payment getPayment(UUID id,Principal p){Payment payment=payments.findByIdAndTenantId(id,p.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found"));if("CUSTOMER".equals(p.accountType())){if(listInvoices(p).stream().noneMatch(i->i.getId().equals(payment.getInvoiceId())))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}else financeAccess(p);return payment;}
    public List<Payment> listPayments(Principal p){if("CUSTOMER".equals(p.accountType())){CustomerProfile cp=customer(p);Set<UUID> invoiceIds=new HashSet<>();listInvoices(p).forEach(i->invoiceIds.add(i.getId()));return payments.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream().filter(v->invoiceIds.contains(v.getInvoiceId())).toList();}financeAccess(p);return payments.findByTenantIdOrderByCreatedAtDesc(p.tenantId());}
    @Transactional public Refund refund(RefundCreate x,Principal p){financeAccess(p);permission(p,"PAYMENT_REFUND");if(x.amount()==null||x.amount().signum()<=0||x.reason()==null||x.reason().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A positive refund amount and reason are required");Payment pay=payments.findForUpdate(x.paymentId(),p.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found"));if(!"MANUAL".equals(pay.getSource()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Gateway refunds require a configured provider refund adapter; no gateway is configured");BigDecimal refunded=refunds.findByTenantIdAndPaymentId(p.tenantId(),pay.getId()).stream().filter(r->"RECORDED".equals(r.getStatus())).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);if(refunded.add(x.amount()).compareTo(pay.getAmount())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund exceeds the unrefunded payment amount");Refund r=new Refund();r.setId(UUID.randomUUID());r.setTenantId(p.tenantId());r.setPaymentId(pay.getId());r.setAmount(x.amount());r.setReason(x.reason().trim());r.setStatus("RECORDED");r.setRecordedBy(p.userId());r.setCreatedAt(Instant.now());Refund saved=refunds.save(r);pay.setStatus(refunded.add(x.amount()).compareTo(pay.getAmount())==0?"REFUNDED":"PARTIALLY_REFUNDED");payments.save(pay);Invoice invoice=invoice(pay.getInvoiceId(),p.tenantId());BigDecimal net=netPaid(p.tenantId(),invoice.getId());invoice.setStatus(net.compareTo(invoice.getGrandTotal())>=0?"PAID":net.signum()>0?"PARTIALLY_PAID":"ISSUED");invoices.save(invoice);return saved;}
    private boolean validSignature(String raw,String signature){if(webhookSecret==null||webhookSecret.isBlank()||signature==null)return false;try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String expected=HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));}catch(Exception e){return false;}}
    @Transactional public Map<String,Object> webhook(String provider,String raw,String signature,WebhookEvent event){if(!validSignature(raw,signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid payment webhook signature");String key=provider.toUpperCase(Locale.ROOT);var duplicate=webhookEvents.findByProviderAndEventId(key,event.eventId());if(duplicate.isPresent())return Map.of("accepted",true,"duplicate",true);PaymentWebhookEvent record=new PaymentWebhookEvent();record.setId(UUID.randomUUID());record.setProvider(key);record.setEventId(event.eventId());record.setPayloadHash(HexFormat.of().formatHex(hash(raw)));record.setProcessingStatus("RECEIVED");record.setReceivedAt(Instant.now());webhookEvents.saveAndFlush(record);Payment pay=payments.findByProviderAndProviderReference(key,event.paymentReference()).orElse(null);if(pay==null){record.setProcessingStatus("UNMATCHED");record.setProcessedAt(Instant.now());webhookEvents.save(record);return Map.of("accepted",true,"matched",false);}if(pay.getAmount().compareTo(event.amount())!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Webhook amount does not match the recorded provider payment");if("SUCCEEDED".equals(event.status()))pay.setStatus("CONFIRMED");else if("FAILED".equals(event.status())&&"PENDING".equals(pay.getStatus()))pay.setStatus("FAILED");payments.save(pay);Invoice i=invoice(pay.getInvoiceId(),pay.getTenantId());if("CONFIRMED".equals(pay.getStatus())){BigDecimal total=payments.findByTenantIdAndInvoiceId(pay.getTenantId(),i.getId()).stream().filter(v->"CONFIRMED".equals(v.getStatus())).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);i.setStatus(total.compareTo(i.getGrandTotal())>=0?"PAID":"PARTIALLY_PAID");invoices.save(i);}record.setProcessingStatus("PROCESSED");record.setProcessedAt(Instant.now());webhookEvents.save(record);return Map.of("accepted",true,"matched",true,"duplicate",false);}
    private byte[] hash(String raw){try{return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}

    private void servicePermission(Principal p){vendorTenant(p);permission(p,"SERVICE_MANAGE");}
    private void event(ServiceRequest t,Principal p,String type,String old,String next,String reason){ServiceEvent e=new ServiceEvent();e.setId(UUID.randomUUID());e.setTenantId(t.getTenantId());e.setServiceRequestId(t.getId());e.setActorId(p.userId());e.setEventType(type);e.setOldStatus(old);e.setNewStatus(next);e.setReason(reason);e.setOccurredAt(Instant.now());serviceEvents.save(e);}
    private void ensureTicketAccess(ServiceRequest t,Principal p){if("CUSTOMER".equals(p.accountType())){CustomerProfile cp=customer(p);if(!cp.getId().equals(t.getCustomerId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}else{servicePermission(p);if(p.roles()==null||(!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r))&&!Objects.equals(t.getAssignedEngineerId(),p.userId())))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}}
    @Transactional public ServiceRequest createTicket(ServiceCreate x,Principal p){
        UUID tenant;CustomerProfile cp;
        if("CUSTOMER".equals(p.accountType())){cp=customer(p);tenant=p.tenantId();}
        else{servicePermission(p);tenant=p.tenantId();if(x.customerId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Customer is required when a vendor creates a service ticket");cp=customers.findByIdAndTenantId(x.customerId(),tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found"));}
        if(x.installationId()!=null){installations.findById(x.installationId()).filter(j->tenant.equals(j.getTenantId())&&cp.getId().equals(j.getCustomerId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Installation not found"));}
        Product product=x.productId()==null?null:products.findByIdAndTenantId(x.productId(),tenant).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
        if(product!=null&&"CUSTOMER".equals(p.accountType())){
            Set<UUID> ownedProducts=new HashSet<>();
            for(CustomerOrder customerOrder:orders.findByTenantIdOrderByIdDesc(tenant).stream().filter(o->cp.getId().equals(o.getCustomerId())).toList())
                for(OrderItem item:orderItems.findByTenantIdOrderByIdDesc(tenant).stream().filter(v->customerOrder.getId().equals(v.getOrderId())).toList())if(item.getProductId()!=null)ownedProducts.add(item.getProductId());
            boolean hasWarranty=warranties.findByTenantIdAndCustomerIdOrderByStartDateDesc(tenant,cp.getId()).stream().anyMatch(w->product.getId().equals(w.getProductId())&&(x.serialNumber()==null||Objects.equals(w.getSerialNumber(),x.serialNumber())));
            if(!ownedProducts.contains(product.getId())&&!hasWarranty)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product is not associated with this customer's order or warranty");
        }
        String warrantyStatus="UNKNOWN";if(product!=null){Warranty active=warranties.findByTenantIdAndCustomerIdOrderByStartDateDesc(tenant,cp.getId()).stream().filter(w->product.getId().equals(w.getProductId())&&(!product.isSerialized()||(x.serialNumber()!=null&&Objects.equals(w.getSerialNumber(),x.serialNumber())))&&"ACTIVE".equals(w.getStatus())&&!LocalDate.now().isBefore(w.getStartDate())&&!LocalDate.now().isAfter(w.getEndDate())).findFirst().orElse(null);warrantyStatus=active==null?"OUT_OF_WARRANTY":"IN_WARRANTY";}
        ServiceSlaPolicy policy=slaPolicies.findByTenantIdAndPriority(tenant,x.priority()).filter(ServiceSlaPolicy::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Configure an active SLA policy for priority "+x.priority()+" before accepting tickets"));
        Instant now=Instant.now();ServiceRequest t=new ServiceRequest();t.setId(UUID.randomUUID());t.setTenantId(tenant);t.setTicketNumber("SRV-"+LocalDate.now().getYear()+"-"+t.getId().toString().substring(0,8).toUpperCase(Locale.ROOT));t.setCustomerId(cp.getId());t.setInstallationId(x.installationId());t.setProductId(x.productId());t.setSerialNumber(x.serialNumber());t.setCategory(x.category().trim());t.setPriority(x.priority());t.setDescription(x.description().trim());t.setWarrantyStatus(warrantyStatus);t.setStatus("OPEN");t.setResponseDueAt(calculateSlaDue(now,policy.getResponseMinutes(),policy));t.setResolutionDueAt(calculateSlaDue(now,policy.getResolutionMinutes(),policy));t.setCreatedAt(now);t.setUpdatedAt(now);tickets.save(t);event(t,p,"CREATED",null,"OPEN","Service request created");return t;
    }
    public ServiceRequest getTicket(UUID id,Principal p){ServiceRequest t=ticket(id,p.tenantId());ensureTicketAccess(t,p);return t;}
    public List<ServiceRequest> listTickets(Principal p){if("CUSTOMER".equals(p.accountType())){CustomerProfile cp=customer(p);return tickets.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream().filter(t->cp.getId().equals(t.getCustomerId())).toList();}servicePermission(p);List<ServiceRequest> all=tickets.findByTenantIdOrderByCreatedAtDesc(p.tenantId());if(p.roles()!=null&&p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))return all;return all.stream().filter(t->p.userId().equals(t.getAssignedEngineerId())).toList();}
    @Transactional public ServiceRequest assignTicket(UUID id,ServiceAssign x,Principal p){servicePermission(p);if(p.roles()==null||!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);UUID tenant=p.tenantId();ServiceRequest t=ticket(id,tenant);AppUser engineer=users.findById(x.engineerId()).filter(u->tenant.equals(u.getTenantId())&&u.isEnabled()&&u.getRoles().stream().anyMatch(r->"SERVICE_ENGINEER".equals(r.getName())||"TECHNICIAN".equals(r.getName())||"INSTALLER".equals(r.getName()))).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Engineer must be an enabled same-tenant service engineer"));String old=t.getStatus();if(!Set.of("OPEN","ASSIGNED","VISIT_SCHEDULED").contains(old))throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket cannot be assigned in its current state");t.setAssignedEngineerId(engineer.getId());t.setStatus("ASSIGNED");if(t.getFirstRespondedAt()==null)t.setFirstRespondedAt(Instant.now());t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"ASSIGNED",old,"ASSIGNED",x.reason());return t;}
    @Transactional public ServiceVisit scheduleVisit(UUID id,ServiceVisitCreate x,Principal p){servicePermission(p);if(p.roles()==null||!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);ServiceRequest t=ticket(id,p.tenantId());if(!x.scheduledAt().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Visit must be scheduled in the future");if(t.getAssignedEngineerId()==null||!t.getAssignedEngineerId().equals(x.engineerId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Visit engineer must be the currently assigned engineer");if(!Set.of("ASSIGNED","VISIT_SCHEDULED","IN_PROGRESS").contains(t.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket is not ready for a service visit");ServiceVisit v=new ServiceVisit();v.setId(UUID.randomUUID());v.setTenantId(p.tenantId());v.setServiceRequestId(id);v.setEngineerId(x.engineerId());v.setScheduledAt(x.scheduledAt());visits.save(v);String old=t.getStatus();t.setStatus("VISIT_SCHEDULED");t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"VISIT_SCHEDULED",old,t.getStatus(),x.reason());return v;}
    @Transactional public ServiceVisit startVisit(UUID id,UUID visitId,Principal p){servicePermission(p);ServiceRequest t=ticket(id,p.tenantId());ensureTicketAccess(t,p);ServiceVisit v=visits.findById(visitId).filter(a->a.getTenantId().equals(p.tenantId())&&a.getServiceRequestId().equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Visit not found"));if(!v.getEngineerId().equals(p.userId())&&!(p.roles()!=null&&p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r))))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(v.getStartedAt()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Visit has already started");v.setStartedAt(Instant.now());visits.save(v);if(t.getFirstRespondedAt()==null)t.setFirstRespondedAt(Instant.now());String old=t.getStatus();t.setStatus("IN_PROGRESS");t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"VISIT_STARTED",old,"IN_PROGRESS","Engineer started service visit");return v;}
    @Transactional public ServiceVisit completeVisit(UUID id,UUID visitId,ServiceVisitComplete x,Principal p){servicePermission(p);ServiceRequest t=ticket(id,p.tenantId());ensureTicketAccess(t,p);ServiceVisit v=visits.findById(visitId).filter(a->a.getTenantId().equals(p.tenantId())&&a.getServiceRequestId().equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Visit not found"));if(!v.getEngineerId().equals(p.userId())&&!(p.roles()!=null&&p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r))))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(v.getCompletedAt()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Visit is already completed");if(v.getStartedAt()==null||!"IN_PROGRESS".equals(t.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Start the assigned service visit before completing it");for(ServicePart part:x.parts()==null?List.<ServicePart>of():x.parts()){warehouses.findForUpdate(part.warehouseId(),p.tenantId()).filter(Warehouse::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found"));Product prod=products.findByIdAndTenantId(part.productId(),p.tenantId()).filter(Product::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Part product not found"));InventoryBalance balance=balances.findForUpdate(p.tenantId(),part.warehouseId(),part.productId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"No inventory balance for service part "+prod.getSku()));if(balance.getAvailable().compareTo(part.quantity())<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient available stock for service part "+prod.getSku());balance.setOnHand(balance.getOnHand().subtract(part.quantity()));balances.save(balance);InventoryTransaction movement=new InventoryTransaction();movement.setId(UUID.randomUUID());movement.setTenantId(p.tenantId());movement.setWarehouseId(part.warehouseId());movement.setProductId(part.productId());movement.setServiceRequestId(id);movement.setType("SERVICE_CONSUMPTION");movement.setQuantityDelta(part.quantity().negate());movement.setReservedDelta(BigDecimal.ZERO);movement.setDamagedDelta(BigDecimal.ZERO);movement.setReason("Service ticket "+t.getTicketNumber()+": "+part.reason());movement.setActorId(p.userId());movement.setCreatedAt(Instant.now());inventoryTransactions.save(movement);}
        v.setEngineerNotes(x.engineerNotes());v.setResolution(x.resolution());v.setEvidenceUrl(x.evidenceUrl());try{v.setPartsConsumedJson(mapper.writeValueAsString(x.parts()==null?List.of():x.parts()));}catch(Exception e){throw new IllegalStateException(e);}v.setCompletedAt(Instant.now());if(v.getStartedAt()==null)v.setStartedAt(v.getCompletedAt());visits.save(v);String old=t.getStatus();t.setStatus("RESOLVED");t.setResolutionNotes(x.resolution());t.setResolvedAt(Instant.now());t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"RESOLVED",old,"RESOLVED",x.resolution());return v;}
    @Transactional public ServiceRequest confirmService(UUID id,Principal p){CustomerProfile cp=customer(p);ServiceRequest t=ticket(id,p.tenantId());if(!cp.getId().equals(t.getCustomerId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(!"RESOLVED".equals(t.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a resolved ticket can be confirmed");String old=t.getStatus();t.setStatus("CUSTOMER_CONFIRMED");t.setCustomerConfirmedAt(Instant.now());t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"CUSTOMER_CONFIRMED",old,t.getStatus(),"Customer confirmed service resolution");return t;}
    @Transactional public ServiceRequest closeTicket(UUID id,String reason,Principal p){servicePermission(p);if(reason==null||reason.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Closure reason is required");if(p.roles()==null||!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);ServiceRequest t=ticket(id,p.tenantId());if(!"CUSTOMER_CONFIRMED".equals(t.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Customer confirmation is required before closure");String old=t.getStatus();t.setStatus("CLOSED");t.setClosedAt(Instant.now());t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"CLOSED",old,"CLOSED",reason);return t;}
    @Transactional public ServiceRequest reopenTicket(UUID id,String reason,Principal p){if(reason==null||reason.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reopen reason is required");CustomerProfile cp=customer(p);ServiceRequest t=ticket(id,p.tenantId());if(!cp.getId().equals(t.getCustomerId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);Tenant tenant=tenants.findById(p.tenantId()).orElseThrow();if(tenant.getServiceReopenDays()==null||tenant.getServiceReopenDays()<1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket reopening is disabled by vendor policy");if(!"CLOSED".equals(t.getStatus())||t.getClosedAt()==null||Instant.now().isAfter(t.getClosedAt().plus(Duration.ofDays(tenant.getServiceReopenDays()))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket is outside the configured reopen window");ServiceSlaPolicy policy=slaPolicies.findByTenantIdAndPriority(p.tenantId(),t.getPriority()).filter(ServiceSlaPolicy::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Active SLA policy is required to reopen this ticket"));String old=t.getStatus();Instant now=Instant.now();t.setStatus("OPEN");t.setAssignedEngineerId(null);t.setClosedAt(null);t.setCustomerConfirmedAt(null);t.setResolvedAt(null);t.setFirstRespondedAt(null);t.setResponseBreachedAt(null);t.setResolutionBreachedAt(null);t.setResponseDueAt(calculateSlaDue(now,policy.getResponseMinutes(),policy));t.setResolutionDueAt(calculateSlaDue(now,policy.getResolutionMinutes(),policy));t.setUpdatedAt(now);tickets.save(t);event(t,p,"REOPENED",old,"OPEN",reason);return t;}
    public List<ServiceVisit> serviceVisits(UUID id,Principal p){ServiceRequest t=ticket(id,p.tenantId());ensureTicketAccess(t,p);return visits.findByTenantIdAndServiceRequestIdOrderByScheduledAtAsc(p.tenantId(),id);}
    public List<ServiceEvent> serviceHistory(UUID id,Principal p){ServiceRequest t=ticket(id,p.tenantId());ensureTicketAccess(t,p);return serviceEvents.findByTenantIdAndServiceRequestIdOrderByOccurredAtAsc(p.tenantId(),id);}
    public List<CustomerProfile> serviceCustomers(Principal p){servicePermission(p);return customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(CustomerProfile::isActive).toList();}
    public List<AppUser> serviceEngineers(Principal p){servicePermission(p);return users.findByTenantId(p.tenantId()).stream().filter(AppUser::isEnabled).filter(u->u.getRoles().stream().anyMatch(r->Set.of("SERVICE_ENGINEER","TECHNICIAN","INSTALLER").contains(r.getName()))).toList();}
    private String defaultWorkingDays(){try{return mapper.writeValueAsString(List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"));}catch(Exception e){throw new IllegalStateException(e);}}
    private void validateCalendar(ServiceSlaPolicy policy){
        try{ZoneId.of(policy.getTimeZone());var holidays=mapper.readTree(policy.getHolidaysJson()==null?"[]":policy.getHolidaysJson());if(!holidays.isArray())throw new IllegalArgumentException();for(var h:holidays)LocalDate.parse(h.asText());var days=mapper.readTree(policy.getWorkingDaysJson()==null?"[]":policy.getWorkingDaysJson());if(!days.isArray()||days.isEmpty())throw new IllegalArgumentException();for(var d:days)DayOfWeek.valueOf(d.asText().toUpperCase(Locale.ROOT));}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid SLA timezone, holiday dates or working-days JSON");}
    }
    private Instant calculateSlaDue(Instant start,int minutes,ServiceSlaPolicy policy){
        if(policy.getBusinessStart()==null||policy.getBusinessEnd()==null)return start.plusSeconds(minutes*60L);
        validateCalendar(policy);ZoneId zone=ZoneId.of(policy.getTimeZone());Set<LocalDate> holidays=new HashSet<>();try{for(var h:mapper.readTree(policy.getHolidaysJson()))holidays.add(LocalDate.parse(h.asText()));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid SLA holiday calendar");}
        Set<DayOfWeek> days=new HashSet<>();try{for(var d:mapper.readTree(policy.getWorkingDaysJson()))days.add(DayOfWeek.valueOf(d.asText().toUpperCase(Locale.ROOT)));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid SLA working days");}
        ZonedDateTime cursor=start.atZone(zone);long remaining=minutes;
        for(int guard=0;guard<3660&&remaining>0;guard++){
            LocalDate day=cursor.toLocalDate();
            if(days.contains(day.getDayOfWeek())&&!holidays.contains(day)){
                ZonedDateTime opens=day.atTime(policy.getBusinessStart()).atZone(zone),closes=day.atTime(policy.getBusinessEnd()).atZone(zone);
                if(cursor.isBefore(opens))cursor=opens;
                if(cursor.isBefore(closes)){long available=Duration.between(cursor,closes).toMinutes();if(available>=remaining)return cursor.plusMinutes(remaining).toInstant();remaining-=available;}
            }
            cursor=day.plusDays(1).atTime(policy.getBusinessStart()).atZone(zone);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configured SLA calendar cannot resolve the target within ten years");
    }
    @Transactional public Tenant saveServiceReopenPolicy(int days,Principal p){servicePermission(p);if(p.roles()==null||!p.roles().contains("VENDOR_ADMIN"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(days<0||days>90)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reopen window must be between 0 and 90 days");Tenant t=tenants.findById(p.tenantId()).orElseThrow();t.setServiceReopenDays(days);t.setUpdatedAt(Instant.now());return tenants.save(t);}
    public List<ServiceSlaPolicy> slaPolicies(Principal p){servicePermission(p);return slaPolicies.findByTenantIdAndActiveTrue(p.tenantId());}
    @Transactional public ServiceSlaPolicy saveSlaPolicy(SlaPolicyInput x,Principal p){servicePermission(p);if(p.roles()==null||!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(x.resolutionMinutes()<x.responseMinutes())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Resolution SLA must be at least the response SLA");if((x.businessStart()==null)!=(x.businessEnd()==null)||x.businessStart()!=null&&!x.businessEnd().isAfter(x.businessStart()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Business hours must include a valid start and end time");ServiceSlaPolicy v=slaPolicies.findByTenantIdAndPriority(p.tenantId(),x.priority()).orElseGet(()->{ServiceSlaPolicy n=new ServiceSlaPolicy();n.setId(UUID.randomUUID());n.setTenantId(p.tenantId());n.setPriority(x.priority());n.setCreatedAt(Instant.now());return n;});v.setResponseMinutes(x.responseMinutes());v.setResolutionMinutes(x.resolutionMinutes());v.setTimeZone(x.timeZone());v.setBusinessStart(x.businessStart());v.setBusinessEnd(x.businessEnd());v.setHolidaysJson(x.holidaysJson()==null?"[]":x.holidaysJson());v.setWorkingDaysJson(x.workingDaysJson()==null||x.workingDaysJson().isBlank()?defaultWorkingDays():x.workingDaysJson());validateCalendar(v);v.setActive(true);v.setUpdatedAt(Instant.now());return slaPolicies.save(v);}
    public List<Refund> listRefunds(Principal p){financeAccess(p);permission(p,"PAYMENT_REFUND");return refunds.findByTenantIdOrderByCreatedAtDesc(p.tenantId());}
    @Transactional public ServiceRequest pauseSla(UUID id,String reason,Principal p){servicePermission(p);if(p.roles()==null||!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(reason==null||reason.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"SLA pause reason is required");ServiceRequest t=ticket(id,p.tenantId());if(t.getSlaPausedAt()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"SLA is already paused");if(Set.of("CLOSED","CANCELLED").contains(t.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Closed tickets cannot be paused");t.setSlaPausedAt(Instant.now());t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"SLA_PAUSED",t.getStatus(),t.getStatus(),reason);return t;}
    @Transactional public ServiceRequest resumeSla(UUID id,String reason,Principal p){servicePermission(p);if(p.roles()==null||!p.roles().stream().anyMatch(r->Set.of("VENDOR_ADMIN","SERVICE_MANAGER").contains(r)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);ServiceRequest t=ticket(id,p.tenantId());if(t.getSlaPausedAt()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"SLA is not paused");long seconds=Duration.between(t.getSlaPausedAt(),Instant.now()).getSeconds();t.setPausedSeconds(t.getPausedSeconds()+seconds);if(t.getResponseDueAt()!=null)t.setResponseDueAt(t.getResponseDueAt().plusSeconds(seconds));if(t.getResolutionDueAt()!=null)t.setResolutionDueAt(t.getResolutionDueAt().plusSeconds(seconds));t.setSlaPausedAt(null);t.setUpdatedAt(Instant.now());tickets.save(t);event(t,p,"SLA_RESUMED",t.getStatus(),t.getStatus(),reason==null?"SLA resumed":reason);return t;}
    @Transactional public void evaluateSlaBreaches(){Instant now=Instant.now();for(ServiceRequest t:tickets.findAll()){if(t.getSlaPausedAt()!=null||Set.of("CLOSED","CANCELLED","CUSTOMER_CONFIRMED").contains(t.getStatus()))continue;boolean changed=false;if(t.getFirstRespondedAt()==null&&t.getResponseDueAt()!=null&&now.isAfter(t.getResponseDueAt())&&t.getResponseBreachedAt()==null){t.setResponseBreachedAt(now);ServiceEvent e=new ServiceEvent();e.setId(UUID.randomUUID());e.setTenantId(t.getTenantId());e.setServiceRequestId(t.getId());e.setActorId(t.getAssignedEngineerId());e.setEventType("SLA_RESPONSE_BREACHED");e.setOldStatus(t.getStatus());e.setNewStatus(t.getStatus());e.setReason("Response SLA target exceeded");e.setOccurredAt(now);serviceEvents.save(e);changed=true;}if(t.getResolvedAt()==null&&t.getResolutionDueAt()!=null&&now.isAfter(t.getResolutionDueAt())&&t.getResolutionBreachedAt()==null){t.setResolutionBreachedAt(now);ServiceEvent e=new ServiceEvent();e.setId(UUID.randomUUID());e.setTenantId(t.getTenantId());e.setServiceRequestId(t.getId());e.setActorId(t.getAssignedEngineerId());e.setEventType("SLA_RESOLUTION_BREACHED");e.setOldStatus(t.getStatus());e.setNewStatus(t.getStatus());e.setReason("Resolution SLA target exceeded");e.setOccurredAt(now);serviceEvents.save(e);changed=true;}if(changed)tickets.save(t);}}
    public boolean responseBreached(ServiceRequest t){return t.getResponseBreachedAt()!=null||(t.getSlaPausedAt()==null&&t.getFirstRespondedAt()==null&&t.getResponseDueAt()!=null&&Instant.now().isAfter(t.getResponseDueAt()));}
    public boolean resolutionBreached(ServiceRequest t){return t.getResolutionBreachedAt()!=null||(t.getSlaPausedAt()==null&&t.getResolvedAt()==null&&t.getResolutionDueAt()!=null&&Instant.now().isAfter(t.getResolutionDueAt()));}
}
