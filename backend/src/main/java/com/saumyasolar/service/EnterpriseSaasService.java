package com.saumyasolar.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;import com.saumyasolar.repo.*;import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;import java.util.*;import java.math.BigDecimal;
@Service
public class EnterpriseSaasService {
 private final BranchRepository branches;private final BranchMembershipRepository memberships;
 private final SubscriptionPlanRepository plans;private final TenantSubscriptionRepository subscriptions;
 private final TenantRepository tenants;private final AppUserRepository users;private final AuditEventRepository audits;private final WarehouseRepository warehouses;private final ObjectMapper mapper;
 public EnterpriseSaasService(BranchRepository b,BranchMembershipRepository m,SubscriptionPlanRepository pl,
  TenantSubscriptionRepository s,TenantRepository t,AppUserRepository u,AuditEventRepository a,WarehouseRepository wr,ObjectMapper om){
  branches=b;memberships=m;plans=pl;subscriptions=s;tenants=t;users=u;audits=a;warehouses=wr;mapper=om;
 }
 private void permission(Principal p,String code){if(p.permissions()==null||!p.permissions().contains(code))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Missing permission: "+code);}
 private UUID vendor(Principal p,String code){if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);permission(p,code);return p.tenantId();}
 private void platform(Principal p,String code){if(!"PLATFORM".equals(p.accountType())||p.roles()==null||!p.roles().contains("PLATFORM_SUPER_ADMIN"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform Super Admin required");permission(p,code);}
 private String json(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){return "{}";}}
 private void audit(Principal p,UUID tenant,String action,String type,UUID id,Object after){
  AuditEvent e=new AuditEvent();e.setId(UUID.randomUUID());e.setTenantId(tenant);e.setActorId(p.userId());e.setActorType(p.accountType());
  e.setAction(action);e.setEntityType(type);e.setEntityId(id==null?null:id.toString());e.setOutcome("SUCCESS");e.setAfterJson(json(after));e.setCreatedAt(Instant.now());audits.save(e);
 }
 @Transactional public Branch createBranch(BranchInput x,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");String code=x.code().trim().toUpperCase(Locale.ROOT);
  if(branches.existsByTenantIdAndCodeIgnoreCase(t,code))throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch code already exists");
  enforceBranchLimit(t);Branch b=new Branch();b.setId(UUID.randomUUID());b.setTenantId(t);b.setCode(code);b.setName(x.name().trim());
  b.setAddress(trim(x.address()));b.setPhone(trim(x.phone()));b.setEmail(trim(x.email()));b.setActive(true);b.setCreatedAt(Instant.now());b.setUpdatedAt(Instant.now());
  branches.save(b);audit(p,t,"BRANCH_CREATED","BRANCH",b.getId(),b);return b;
 }
 public List<Branch> branches(Principal p){return branches.findByTenantIdOrderByNameAsc(vendor(p,"BRANCH_MANAGE"));}
 @Transactional public Branch updateBranch(UUID id,BranchInput x,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");Branch b=branches.findByIdAndTenantId(id,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found"));
  String code=x.code().trim().toUpperCase(Locale.ROOT);
  if(!b.getCode().equalsIgnoreCase(code)&&branches.existsByTenantIdAndCodeIgnoreCase(t,code))throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch code already exists");
  b.setCode(code);b.setName(x.name().trim());b.setAddress(trim(x.address()));b.setPhone(trim(x.phone()));b.setEmail(trim(x.email()));b.setUpdatedAt(Instant.now());
  branches.save(b);audit(p,t,"BRANCH_UPDATED","BRANCH",id,b);return b;
 }
 @Transactional public void setBranchActive(UUID id,boolean active,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");Branch b=branches.findByIdAndTenantId(id,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found"));
  if(!active&&memberships.countByTenantIdAndBranchId(t,id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Remove branch memberships before deactivation");
  b.setActive(active);b.setUpdatedAt(Instant.now());branches.save(b);audit(p,t,active?"BRANCH_ACTIVATED":"BRANCH_DEACTIVATED","BRANCH",id,b);
 }
 @Transactional public void assignUser(UUID branchId,UUID userId,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");branches.findByIdAndTenantId(branchId,t).filter(Branch::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Active branch not found"));
  users.findById(userId).filter(u->t.equals(u.getTenantId())&&u.isEnabled()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enabled same-tenant user not found"));
  if(!memberships.existsByTenantIdAndBranchIdAndUserId(t,branchId,userId)){BranchMembership m=new BranchMembership();m.setId(UUID.randomUUID());m.setTenantId(t);m.setBranchId(branchId);m.setUserId(userId);m.setCreatedAt(Instant.now());memberships.save(m);audit(p,t,"BRANCH_USER_ASSIGNED","BRANCH_USER",m.getId(),Map.of("branchId",branchId,"userId",userId));}
 }
 @Transactional public SubscriptionPlan createPlan(PlanInput x,Principal p){
  platform(p,"SUBSCRIPTION_MANAGE");String code=x.planCode().trim().toUpperCase(Locale.ROOT);
  if(plans.findByPlanCodeIgnoreCase(code).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Plan code already exists");
  if(!Set.of("MONTHLY","YEARLY").contains(x.billingCycle()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Billing cycle must be MONTHLY or YEARLY");
  try{mapper.readTree(x.entitlementsJson());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Entitlements must be valid JSON");}
  SubscriptionPlan q=new SubscriptionPlan();q.setId(UUID.randomUUID());q.setPlanCode(code);q.setName(x.name().trim());q.setDescription(trim(x.description()));
  q.setBillingCycle(x.billingCycle());q.setPrice(x.price());q.setCurrency(x.currency().toUpperCase(Locale.ROOT));q.setMaxUsers(x.maxUsers());
  q.setMaxBranches(x.maxBranches());q.setMaxWarehouses(x.maxWarehouses());q.setMaxStorageMb(x.maxStorageMb());q.setEntitlementsJson(x.entitlementsJson());
  q.setActive(true);q.setCreatedAt(Instant.now());q.setUpdatedAt(Instant.now());plans.save(q);audit(p,null,"PLAN_CREATED","SUBSCRIPTION_PLAN",q.getId(),q);return q;
 }
 public List<SubscriptionPlan> plans(){return plans.findByActiveTrueOrderByPriceAsc();}
 @Transactional public TenantSubscription assignSubscription(UUID tenantId,UUID planId,SubscriptionInput x,Principal p){
  platform(p,"SUBSCRIPTION_MANAGE");tenants.findByIdForUpdate(tenantId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
  SubscriptionPlan plan=plans.findById(planId).filter(SubscriptionPlan::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Active plan not found"));
  Instant start=x.startsAt()==null?Instant.now():x.startsAt();if(x.endsAt()!=null&&!x.endsAt().isAfter(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Subscription end must be after start");
  subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(tenantId,List.of("TRIAL","ACTIVE","PAST_DUE")).ifPresent(old->{old.setStatus("CANCELLED");old.setUpdatedAt(Instant.now());subscriptions.save(old);});
  TenantSubscription s=new TenantSubscription();s.setId(UUID.randomUUID());s.setTenantId(tenantId);s.setPlanId(planId);s.setStatus(x.status());s.setStartsAt(start);
  s.setEndsAt(x.endsAt());s.setRenewsAt(x.renewsAt());s.setExternalReference(trim(x.externalReference()));s.setCreatedBy(p.userId());s.setCreatedAt(Instant.now());s.setUpdatedAt(Instant.now());
  subscriptions.save(s);audit(p,tenantId,"SUBSCRIPTION_ASSIGNED","TENANT_SUBSCRIPTION",s.getId(),s);return s;
 }
 public Map<String,Object> currentSubscription(Principal p){
  if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  TenantSubscription s=subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(p.tenantId(),List.of("TRIAL","ACTIVE","PAST_DUE")).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No subscription found"));
  SubscriptionPlan plan=plans.findById(s.getPlanId()).orElseThrow();return Map.of("subscription",s,"plan",plan,"effective",effective(s));
 }
 public void enforceWarehouseLimit(UUID t){
  List<TenantSubscription> history=subscriptions.findByTenantIdOrderByCreatedAtDesc(t);
  if(history.isEmpty())return; // Preserve existing Phase 1/2 tenants until a platform subscription is assigned.
  TenantSubscription s=subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(t,List.of("TRIAL","ACTIVE"))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"An active subscription is required for warehouse changes"));
  if(!effective(s))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription is expired");
  SubscriptionPlan plan=plans.findById(s.getPlanId()).orElseThrow();
  long active=warehouses.findByTenantIdOrderByNameAsc(t).stream().filter(Warehouse::isActive).count();
  if(active>=plan.getMaxWarehouses())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Warehouse limit reached for current plan");
 }
 @Transactional public Warehouse assignWarehouseBranch(UUID warehouseId,UUID branchId,Principal p){
  UUID t=vendor(p,"INVENTORY_MANAGE");
  Warehouse w=warehouses.findByIdAndTenantId(warehouseId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found"));
  if(branchId!=null)branches.findByIdAndTenantId(branchId,t).filter(Branch::isActive)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Active branch must belong to this vendor"));
  w.setBranchId(branchId);warehouses.save(w);audit(p,t,"WAREHOUSE_BRANCH_ASSIGNED","WAREHOUSE",warehouseId,w);return w;
 }
 private void enforceBranchLimit(UUID t){
  TenantSubscription s=subscriptions.findFirstByTenantIdAndStatusInOrderByStartsAtDesc(t,List.of("TRIAL","ACTIVE")).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active subscription required"));
  if(!effective(s))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription is expired");
  SubscriptionPlan plan=plans.findById(s.getPlanId()).orElseThrow();if(branches.countByTenantIdAndActiveTrue(t)>=plan.getMaxBranches())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch limit reached for current plan");
 }
 private boolean effective(TenantSubscription s){Instant now=Instant.now();return ("ACTIVE".equals(s.getStatus())||"TRIAL".equals(s.getStatus()))&&!s.getStartsAt().isAfter(now)&&(s.getEndsAt()==null||s.getEndsAt().isAfter(now));}
 public List<AuditEvent> auditEvents(Principal p,Instant from,Instant to){
  if("PLATFORM".equals(p.accountType())){platform(p,"AUDIT_VIEW");return audits.findTop200ByCreatedAtBetweenOrderByCreatedAtDesc(from,to);}
  UUID t=vendor(p,"AUDIT_VIEW");return audits.findTop200ByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(t,from,to);
 }
 private String trim(String x){return x==null||x.isBlank()?null:x.trim();}
 public record BranchInput(String code,String name,String address,String phone,String email){}
 public record PlanInput(String planCode,String name,String description,String billingCycle,BigDecimal price,String currency,int maxUsers,int maxBranches,int maxWarehouses,long maxStorageMb,String entitlementsJson){}
 public record SubscriptionInput(String status,Instant startsAt,Instant endsAt,Instant renewsAt,String externalReference){}
}
