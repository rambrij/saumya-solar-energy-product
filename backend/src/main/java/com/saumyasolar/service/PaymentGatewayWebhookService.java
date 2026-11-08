package com.saumyasolar.service;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Service
public class PaymentGatewayWebhookService {
 private final PaymentGatewayRegistry adapters;private final PaymentRepository payments;
 private final PaymentWebhookEventRepository events;private final InvoiceRepository invoices;
 public PaymentGatewayWebhookService(PaymentGatewayRegistry adapters,PaymentRepository payments,PaymentWebhookEventRepository events,InvoiceRepository invoices){
  this.adapters=adapters;this.payments=payments;this.events=events;this.invoices=invoices;
 }
 @Transactional public Map<String,Object> accept(String provider,String raw,String signature){
  PaymentGatewayAdapter adapter=adapters.require(provider);
  if(!adapter.verifyWebhook(raw,signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid payment provider signature");
  PaymentGatewayAdapter.GatewayWebhookEvent event;
  try{event=adapter.parseWebhook(raw);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid payment provider event");}
  if(event==null||event.eventId()==null||event.eventId().isBlank()||event.eventId().length()>160
    ||event.providerReference()==null||event.providerReference().isBlank()||event.amount()==null||event.amount().signum()<=0
    ||event.currency()==null||!event.currency().matches("[A-Z]{3}")||!Set.of("SUCCEEDED","FAILED").contains(event.status()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment provider event is incomplete or unsupported");
  String code=provider.toUpperCase(Locale.ROOT);
  if(events.findByProviderAndEventId(code,event.eventId()).isPresent())return Map.of("accepted",true,"duplicate",true);
  PaymentWebhookEvent record=new PaymentWebhookEvent();record.setId(UUID.randomUUID());record.setProvider(code);record.setEventId(event.eventId());
  record.setPayloadHash(hash(raw));record.setProcessingStatus("RECEIVED");record.setReceivedAt(Instant.now());events.saveAndFlush(record);
  Payment payment=payments.findByProviderAndProviderReference(code,event.providerReference()).orElse(null);
  if(payment==null){record.setProcessingStatus("UNMATCHED");record.setProcessedAt(Instant.now());events.save(record);return Map.of("accepted",true,"matched",false);}
  if(payment.getAmount().compareTo(event.amount())!=0||!payment.getCurrency().equalsIgnoreCase(event.currency()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment provider amount or currency does not match");
  if("PENDING".equals(payment.getStatus())){
   payment.setStatus("SUCCEEDED".equals(event.status())?"CONFIRMED":"FAILED");
   if("CONFIRMED".equals(payment.getStatus()))payment.setPaidAt(Instant.now());payments.save(payment);
   Invoice invoice=invoices.findForUpdate(payment.getInvoiceId(),payment.getTenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found"));
   BigDecimal total=payments.findByTenantIdAndInvoiceId(payment.getTenantId(),invoice.getId()).stream()
    .filter(x->"CONFIRMED".equals(x.getStatus())).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
   invoice.setStatus(total.compareTo(invoice.getGrandTotal())>=0?"PAID":total.signum()>0?"PARTIALLY_PAID":"ISSUED");invoices.save(invoice);
  }
  record.setProcessingStatus("PROCESSED");record.setProcessedAt(Instant.now());events.save(record);
  return Map.of("accepted",true,"matched",true,"duplicate",false);
 }
 private String hash(String raw){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
