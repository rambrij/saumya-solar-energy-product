package com.saumyasolar.service;

import com.saumyasolar.domain.Invoice;
import com.saumyasolar.domain.Payment;
import com.saumyasolar.domain.Refund;
import com.saumyasolar.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class DashboardService {
    private final AppService app;
    private final FinanceService finance;
    private final OperationsService operations;

    public DashboardService(AppService app, FinanceService finance, OperationsService operations) {
        this.app=app;this.finance=finance;this.operations=operations;
    }

    private boolean has(Principal p,String permission) {
        return p.permissions()!=null&&p.permissions().contains(permission);
    }
    @SuppressWarnings("unchecked")
    private List<?> rows(String type,Principal p) {
        Object value=app.list(type,p);
        return value instanceof List<?> list?list:List.of();
    }

    public Map<String,Object> vendor(Principal p) {
        if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Vendor dashboard access is required");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("generatedAt",Instant.now());
        if(has(p,"CUSTOMER_MANAGE")) result.put("customers",rows("customers",p).size());
        if(has(p,"LEAD_MANAGE")) {
            List<?> enquiries=rows("enquiries",p), leads=rows("leads",p);
            result.put("enquiries",enquiries.size());
            result.put("leads",leads.stream().filter(x->{
                try {String stage=String.valueOf(x.getClass().getMethod("getStage").invoke(x));return !Set.of("WON","LOST").contains(stage);}
                catch(Exception ignored){return false;}
            }).count());
            result.put("recentEnquiries",enquiries.stream().limit(5).toList());
        }
        if(has(p,"QUOTATION_MANAGE")) {
            List<?> quotes=rows("quotations",p);
            result.put("openQuotations",quotes.stream().filter(x->{
                try {String status=String.valueOf(x.getClass().getMethod("getStatus").invoke(x));return !Set.of("ACCEPTED","REJECTED","CANCELLED").contains(status);}
                catch(Exception ignored){return false;}
            }).count());
        }
        List<?> orders=List.of();
        if(has(p,"ORDER_CREATE")) {orders=rows("orders",p);result.put("orders",orders.size());}
        if(has(p,"INVENTORY_MANAGE")) {
            List<?> low=operations.lowStock(p);
            result.put("lowStockItems",low.size());
        }
        if(has(p,"FINANCE_MANAGE")) {
            List<Invoice> invoices=finance.listInvoices(p);
            List<Payment> payments=finance.listPayments(p);
            List<Refund> refunds=has(p,"PAYMENT_REFUND")?finance.listRefunds(p):List.of();
            BigDecimal invoiced=invoices.stream().filter(i->!"VOID".equals(i.getStatus()))
                    .map(Invoice::getGrandTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal outstanding=BigDecimal.ZERO;
            for(Invoice invoice:invoices) {
                if(Set.of("VOID","CREDITED").contains(invoice.getStatus())) continue;
                BigDecimal paid=payments.stream().filter(payment->invoice.getId().equals(payment.getInvoiceId())
                                &&Set.of("CONFIRMED","PARTIALLY_REFUNDED").contains(payment.getStatus()))
                        .map(payment->{
                            BigDecimal refunded=refunds.stream().filter(refund->payment.getId().equals(refund.getPaymentId())&&"RECORDED".equals(refund.getStatus()))
                                    .map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
                            return payment.getAmount().subtract(refunded).max(BigDecimal.ZERO);
                        }).reduce(BigDecimal.ZERO,BigDecimal::add);
                outstanding=outstanding.add(invoice.getGrandTotal().subtract(paid).max(BigDecimal.ZERO));
            }
            Set<UUID> invoicedOrders=new HashSet<>();invoices.forEach(i->invoicedOrders.add(i.getOrderId()));
            long awaitingInvoice=orders.stream().filter(order->{
                try{return !invoicedOrders.contains((UUID)order.getClass().getMethod("getId").invoke(order));}
                catch(Exception ignored){return false;}
            }).count();
            result.put("invoicedValue",invoiced);
            result.put("outstandingPayments",outstanding);
            result.put("awaitingInvoice",awaitingInvoice);
        }
        if(has(p,"SERVICE_MANAGE")) {
            List<?> tickets=finance.listTickets(p);
            result.put("openServiceRequests",tickets.stream().filter(ticket->{
                try {String status=String.valueOf(ticket.getClass().getMethod("getStatus").invoke(ticket));return !Set.of("CLOSED","CUSTOMER_CONFIRMED").contains(status);}
                catch(Exception ignored){return false;}
            }).count());
        }
        return result;
    }

    public Map<String,Object> customer(Principal p) {
        if(!"CUSTOMER".equals(p.accountType())||p.tenantId()==null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer dashboard access is required");
        List<?> enquiries=rows("customer-enquiries",p);
        List<?> quotes=rows("quotations",p);
        List<?> orders=rows("customer-orders",p);
        List<Invoice> invoices=finance.listInvoices(p);
        List<?> installations=operations.customerInstallations(p);
        List<?> warranties=operations.customerWarranties(p);
        List<?> tickets=finance.listTickets(p);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("generatedAt",Instant.now());
        result.put("enquiries",enquiries.size());
        result.put("pendingQuotations",quotes.stream().filter(x->{
            try{return Set.of("SENT","PENDING_APPROVAL").contains(String.valueOf(x.getClass().getMethod("getStatus").invoke(x)));}
            catch(Exception ignored){return false;}
        }).count());
        result.put("orders",orders.size());
        result.put("invoices",invoices.size());
        result.put("installations",installations.size());
        result.put("warranties",warranties.size());
        result.put("openServiceRequests",tickets.stream().filter(x->{
            try{return !Set.of("CLOSED","CUSTOMER_CONFIRMED").contains(String.valueOf(x.getClass().getMethod("getStatus").invoke(x)));}
            catch(Exception ignored){return false;}
        }).count());
        return result;
    }
}
