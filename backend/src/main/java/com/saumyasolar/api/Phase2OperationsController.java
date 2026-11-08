package com.saumyasolar.api;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;import org.springframework.http.HttpHeaders;import org.springframework.http.MediaType;import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Tenant-scoped Phase 2 engineer, warranty-claim and operational-report endpoints. */
@RestController
@RequestMapping("/api/v1")
public class Phase2OperationsController {
 private final EngineerProfileRepository engineers; private final WarrantyClaimRepository claims;
 private final WarrantyRepository warranties; private final CustomerProfileRepository customers;
 private final AppUserRepository users; private final InstallationJobRepository installations;
 private final CustomerOrderRepository orders; private final ServiceRequestRepository tickets;
 private final InventoryTransactionRepository inventory; private final InvoiceRepository invoices;
 private final PaymentRepository payments; private final SalesLeadRepository leads;
 public Phase2OperationsController(EngineerProfileRepository engineers,WarrantyClaimRepository claims,
  WarrantyRepository warranties,CustomerProfileRepository customers,AppUserRepository users,
  InstallationJobRepository installations,CustomerOrderRepository orders,ServiceRequestRepository tickets,
  InventoryTransactionRepository inventory,InvoiceRepository invoices,PaymentRepository payments,SalesLeadRepository leads){
  this.engineers=engineers;this.claims=claims;this.warranties=warranties;this.customers=customers;this.users=users;
  this.installations=installations;this.orders=orders;this.tickets=tickets;this.inventory=inventory;
  this.invoices=invoices;this.payments=payments;this.leads=leads;
 }
 private Principal p(Authentication a){return (Principal)a.getPrincipal();}
 private UUID tenant(Principal p){if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p.tenantId();}
 private void permission(Principal p,String code){if(p.permissions()==null||!p.permissions().contains(code))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Missing permission: "+code);}
 private boolean hasRole(Principal p,String...roles){return p.roles()!=null&&p.roles().stream().anyMatch(Set.of(roles)::contains);}
 private void ops(Principal p){tenant(p);permission(p,"SURVEY_MANAGE");if(!hasRole(p,"VENDOR_ADMIN","OPERATIONS_MANAGER","SERVICE_MANAGER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 public record EngineerInput(@NotNull UUID userId,@NotNull List<@NotBlank String> skills,
  @NotNull List<@NotBlank String> certifications,@Size(max=500) String territory,
  String availableFrom,String availableUntil,@Min(1) @Max(50) int maxConcurrentJobs){}
 public record ClaimInput(@NotNull UUID warrantyId,@NotBlank @Size(max=200) String subject,
  @NotBlank @Size(max=4000) String description,@Size(max=1000) String evidenceUrl){}
 public record ClaimDecision(@NotBlank @Size(max=1000) String reason){}
 public record ClaimResolution(@NotBlank @Size(max=2000) String notes){}
 @GetMapping("/engineers") public List<Map<String,Object>> engineers(Authentication a){
  Principal p=p(a);ops(p);
  List<Map<String,Object>> out=new ArrayList<>();
  for(EngineerProfile e:engineers.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(p.tenantId())){
   AppUser u=users.findById(e.getUserId()).filter(x->p.tenantId().equals(x.getTenantId())&&x.isEnabled()).orElse(null);
   if(u==null)continue;
   long active=installations.findByTenantIdOrderByScheduledAtAsc(p.tenantId()).stream().filter(j->e.getUserId().equals(j.getAssignedEngineerId())&&Set.of("SCHEDULED","IN_PROGRESS").contains(j.getStatus())).count();
   Map<String,Object> row=new LinkedHashMap<>();row.put("id",e.getId());row.put("userId",e.getUserId());row.put("name",u.getName());row.put("email",u.getEmail());row.put("skills",e.getSkillsJson());row.put("certifications",e.getCertificationsJson());row.put("territory",e.getTerritory());row.put("availableFrom",e.getAvailableFrom());row.put("availableUntil",e.getAvailableUntil());row.put("maxConcurrentJobs",e.getMaxConcurrentJobs());row.put("activeJobs",active);row.put("availableCapacity",Math.max(0,e.getMaxConcurrentJobs()-(int)active));out.add(row);
  }return out;
 }
 @PostMapping("/engineers") @Transactional public EngineerProfile saveEngineer(@Valid @RequestBody EngineerInput x,Authentication a){
  Principal p=p(a);ops(p);
  AppUser u=users.findById(x.userId()).filter(v->p.tenantId().equals(v.getTenantId())&&v.isEnabled()&&v.getRoles().stream().anyMatch(r->Set.of("SERVICE_ENGINEER","TECHNICIAN","INSTALLER","OPERATIONS_MANAGER").contains(r.getName()))).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"User must be an enabled same-tenant engineer or installer"));
  var from=parseTime(x.availableFrom());var until=parseTime(x.availableUntil());
  if((from==null)!=(until==null)||from!=null&&!until.isAfter(from))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Availability requires a valid start and end time");
  EngineerProfile e=engineers.findByTenantIdAndUserId(p.tenantId(),u.getId()).orElseGet(()->{EngineerProfile n=new EngineerProfile();n.setId(UUID.randomUUID());n.setTenantId(p.tenantId());n.setUserId(u.getId());n.setCreatedAt(Instant.now());return n;});
  e.setSkillsJson(jsonStrings(x.skills()));e.setCertificationsJson(jsonStrings(x.certifications()));e.setTerritory(x.territory());e.setAvailableFrom(from);e.setAvailableUntil(until);e.setMaxConcurrentJobs(x.maxConcurrentJobs());e.setActive(true);e.setUpdatedAt(Instant.now());return engineers.save(e);
 }
 private java.time.LocalTime parseTime(String s){try{return s==null||s.isBlank()?null:java.time.LocalTime.parse(s);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Time must use HH:mm");}}
 private String jsonStrings(List<String> values){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(values.stream().map(String::trim).filter(s->!s.isBlank()).distinct().toList());}catch(Exception e){throw new IllegalStateException(e);}}
 @PatchMapping("/customer/messaging-consent") @Transactional public CustomerProfile updateMessagingConsent(@Valid @RequestBody CustomerMessagingConsent x,Authentication a){Principal p=p(a);CustomerProfile c=customer(p);c.setSmsConsent(x.smsConsent());c.setWhatsappConsent(x.whatsappConsent());c.setMessagingConsentUpdatedAt(Instant.now());return customers.save(c);}
 @GetMapping("/customer/messaging-consent") public Map<String,Object> messagingConsent(Authentication a){Principal p=p(a);CustomerProfile c=customer(p);return Map.of("smsConsent",c.isSmsConsent(),"whatsappConsent",c.isWhatsappConsent(),"updatedAt",c.getMessagingConsentUpdatedAt()==null?"":c.getMessagingConsentUpdatedAt().toString());}
 @GetMapping("/warranty-claims") public List<WarrantyClaim> listClaims(Authentication a){Principal p=p(a);ops(p);return claims.findByTenantIdOrderByCreatedAtDesc(p.tenantId());}
 @GetMapping("/customer/warranty-claims") public List<WarrantyClaim> customerClaims(Authentication a){Principal p=p(a);CustomerProfile c=customer(p);return claims.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(p.tenantId(),c.getId());}
 @PostMapping("/customer/warranty-claims") @Transactional public WarrantyClaim createClaim(@Valid @RequestBody ClaimInput x,Authentication a){
  Principal p=p(a);CustomerProfile c=customer(p);
  Warranty w=warranties.findById(x.warrantyId()).filter(v->p.tenantId().equals(v.getTenantId())&&c.getId().equals(v.getCustomerId())&&"ACTIVE".equals(v.getStatus())&&!LocalDate.now().isBefore(v.getStartDate())&&!LocalDate.now().isAfter(v.getEndDate())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Active warranty not found"));
  WarrantyClaim claim=new WarrantyClaim();claim.setId(UUID.randomUUID());claim.setTenantId(p.tenantId());claim.setWarrantyId(w.getId());claim.setCustomerId(c.getId());claim.setClaimNumber("WCL-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));claim.setSubject(x.subject().trim());claim.setDescription(x.description().trim());claim.setEvidenceUrl(x.evidenceUrl());claim.setStatus("SUBMITTED");claim.setCreatedAt(Instant.now());claim.setUpdatedAt(Instant.now());return claims.save(claim);
 }
 @PostMapping("/warranty-claims/{id}/approve") @Transactional public WarrantyClaim approveClaim(@PathVariable UUID id,@Valid @RequestBody ClaimDecision x,Authentication a){return decideClaim(id,x.reason(),"APPROVED",a);}
 @PostMapping("/warranty-claims/{id}/reject") @Transactional public WarrantyClaim rejectClaim(@PathVariable UUID id,@Valid @RequestBody ClaimDecision x,Authentication a){return decideClaim(id,x.reason(),"REJECTED",a);}
 private WarrantyClaim decideClaim(UUID id,String reason,String status,Authentication a){Principal p=p(a);ops(p);WarrantyClaim c=claim(id,p.tenantId());if(!Set.of("SUBMITTED","UNDER_REVIEW").contains(c.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Claim is not awaiting a decision");c.setStatus(status);c.setDecisionReason(reason.trim());c.setReviewedBy(p.userId());c.setReviewedAt(Instant.now());c.setUpdatedAt(Instant.now());return claims.save(c);}
 @PostMapping("/warranty-claims/{id}/resolve") @Transactional public WarrantyClaim resolveClaim(@PathVariable UUID id,@Valid @RequestBody ClaimResolution x,Authentication a){Principal p=p(a);ops(p);WarrantyClaim c=claim(id,p.tenantId());if(!"APPROVED".equals(c.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only approved claims can be resolved");c.setStatus("RESOLVED");c.setResolutionNotes(x.notes().trim());c.setUpdatedAt(Instant.now());return claims.save(c);}
 private WarrantyClaim claim(UUID id,UUID t){return claims.findByIdAndTenantId(id,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Claim not found"));}
 private CustomerProfile customer(Principal p){if(!"CUSTOMER".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);return customers.findByTenantIdAndUserId(p.tenantId(),p.userId()).filter(CustomerProfile::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 @GetMapping("/reports/operations") public Map<String,Object> operationsReport(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,Authentication a){
  Principal p=p(a);ops(p);if(from!=null&&to!=null&&to.isBefore(from))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"to must not precede from");
  UUID t=p.tenantId();Instant start=from==null?Instant.EPOCH:from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();Instant end=to==null?Instant.now():to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
  var orderRows=orders.findByTenantIdOrderByIdDesc(t).stream().filter(x->x.getCreatedAt()!=null&&!x.getCreatedAt().isBefore(start)&&x.getCreatedAt().isBefore(end)).toList();
  var installationRows=installations.findByTenantIdOrderByScheduledAtAsc(t).stream().filter(x->x.getScheduledAt()!=null&&!x.getScheduledAt().isBefore(start)&&x.getScheduledAt().isBefore(end)).toList();
  var ticketRows=tickets.findByTenantIdOrderByCreatedAtDesc(t).stream().filter(x->x.getCreatedAt()!=null&&!x.getCreatedAt().isBefore(start)&&x.getCreatedAt().isBefore(end)).toList();
  var invoiceRows=invoices.findByTenantIdOrderByCreatedAtDesc(t).stream().filter(x->x.getCreatedAt()!=null&&!x.getCreatedAt().isBefore(start)&&x.getCreatedAt().isBefore(end)).toList();
  var paymentRows=payments.findByTenantIdOrderByCreatedAtDesc(t).stream().filter(x->x.getCreatedAt()!=null&&!x.getCreatedAt().isBefore(start)&&x.getCreatedAt().isBefore(end)).toList();
  var leadRows=leads.findByTenantIdOrderByIdDesc(t).stream().filter(x->x.getCreatedAt()!=null&&!x.getCreatedAt().isBefore(start)&&x.getCreatedAt().isBefore(end)).toList();
  var warrantyRows=warranties.findByTenantIdOrderByStartDateDesc(t).stream().filter(x->x.getStartDate()!=null&&!x.getStartDate().isBefore(from==null?LocalDate.MIN:from)&&!x.getStartDate().isAfter(to==null?LocalDate.MAX:to)).toList();
  var stockRows=inventory.findByTenantIdOrderByCreatedAtDesc(t).stream().filter(x->x.getCreatedAt()!=null&&!x.getCreatedAt().isBefore(start)&&x.getCreatedAt().isBefore(end)).toList();
  BigDecimal paymentTotal=paymentRows.stream().filter(x->"SUCCEEDED".equals(x.getStatus())||"PAID".equals(x.getStatus())).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
  Map<String,Object> result=new LinkedHashMap<>();result.put("from",from);result.put("to",to);result.put("orders",orderRows.size());result.put("orderValue",orderRows.stream().map(CustomerOrder::getTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add));result.put("leads",leadRows.size());result.put("installations",installationRows.size());result.put("installationsCompleted",installationRows.stream().filter(x->Set.of("COMPLETED","COMMISSIONED").contains(x.getStatus())).count());result.put("serviceTickets",ticketRows.size());result.put("serviceOpen",ticketRows.stream().filter(x->!Set.of("CLOSED","RESOLVED","CUSTOMER_CONFIRMED").contains(x.getStatus())).count());result.put("warranties",warrantyRows.size());result.put("invoices",invoiceRows.size());result.put("invoicedAmount",invoiceRows.stream().map(Invoice::getGrandTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add));result.put("paymentsReceived",paymentTotal);result.put("inventoryTransactions",stockRows.size());return result;
 }

 @GetMapping(value="/reports/operations/export.csv",produces="text/csv")
 public ResponseEntity<byte[]> exportOperationsCsv(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,Authentication a){
  Map<String,Object> report=operationsReport(from,to,a);StringBuilder csv=new StringBuilder("Metric,Value\\n");
  report.forEach((key,value)->csv.append(key).append(',').append(value==null?"":value).append('\\n'));
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=operations-report.csv").contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }

}
