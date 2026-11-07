package com.saumyasolar.api;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.api.FinanceModels.*;
import com.saumyasolar.domain.Invoice;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.FinanceService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class FinanceController {
    private final FinanceService finance;
    private final ObjectMapper mapper;
    public FinanceController(FinanceService finance,ObjectMapper mapper){this.finance=finance;this.mapper=mapper;}
    private Principal principal(Authentication a){return (Principal)a.getPrincipal();}

    @GetMapping("/invoices")
    List<?> invoices(Authentication a){return finance.listInvoices(principal(a));}
    @PostMapping("/invoices")
    Invoice createInvoice(@Valid @RequestBody InvoiceCreate x,Authentication a){return finance.createInvoice(x,principal(a));}
    @GetMapping("/invoices/{id}/items")
    List<?> invoiceItems(@PathVariable UUID id,Authentication a){return finance.invoiceLines(id,principal(a));}
    @PostMapping("/invoices/{id}/void")
    Invoice voidInvoice(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication a){return finance.voidInvoice(id,body.get("reason"),principal(a));}
    @GetMapping(value="/invoices/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> invoicePdf(@PathVariable UUID id,Authentication a)throws Exception{
        Invoice i=finance.listInvoices(principal(a)).stream().filter(v->v.getId().equals(id)).findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));
        var lines=finance.invoiceLines(id,principal(a));
        try(var out=new ByteArrayOutputStream()){
            Document d=new Document();PdfWriter.getInstance(d,out);d.open();
            d.add(new Paragraph("SAUMYA SOLAR - TAX INVOICE"));
            d.add(new Paragraph("Invoice: "+i.getInvoiceNumber()+" | Fiscal year: "+i.getFiscalYear()+" | Status: "+i.getStatus()));
            d.add(new Paragraph("Supplier: "+i.getVendorName()+" | GSTIN: "+safe(i.getVendorGstin())));
            d.add(new Paragraph("Bill to: "+i.getCustomerName()+" | GSTIN: "+safe(i.getCustomerGstin())));
            d.add(new Paragraph("Email: "+safe(i.getCustomerEmail())+" | Address: "+safe(i.getCustomerAddress())));
            d.add(new Paragraph("Tax treatment: "+safe(i.getTaxTreatment())+" (confirm treatment with accounting owner before statutory use)"));
            PdfPTable table=new PdfPTable(new float[]{4,1,2,1,2,2});table.setWidthPercentage(100);
            for(String h:new String[]{"Description","Qty","HSN","Tax %","Taxable","Tax"})table.addCell(h);
            for(Object value:lines){var x=(com.saumyasolar.domain.InvoiceItem)value;table.addCell(safe(x.getDescription()));table.addCell(String.valueOf(x.getQuantity()));table.addCell(safe(x.getHsnCode()));table.addCell(String.valueOf(x.getTaxPercent()));table.addCell(String.valueOf(x.getTaxableAmount()));table.addCell(String.valueOf(x.getTaxAmount()));}
            d.add(table);d.add(new Paragraph("Taxable subtotal: INR "+i.getSubtotal()));d.add(new Paragraph("Tax total: INR "+i.getTaxTotal()));d.add(new Paragraph("Grand total: INR "+i.getGrandTotal()));d.add(new Paragraph("Due date: "+safe(i.getDueDate())));d.close();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\\""+i.getInvoiceNumber()+".pdf\\"").body(out.toByteArray());
        }
    }
    @GetMapping("/payments") List<?> payments(Authentication a){return finance.listPayments(principal(a));}
    @PostMapping("/payments") Object recordPayment(@Valid @RequestBody ManualPayment x,Authentication a){return finance.recordPayment(x,principal(a));}
    @PostMapping("/payments/{id}/refund") Object refund(@PathVariable UUID id,@Valid @RequestBody RefundBody body,Authentication a){return finance.refund(new RefundCreate(id,body.amount(),body.reason()),principal(a));}
    public record RefundBody(java.math.BigDecimal amount,String reason){}
    @GetMapping("/refunds") List<?> refunds(Authentication a){return finance.listRefunds(principal(a));}

    @PostMapping("/payment-webhooks/{provider}")
    Map<String,Object> paymentWebhook(@PathVariable String provider,@RequestHeader("X-Payment-Signature") String signature,@RequestBody String raw){
        try{return finance.webhook(provider,raw,signature,mapper.readValue(raw,WebhookEvent.class));}
        catch(org.springframework.web.server.ResponseStatusException e){throw e;}
        catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid payment webhook payload");}
    }

    @GetMapping("/service-requests") List<?> serviceRequests(Authentication a){return finance.listTickets(principal(a));}
    @PostMapping("/service-requests") Object createServiceRequest(@Valid @RequestBody ServiceCreate x,Authentication a){return finance.createTicket(x,principal(a));}
    @PostMapping("/service-requests/{id}/assign") Object assignService(@PathVariable UUID id,@Valid @RequestBody ServiceAssign x,Authentication a){return finance.assignTicket(id,x,principal(a));}
    @PostMapping("/service-requests/{id}/visits") Object scheduleVisit(@PathVariable UUID id,@Valid @RequestBody ServiceVisitCreate x,Authentication a){return finance.scheduleVisit(id,x,principal(a));}
    @PostMapping("/service-requests/{id}/visits/{visitId}/complete") Object completeVisit(@PathVariable UUID id,@PathVariable UUID visitId,@Valid @RequestBody ServiceVisitComplete x,Authentication a){return finance.completeVisit(id,visitId,x,principal(a));}
    @PostMapping("/service-requests/{id}/confirm") Object confirmService(@PathVariable UUID id,Authentication a){return finance.confirmService(id,principal(a));}
    @PostMapping("/service-requests/{id}/close") Object closeService(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication a){return finance.closeTicket(id,body.get("reason"),principal(a));}
    @PostMapping("/service-requests/{id}/reopen") Object reopenService(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication a){return finance.reopenTicket(id,body.get("reason"),principal(a));}
    @GetMapping("/service-requests/{id}/visits") List<?> serviceVisits(@PathVariable UUID id,Authentication a){return finance.serviceVisits(id,principal(a));}
    @GetMapping("/service-requests/{id}/history") List<?> serviceHistory(@PathVariable UUID id,Authentication a){return finance.serviceHistory(id,principal(a));}
    @GetMapping("/service-sla-policies") List<?> slaPolicies(Authentication a){return finance.slaPolicies(principal(a));}
    @PostMapping("/service-sla-policies") Object saveSlaPolicy(@Valid @RequestBody SlaPolicyInput x,Authentication a){return finance.saveSlaPolicy(x,principal(a));}
    private static String safe(Object value){return value==null?"":String.valueOf(value);}
}
