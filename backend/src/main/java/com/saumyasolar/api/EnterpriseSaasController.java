package com.saumyasolar.api;
import io.swagger.v3.oas.annotations.Operation;
import com.saumyasolar.domain.*;import com.saumyasolar.security.Principal;import com.saumyasolar.service.EnterpriseSaasService;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;import java.time.Instant;import java.util.*;
@RestController @RequestMapping("/api/v1")
public class EnterpriseSaasController {
 private final EnterpriseSaasService service;private final com.saumyasolar.service.ApprovalWorkflowService approvalWorkflows;private final com.saumyasolar.service.IntegrationManagementService integrations;public EnterpriseSaasController(EnterpriseSaasService s,com.saumyasolar.service.ApprovalWorkflowService a,com.saumyasolar.service.IntegrationManagementService i){service=s;approvalWorkflows=a;integrations=i;}
 private Principal p(Authentication a){return (Principal)a.getPrincipal();}
 private EnterpriseSaasService.PlanInput planInput(PlanRequest x){return new EnterpriseSaasService.PlanInput(x.planCode(),x.name(),x.description(),x.billingCycle(),x.price(),x.currency()==null?"INR":x.currency(),x.maxUsers(),x.maxBranches(),x.maxWarehouses(),x.maxStorageMb(),x.maxProducts(),x.maxCustomers(),x.trialPeriodDays(),x.displayOrder(),x.entitlementsJson());}
 public record BranchRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=150) String name,@Size(max=700) String address,@Size(max=40) String phone,@Email @Size(max=254) String email){}
 public record BranchUserRequest(@NotNull UUID userId){}
 public record PlanRequest(@NotBlank @Size(max=50) String planCode,@NotBlank @Size(max=120) String name,@Size(max=1000) String description,
  @NotBlank String billingCycle,@NotNull @DecimalMin("0.00") BigDecimal price,@Pattern(regexp="[A-Z]{3}") String currency,
  @Min(1) @Max(10000) int maxUsers,@Min(1) @Max(1000) int maxBranches,@Min(1) @Max(1000) int maxWarehouses,
  @Min(0) long maxStorageMb,@Min(1) int maxProducts,@Min(1) int maxCustomers,@Min(0) int trialPeriodDays,@Min(0) int displayOrder,@NotBlank String entitlementsJson){}
 public record WarehouseBranchRequest(UUID branchId){}
 public record BrandingRequest(@NotBlank @Size(max=150) String displayName,@Size(max=1000) String logoUrl,@NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String primaryColor,@NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String secondaryColor,@Email @Size(max=254) String contactEmail,@Size(max=40) String contactPhone,@Email @Size(max=254) String supportEmail,@Size(max=40) String supportPhone){}
 public record DomainRequest(@NotBlank @Size(max=253) String domain){}
 public record IntegrationRequest(@NotBlank @Size(max=60) String providerCode,@NotBlank String integrationType,@NotBlank @Size(max=150) String displayName,@Size(max=500) String secretReference,@NotBlank String configJson){}
 public record IntegrationStatusRequest(@NotBlank String status){}
 public record IntegrationJobRequest(@NotBlank @Size(max=60) String entityType,@NotBlank @Size(max=100) String entityId,@NotBlank @Size(max=30) String operation,@NotBlank @Size(max=160) String idempotencyKey,@NotBlank @Size(max=100000) String payloadJson){}
 public record WorkflowRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=150) String name,@NotBlank String entityType,@NotBlank String stepsJson){}
 public record ApprovalDecisionRequest(@NotBlank String decision,@Size(max=1000) String comments){}
 public record SubscriptionRequest(@NotNull UUID planId,@NotBlank String status,Instant startsAt,Instant endsAt,Instant renewsAt,@Size(max=160) String externalReference,String billingCycle,Instant trialEndsAt,boolean autoRenew,@Size(max=2000) String notes){}
 @GetMapping("/integrations") public List<IntegrationConnection> integrations(Authentication a){return integrations.list(p(a));}
 @PostMapping("/integrations") public IntegrationConnection createIntegration(@Valid @RequestBody IntegrationRequest x,Authentication a){return integrations.create(new com.saumyasolar.service.IntegrationManagementService.ConnectionInput(x.providerCode(),x.integrationType(),x.displayName(),x.secretReference(),x.configJson()),p(a));}
 @PatchMapping("/integrations/{id}/status") public IntegrationConnection setIntegrationStatus(@PathVariable UUID id,@Valid @RequestBody IntegrationStatusRequest x,Authentication a){return integrations.setStatus(id,x.status().toUpperCase(Locale.ROOT),p(a));}
 @GetMapping("/integrations/{id}/sync-jobs") public List<IntegrationSyncJob> integrationJobs(@PathVariable UUID id,Authentication a){return integrations.jobs(id,p(a));}
 @PostMapping("/integrations/{id}/sync-jobs") public IntegrationSyncJob enqueueIntegrationJob(@PathVariable UUID id,@Valid @RequestBody IntegrationJobRequest x,Authentication a){return integrations.enqueue(id,new com.saumyasolar.service.IntegrationManagementService.JobInput(x.entityType(),x.entityId(),x.operation(),x.idempotencyKey(),x.payloadJson()),p(a));}
 @PostMapping("/integrations/sync-jobs/{jobId}/retry") public IntegrationSyncJob retryIntegrationJob(@PathVariable UUID jobId,Authentication a){return integrations.retry(jobId,p(a));}
 @GetMapping("/approval-workflows") public List<ApprovalWorkflow> approvalWorkflows(Authentication a){return approvalWorkflows.list(p(a));}
 @PostMapping("/approval-workflows") public ApprovalWorkflow createApprovalWorkflow(@Valid @RequestBody WorkflowRequest x,Authentication a){return approvalWorkflows.create(new com.saumyasolar.service.ApprovalWorkflowService.WorkflowInput(x.code(),x.name(),x.entityType(),x.stepsJson()),p(a));}
 @GetMapping("/approval-instances") public List<ApprovalInstance> approvalInstances(Authentication a){return approvalWorkflows.instances(p(a));}
 @GetMapping("/approval-instances/{id}") public ApprovalInstance approvalInstance(@PathVariable UUID id,Authentication a){return approvalWorkflows.get(id,p(a));}
 @GetMapping("/approval-instances/{id}/decisions") public List<ApprovalDecision> approvalDecisionHistory(@PathVariable UUID id,Authentication a){return approvalWorkflows.history(id,p(a));}
 @PostMapping("/approval-instances/{id}/decisions") public ApprovalInstance decideApproval(@PathVariable UUID id,@Valid @RequestBody ApprovalDecisionRequest x,Authentication a){return approvalWorkflows.decide(id,x.decision().toUpperCase(Locale.ROOT),x.comments(),p(a));}
 @GetMapping("/vendor/branding") public EnterpriseSaasService.BrandingView branding(Authentication a){return service.getBranding(p(a));}
 @PatchMapping("/vendor/branding") public EnterpriseSaasService.BrandingView updateBranding(@Valid @RequestBody BrandingRequest x,Authentication a){
  return service.updateBranding(new EnterpriseSaasService.BrandingInput(x.displayName(),x.logoUrl(),x.primaryColor(),x.secondaryColor(),x.contactEmail(),x.contactPhone(),x.supportEmail(),x.supportPhone()),p(a));
 }
 @GetMapping("/public/{slug}/branding") public EnterpriseSaasService.BrandingView publicBranding(@PathVariable String slug){return service.publicBranding(slug);}
 @GetMapping("/public/store-context") public EnterpriseSaasService.PublicStoreContext publicStoreContext(jakarta.servlet.http.HttpServletRequest request,@RequestHeader(value="Origin",required=false) String origin){
  String host=request.getServerName();
  if(origin!=null&&!origin.isBlank())try{java.net.URI uri=java.net.URI.create(origin);if(("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))&&uri.getHost()!=null)host=uri.getHost();}catch(IllegalArgumentException ignored){}
  return service.publicStoreContext(host);
 }
 @GetMapping("/vendor/domains") public List<TenantDomain> domains(Authentication a){return service.domains(p(a));}
 @PostMapping("/vendor/domains") public TenantDomain requestDomain(@Valid @RequestBody DomainRequest x,Authentication a){return service.requestDomain(x.domain(),p(a));}
 @PostMapping("/vendor/domains/{id}/verify") public TenantDomain verifyDomain(@PathVariable UUID id,Authentication a){return service.verifyDomain(id,p(a));}
 @GetMapping("/branches") public List<Branch> branches(Authentication a){return service.branches(p(a));}
 @PostMapping("/branches") public Branch createBranch(@Valid @RequestBody BranchRequest x,Authentication a){return service.createBranch(new EnterpriseSaasService.BranchInput(x.code(),x.name(),x.address(),x.phone(),x.email()),p(a));}
 @PutMapping("/branches/{id}") public Branch updateBranch(@PathVariable UUID id,@Valid @RequestBody BranchRequest x,Authentication a){return service.updateBranch(id,new EnterpriseSaasService.BranchInput(x.code(),x.name(),x.address(),x.phone(),x.email()),p(a));}
 @PostMapping("/branches/{id}/activate") public Map<String,Object> activate(@PathVariable UUID id,Authentication a){service.setBranchActive(id,true,p(a));return Map.of("updated",true);}
 @PostMapping("/branches/{id}/deactivate") public Map<String,Object> deactivate(@PathVariable UUID id,Authentication a){service.setBranchActive(id,false,p(a));return Map.of("updated",true);}
 @PostMapping("/branches/{id}/users") public Map<String,Object> assignUser(@PathVariable UUID id,@Valid @RequestBody BranchUserRequest x,Authentication a){service.assignUser(id,x.userId(),p(a));return Map.of("assigned",true);}
 @GetMapping("/branches/{id}/users") public List<BranchMembership> branchUsers(@PathVariable UUID id,Authentication a){return service.branchUsers(id,p(a));}
 @DeleteMapping("/branches/{id}/users/{userId}") public Map<String,Object> removeUser(@PathVariable UUID id,@PathVariable UUID userId,Authentication a){service.removeUser(id,userId,p(a));return Map.of("removed",true);}
 @GetMapping("/subscription-plans") public List<SubscriptionPlan> plans(){return service.plans();}
 @GetMapping("/platform/subscription-plans") public List<SubscriptionPlan> allPlans(Authentication a){return service.allPlans(p(a));}
 @GetMapping("/platform/subscription-plans/{planId}") public SubscriptionPlan plan(@PathVariable UUID planId,Authentication a){return service.plan(planId,p(a));}
 @PutMapping("/platform/subscription-plans/{planId}") public SubscriptionPlan updatePlan(@PathVariable UUID planId,@Valid @RequestBody PlanRequest x,Authentication a){return service.updatePlan(planId,planInput(x),p(a));}
 @PostMapping("/platform/subscription-plans/{planId}/activate") public SubscriptionPlan activatePlan(@PathVariable UUID planId,Authentication a){return service.setPlanActive(planId,true,p(a));}
 @PostMapping("/platform/subscription-plans/{planId}/deactivate") public SubscriptionPlan deactivatePlan(@PathVariable UUID planId,Authentication a){return service.setPlanActive(planId,false,p(a));}
 @DeleteMapping("/platform/subscription-plans/{planId}") public Map<String,Object> deletePlan(@PathVariable UUID planId,Authentication a){service.deletePlan(planId,p(a));return Map.of("deleted",true);}
 @GetMapping("/platform/tenants/search") public List<Tenant> searchTenants(@RequestParam String q,Authentication a){return service.searchTenants(q,p(a));}
 @GetMapping("/platform/tenants/{tenantId}/subscriptions") public List<TenantSubscription> tenantSubscriptions(@PathVariable UUID tenantId,Authentication a){return service.tenantSubscriptions(tenantId,p(a));}
 @GetMapping("/platform/tenants/{tenantId}/subscriptions/{subscriptionId}") public TenantSubscription tenantSubscription(@PathVariable UUID tenantId,@PathVariable UUID subscriptionId,Authentication a){return service.tenantSubscription(tenantId,subscriptionId,p(a));}
 @PutMapping("/platform/tenants/{tenantId}/subscriptions/{subscriptionId}") public TenantSubscription updateTenantSubscription(@PathVariable UUID tenantId,@PathVariable UUID subscriptionId,@Valid @RequestBody SubscriptionRequest x,Authentication a){return service.updateTenantSubscription(tenantId,subscriptionId,new EnterpriseSaasService.SubscriptionInput(x.status(),x.startsAt(),x.endsAt(),x.renewsAt(),x.externalReference(),x.billingCycle(),x.trialEndsAt(),x.autoRenew(),x.notes()),x.planId(),p(a));}

 @GetMapping("/platform/tenants/{tenantId}/subscription-history") public List<SubscriptionAssignmentHistory> subscriptionHistory(@PathVariable UUID tenantId,Authentication a){return service.subscriptionHistory(tenantId,p(a));}
 @PostMapping("/platform/subscription-plans") public SubscriptionPlan createPlan(@Valid @RequestBody PlanRequest x,Authentication a){return service.createPlan(planInput(x),p(a));}
 @PostMapping("/platform/tenants/{tenantId}/subscriptions/{subscriptionId}/cancel") public TenantSubscription cancelSubscription(@PathVariable UUID tenantId,@PathVariable UUID subscriptionId,Authentication a){return service.cancelSubscription(tenantId,subscriptionId,p(a));}
 @PostMapping("/platform/tenants/{tenantId}/subscriptions") public TenantSubscription assignSubscription(@PathVariable UUID tenantId,@Valid @RequestBody SubscriptionRequest x,Authentication a){
  if(!Set.of("TRIAL","ACTIVE").contains(x.status()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"New subscriptions must be TRIAL or ACTIVE");
  return service.assignSubscription(tenantId,x.planId(),new EnterpriseSaasService.SubscriptionInput(x.status(),x.startsAt(),x.endsAt(),x.renewsAt(),x.externalReference(),x.billingCycle(),x.trialEndsAt(),x.autoRenew(),x.notes()),p(a));
 }
 @PatchMapping("/warehouses/{id}/branch") public Warehouse assignWarehouseBranch(@PathVariable UUID id,@RequestBody WarehouseBranchRequest x,Authentication a){return service.assignWarehouseBranch(id,x.branchId(),p(a));}
 @GetMapping("/subscription") public Map<String,Object> currentSubscription(Authentication a){return service.currentSubscription(p(a));}
 @Operation(summary="Platform tenant resource usage",description="Paginated usage and plan limits across tenants. Requires Platform Super Admin and SUBSCRIPTION_MANAGE.")
 @GetMapping("/platform/analytics/tenant-usage") public Map<String,Object> platformTenantUsage(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication a){return service.platformTenantUsage(p(a),page,size);}
 @GetMapping("/audit-events") public List<AuditEvent> auditEvents(@RequestParam Instant from,@RequestParam Instant to,
  @RequestParam(required=false) UUID actorId,@RequestParam(required=false) String action,
  @RequestParam(required=false) String entityType,@RequestParam(required=false) String outcome,Authentication a){
  return service.auditEvents(p(a),from,to,actorId,action,entityType,outcome);
 }
}
