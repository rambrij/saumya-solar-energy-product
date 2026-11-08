package com.saumyasolar.api;
import com.saumyasolar.domain.*;import com.saumyasolar.security.Principal;import com.saumyasolar.service.EnterpriseSaasService;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;import java.time.Instant;import java.util.*;
@RestController @RequestMapping("/api/v1")
public class EnterpriseSaasController {
 private final EnterpriseSaasService service;public EnterpriseSaasController(EnterpriseSaasService s){service=s;}
 private Principal p(Authentication a){return (Principal)a.getPrincipal();}
 public record BranchRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=150) String name,@Size(max=700) String address,@Size(max=40) String phone,@Email @Size(max=254) String email){}
 public record BranchUserRequest(@NotNull UUID userId){}
 public record PlanRequest(@NotBlank @Size(max=50) String planCode,@NotBlank @Size(max=120) String name,@Size(max=1000) String description,
  @NotBlank String billingCycle,@NotNull @DecimalMin("0.00") BigDecimal price,@Pattern(regexp="[A-Z]{3}") String currency,
  @Min(1) @Max(10000) int maxUsers,@Min(1) @Max(1000) int maxBranches,@Min(1) @Max(1000) int maxWarehouses,
  @Min(0) long maxStorageMb,@NotBlank String entitlementsJson){}
 public record WarehouseBranchRequest(UUID branchId){}
 public record SubscriptionRequest(@NotNull UUID planId,@NotBlank String status,Instant startsAt,Instant endsAt,Instant renewsAt,@Size(max=160) String externalReference){}
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
