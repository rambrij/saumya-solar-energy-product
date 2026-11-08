package com.saumyasolar.api;
import com.saumyasolar.domain.*;import com.saumyasolar.security.Principal;import com.saumyasolar.service.EnterpriseSaasService;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;import java.time.Instant;import java.util.*;
@RestController @RequestMapping("/api/v1")
public class EnterpriseSaasController {
 private final EnterpriseSaasService service;private final com.saumyasolar.service.ApprovalWorkflowService approvalWorkflows;public EnterpriseSaasController(EnterpriseSaasService s,com.saumyasolar.service.ApprovalWorkflowService a){service=s;approvalWorkflows=a;}
 private Principal p(Authentication a){return (Principal)a.getPrincipal();}
 public record BranchRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=150) String name,@Size(max=700) String address,@Size(max=40) String phone,@Email @Size(max=254) String email){}
 public record BranchUserRequest(@NotNull UUID userId){}
 public record PlanRequest(@NotBlank @Size(max=50) String planCode,@NotBlank @Size(max=120) String name,@Size(max=1000) String description,
  @NotBlank String billingCycle,@NotNull @DecimalMin("0.00") BigDecimal price,@Pattern(regexp="[A-Z]{3}") String currency,
  @Min(1) @Max(10000) int maxUsers,@Min(1) @Max(1000) int maxBranches,@Min(1) @Max(1000) int maxWarehouses,
  @Min(0) long maxStorageMb,@NotBlank String entitlementsJson){}
 public record WarehouseBranchRequest(UUID branchId){}
 public record BrandingRequest(@NotBlank @Size(max=150) String displayName,@Size(max=1000) String logoUrl,@NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String primaryColor,@NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String secondaryColor,@Email @Size(max=254) String contactEmail,@Size(max=40) String contactPhone,@Email @Size(max=254) String supportEmail,@Size(max=40) String supportPhone){}
 public record DomainRequest(@NotBlank @Size(max=253) String domain){}
 public record WorkflowRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=150) String name,@NotBlank String entityType,@NotBlank String stepsJson){}
 public record ApprovalDecisionRequest(@NotBlank String decision,@Size(max=1000) String comments){}
 public record SubscriptionRequest(@NotNull UUID planId,@NotBlank String status,Instant startsAt,Instant endsAt,Instant renewsAt,@Size(max=160) String externalReference){}
 @GetMapping("/approval-workflows") public List<ApprovalWorkflow> approvalWorkflows(Authentication a){return approvalWorkflows.list(p(a));}
 @PostMapping("/approval-workflows") public ApprovalWorkflow createApprovalWorkflow(@Valid @RequestBody WorkflowRequest x,Authentication a){return approvalWorkflows.create(new com.saumyasolar.service.ApprovalWorkflowService.WorkflowInput(x.code(),x.name(),x.entityType(),x.stepsJson()),p(a));}
 @GetMapping("/approval-instances/{id}") public ApprovalInstance approvalInstance(@PathVariable UUID id,Authentication a){return approvalWorkflows.get(id,p(a));}
 @PostMapping("/approval-instances/{id}/decisions") public ApprovalInstance decideApproval(@PathVariable UUID id,@Valid @RequestBody ApprovalDecisionRequest x,Authentication a){return approvalWorkflows.decide(id,x.decision().toUpperCase(Locale.ROOT),x.comments(),p(a));}
 @GetMapping("/vendor/branding") public EnterpriseSaasService.BrandingView branding(Authentication a){return service.getBranding(p(a));}
 @PatchMapping("/vendor/branding") public EnterpriseSaasService.BrandingView updateBranding(@Valid @RequestBody BrandingRequest x,Authentication a){
  return service.updateBranding(new EnterpriseSaasService.BrandingInput(x.displayName(),x.logoUrl(),x.primaryColor(),x.secondaryColor(),x.contactEmail(),x.contactPhone(),x.supportEmail(),x.supportPhone()),p(a));
 }
 @GetMapping("/public/{slug}/branding") public EnterpriseSaasService.BrandingView publicBranding(@PathVariable String slug){return service.publicBranding(slug);}
 @GetMapping("/vendor/domains") public List<TenantDomain> domains(Authentication a){return service.domains(p(a));}
 @PostMapping("/vendor/domains") public TenantDomain requestDomain(@Valid @RequestBody DomainRequest x,Authentication a){return service.requestDomain(x.domain(),p(a));}
 @PostMapping("/vendor/domains/{id}/verify") public TenantDomain verifyDomain(@PathVariable UUID id,Authentication a){return service.verifyDomain(id,p(a));}
 @GetMapping("/branches") public List<Branch> branches(Authentication a){return service.branches(p(a));}
 @PostMapping("/branches") public Branch createBranch(@Valid @RequestBody BranchRequest x,Authentication a){return service.createBranch(new EnterpriseSaasService.BranchInput(x.code(),x.name(),x.address(),x.phone(),x.email()),p(a));}
 @PutMapping("/branches/{id}") public Branch updateBranch(@PathVariable UUID id,@Valid @RequestBody BranchRequest x,Authentication a){return service.updateBranch(id,new EnterpriseSaasService.BranchInput(x.code(),x.name(),x.address(),x.phone(),x.email()),p(a));}
 @PostMapping("/branches/{id}/activate") public Map<String,Object> activate(@PathVariable UUID id,Authentication a){service.setBranchActive(id,true,p(a));return Map.of("updated",true);}
 @PostMapping("/branches/{id}/deactivate") public Map<String,Object> deactivate(@PathVariable UUID id,Authentication a){service.setBranchActive(id,false,p(a));return Map.of("updated",true);}
 @PostMapping("/branches/{id}/users") public Map<String,Object> assignUser(@PathVariable UUID id,@Valid @RequestBody BranchUserRequest x,Authentication a){service.assignUser(id,x.userId(),p(a));return Map.of("assigned",true);}
 @GetMapping("/subscription-plans") public List<SubscriptionPlan> plans(){return service.plans();}
 @PostMapping("/platform/subscription-plans") public SubscriptionPlan createPlan(@Valid @RequestBody PlanRequest x,Authentication a){return service.createPlan(new EnterpriseSaasService.PlanInput(x.planCode(),x.name(),x.description(),x.billingCycle(),x.price(),x.currency()==null?"INR":x.currency(),x.maxUsers(),x.maxBranches(),x.maxWarehouses(),x.maxStorageMb(),x.entitlementsJson()),p(a));}
 @PostMapping("/platform/tenants/{tenantId}/subscriptions") public TenantSubscription assignSubscription(@PathVariable UUID tenantId,@Valid @RequestBody SubscriptionRequest x,Authentication a){
  if(!Set.of("TRIAL","ACTIVE").contains(x.status()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"New subscriptions must be TRIAL or ACTIVE");
  return service.assignSubscription(tenantId,x.planId(),new EnterpriseSaasService.SubscriptionInput(x.status(),x.startsAt(),x.endsAt(),x.renewsAt(),x.externalReference()),p(a));
 }
 @PatchMapping("/warehouses/{id}/branch") public Warehouse assignWarehouseBranch(@PathVariable UUID id,@RequestBody WarehouseBranchRequest x,Authentication a){return service.assignWarehouseBranch(id,x.branchId(),p(a));}
 @GetMapping("/subscription") public Map<String,Object> currentSubscription(Authentication a){return service.currentSubscription(p(a));}
 @GetMapping("/audit-events") public List<AuditEvent> auditEvents(@RequestParam Instant from,@RequestParam Instant to,Authentication a){
  if(to.isBefore(from)||java.time.Duration.between(from,to).toDays()>90)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Audit range must be valid and no longer than 90 days");
  return service.auditEvents(p(a),from,to);
 }
}
