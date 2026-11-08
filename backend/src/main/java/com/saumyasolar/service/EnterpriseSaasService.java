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
 private final TenantRepository tenants;private final AppUserRepository users;private final AuditEventRepository audits;private final WarehouseRepository warehouses;private final TenantDomainRepository tenantDomains;private final ObjectMapper mapper;
 public EnterpriseSaasService(BranchRepository b,BranchMembershipRepository m,SubscriptionPlanRepository pl,
  TenantSubscriptionRepository s,TenantRepository t,AppUserRepository u,AuditEventRepository a,WarehouseRepository wr,TenantDomainRepository td,ObjectMapper om){
  branches=b;memberships=m;plans=pl;subscriptions=s;tenants=t;users=u;audits=a;warehouses=wr;tenantDomains=td;mapper=om;
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
  UUID t=vendor(p,"BRANCH_MANAGE");tenants.findByIdForUpdate(t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));String code=x.code().trim().toUpperCase(Locale.ROOT);
  if(branches.existsByTenantIdAndCodeIgnoreCase(t,code))throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch code already exists");
  enforceBranchLimit(t);Branch b=new Branch();b.setId(UUID.randomUUID());b.setTenantId(t);b.setCode(code);b.setName(x.name().trim());
  b.setAddress(trim(x.address()));b.setPhone(trim(x.phone()));b.setEmail(trim(x.email()));b.setActive(true);b.setCreatedAt(Instant.now());b.setUpdatedAt(Instant.now());
  branches.save(b);audit(p,t,"BRANCH_CREATED","BRANCH",b.getId(),b);return b;
 }
 public List<Branch> branches(Principal p){return branches.findByTenantIdOrderByNameAsc(vendor(p,"BRANCH_MANAGE"));}
 public List<BranchMembership> branchUsers(UUID branchId,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");
  branches.findByIdAndTenantId(branchId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found"));
  return memberships.findByTenantIdAndBranchId(t,branchId);
 }
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
  if(!active&&warehouses.countByTenantIdAndBranchId(t,id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Unassign warehouses from this branch before deactivation");
  b.setActive(active);b.setUpdatedAt(Instant.now());branches.save(b);audit(p,t,active?"BRANCH_ACTIVATED":"BRANCH_DEACTIVATED","BRANCH",id,b);
 }
 @Transactional public void removeUser(UUID branchId,UUID userId,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");
  branches.findByIdAndTenantId(branchId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found"));
  if(!memberships.existsByTenantIdAndBranchIdAndUserId(t,branchId,userId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch membership not found");
  memberships.deleteByTenantIdAndBranchIdAndUserId(t,branchId,userId);
  audit(p,t,"BRANCH_USER_REMOVED","BRANCH",branchId,Map.of("userId",userId));
 }
 @Transactional public void assignUser(UUID branchId,UUID userId,Principal p){
  UUID t=vendor(p,"BRANCH_MANAGE");branches.findByIdAndTenantId(branchId,t).filter(Branch::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Active branch not found"));
  users.findById(userId).filter(u->t.equals(u.getTenantId())&&u.isEnabled()&&"VENDOR".equals(u.getAccountType())).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enabled same-tenant vendor user not found"));
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
  Instant now=Instant.now(),start=x.startsAt()==null?now:x.startsAt();
  if(!Set.of("TRIAL","ACTIVE").contains(x.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New subscriptions must be TRIAL or ACTIVE");
  if(x.endsAt()!=null&&!x.endsAt().isAfter(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Subscription end must be after start");
  if(x.renewsAt()!=null&&x.renewsAt().isBefore(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Renewal date cannot precede subscription start");
  for(TenantSubscription old:subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId)){
   if(!Set.of("TRIAL","ACTIVE","PAST_DUE").contains(old.getStatus()))continue;
   if(old.getStartsAt()!=null&&!old.getStartsAt().isBefore(start)){
    old.setStatus("CANCELLED");old.setUpdatedAt(now);subscriptions.save(old);
   }else if(start.isAfter(now)&&effective(old)){
    if(old.getEndsAt()==null||start.isBefore(old.getEndsAt()))old.setEndsAt(start);
    old.setUpdatedAt(now);subscriptions.save(old);
   }else if(!start.isAfter(now)&&effective(old)){
    old.setStatus("CANCELLED");old.setUpdatedAt(now);subscriptions.save(old);
   }
  }
  TenantSubscription s=new TenantSubscription();s.setId(UUID.randomUUID());s.setTenantId(tenantId);s.setPlanId(planId);s.setStatus(x.status());s.setStartsAt(start);
  s.setEndsAt(x.endsAt());s.setRenewsAt(x.renewsAt());s.setExternalReference(trim(x.externalReference()));s.setCreatedBy(p.userId());s.setCreatedAt(now);s.setUpdatedAt(now);
  subscriptions.save(s);audit(p,tenantId,"SUBSCRIPTION_ASSIGNED","TENANT_SUBSCRIPTION",s.getId(),s);return s;
 }
 public Map<String,Object> currentSubscription(Principal p){
  if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  TenantSubscription s=effectiveSubscription(p.tenantId()).orElseGet(()->subscriptions.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream()
   .filter(v->!"CANCELLED".equals(v.getStatus()))
   .max(Comparator.comparing(TenantSubscription::getStartsAt,Comparator.nullsFirst(Comparator.naturalOrder())))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No subscription found")));
  SubscriptionPlan plan=plans.findById(s.getPlanId()).orElseThrow();return Map.of("subscription",s,"plan",plan,"effective",effective(s));
 }
 public void assertWarehouseAccess(Warehouse warehouse,Principal p){
  if(!"VENDOR".equals(p.accountType())||!Objects.equals(warehouse.getTenantId(),p.tenantId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found");
  if(p.roles()!=null&&p.roles().contains("VENDOR_ADMIN"))return;
  if(warehouse.getBranchId()==null)return; // Unassigned legacy warehouses remain available to existing vendor operations.
  List<BranchMembership> assigned=memberships.findByTenantIdAndUserId(p.tenantId(),p.userId());
  if(assigned.stream().noneMatch(m->warehouse.getBranchId().equals(m.getBranchId())))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found");
 }
 public List<Warehouse> filterWarehouses(List<Warehouse> rows,Principal p){
  if(p.roles()!=null&&p.roles().contains("VENDOR_ADMIN"))return rows;
  List<BranchMembership> assigned=memberships.findByTenantIdAndUserId(p.tenantId(),p.userId());
  Set<UUID> branchIds=new HashSet<>();assigned.forEach(m->branchIds.add(m.getBranchId()));
  return rows.stream().filter(w->w.getBranchId()==null||branchIds.contains(w.getBranchId())).toList();
 }
 public void assertFeatureEnabled(UUID tenantId,String feature){
  List<TenantSubscription> history=subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId);
  if(history.isEmpty())return;
  TenantSubscription subscription=effectiveSubscription(tenantId).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"An active subscription is required"));
  SubscriptionPlan plan=plans.findById(subscription.getPlanId()).orElseThrow();
  try{JsonNode root=mapper.readTree(plan.getEntitlementsJson());if(!root.path(feature).asBoolean(false))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"The current subscription plan does not include "+feature);}
  catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Subscription entitlements are invalid");}
 }
 public void enforceUserLimit(UUID t){
  List<TenantSubscription> history=subscriptions.findByTenantIdOrderByCreatedAtDesc(t);
  if(history.isEmpty())return; // Existing Phase 1/2 vendors remain grandfathered until subscription assignment.
  tenants.findByIdForUpdate(t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
  TenantSubscription active=effectiveSubscription(t)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"An active subscription is required to add vendor users"));
  SubscriptionPlan plan=plans.findById(active.getPlanId()).orElseThrow();
  long count=users.findByTenantId(t).stream().filter(u->"VENDOR".equals(u.getAccountType())&&u.isEnabled()).count();
  if(count>=plan.getMaxUsers())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"User limit reached for current plan");
 }
 public void enforceWarehouseLimit(UUID t){
  List<TenantSubscription> history=subscriptions.findByTenantIdOrderByCreatedAtDesc(t);
  if(history.isEmpty())return;
  assertFeatureEnabled(t,"multiWarehouse"); // Preserve existing Phase 1/2 tenants until a platform subscription is assigned.
  tenants.findByIdForUpdate(t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
  TenantSubscription s=effectiveSubscription(t)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"An active subscription is required for warehouse changes"));
  SubscriptionPlan plan=plans.findById(s.getPlanId()).orElseThrow();
  long active=warehouses.findByTenantIdOrderByNameAsc(t).stream().filter(Warehouse::isActive).count();
  if(active>=plan.getMaxWarehouses())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Warehouse limit reached for current plan");
 }
 @Transactional public Warehouse assignWarehouseBranch(UUID warehouseId,UUID branchId,Principal p){
  UUID t=vendor(p,"INVENTORY_MANAGE");
  if((p.roles()==null||!p.roles().contains("VENDOR_ADMIN"))&&(p.permissions()==null||!p.permissions().contains("BRANCH_MANAGE")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch management permission is required to move warehouses between branches");
  Warehouse w=warehouses.findByIdAndTenantId(warehouseId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found"));
  if(branchId!=null)branches.findByIdAndTenantId(branchId,t).filter(Branch::isActive)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Active branch must belong to this vendor"));
  w.setBranchId(branchId);warehouses.save(w);audit(p,t,"WAREHOUSE_BRANCH_ASSIGNED","WAREHOUSE",warehouseId,w);return w;
 }
 private void enforceBranchLimit(UUID t){
  if(subscriptions.findByTenantIdOrderByCreatedAtDesc(t).isEmpty())return;
  assertFeatureEnabled(t,"branches"); // Grandfather existing Phase 1/2 tenants until a plan is assigned.
  TenantSubscription s=effectiveSubscription(t).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"An active subscription is required to add branches"));
  SubscriptionPlan plan=plans.findById(s.getPlanId()).orElseThrow();
  if(branches.countByTenantIdAndActiveTrue(t)>=plan.getMaxBranches())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch limit reached for current plan");
 }
 private Optional<TenantSubscription> effectiveSubscription(UUID tenantId){
  Instant now=Instant.now();
  return subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
   .filter(s->("ACTIVE".equals(s.getStatus())||"TRIAL".equals(s.getStatus()))
    &&s.getStartsAt()!=null&&!s.getStartsAt().isAfter(now)&&(s.getEndsAt()==null||s.getEndsAt().isAfter(now)))
   .max(Comparator.comparing(TenantSubscription::getStartsAt));
 }
 private boolean effective(TenantSubscription s){Instant now=Instant.now();return ("ACTIVE".equals(s.getStatus())||"TRIAL".equals(s.getStatus()))&&s.getStartsAt()!=null&&!s.getStartsAt().isAfter(now)&&(s.getEndsAt()==null||s.getEndsAt().isAfter(now));}
 private String camelKey(String value){
  StringBuilder result=new StringBuilder();boolean upper=false;
  for(char c:value.toLowerCase(Locale.ROOT).toCharArray()){
   if(c=='_'||c=='-'||c==' '){upper=true;continue;}
   result.append(upper?Character.toUpperCase(c):c);upper=false;
  }
  return result.toString();
 }
 public void requireEntitlement(UUID tenantId,String featureCode){
  if(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId).isEmpty())return;
  TenantSubscription active=effectiveSubscription(tenantId).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"An active subscription is required for this feature"));
  SubscriptionPlan plan=plans.findById(active.getPlanId()).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription plan is unavailable"));
  try{
   var root=mapper.readTree(plan.getEntitlementsJson()==null?"{}":plan.getEntitlementsJson());
   String lower=featureCode.toLowerCase(Locale.ROOT),camel=camelKey(featureCode);
   var feature=root.get(featureCode);if(feature==null)feature=root.get(lower);if(feature==null)feature=root.get(camel);
   if(feature==null&&root.has("features")){
    var features=root.get("features");
    if(features.isObject()){
     feature=features.get(featureCode);if(feature==null)feature=features.get(lower);if(feature==null)feature=features.get(camel);
     if(feature==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current subscription does not include "+featureCode);
    }else if(features.isArray()){
     boolean included=false;for(var value:features)if(featureCode.equalsIgnoreCase(value.asText())||lower.equals(value.asText().toLowerCase(Locale.ROOT)))included=true;
     if(!included)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current subscription does not include "+featureCode);
    }else throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription feature list is invalid");
   }
   if(feature!=null&&feature.isBoolean()&&!feature.asBoolean())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current subscription does not include "+featureCode);
  }catch(ResponseStatusException e){throw e;}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription entitlements are invalid; contact the platform administrator");}
 }

 public List<AuditEvent> auditEvents(Principal p,Instant from,Instant to,UUID actorId,String action,String entityType,String outcome){
  UUID tenant=null;
  if("PLATFORM".equals(p.accountType()))platform(p,"AUDIT_VIEW");
  else tenant=vendor(p,"AUDIT_VIEW");
  if(from==null||to==null||to.isBefore(from)||java.time.Duration.between(from,to).toDays()>90)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Audit range must be valid and no longer than 90 days");
  return audits.search(tenant,from,to,actorId,trim(action),trim(entityType),trim(outcome),org.springframework.data.domain.PageRequest.of(0,500));
 }
 public BrandingView publicBranding(String slug){
  Tenant t=tenants.findBySlug(slug).filter(v->"ACTIVE".equals(v.getStatus()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found"));
  return brandingView(t);
 }
 public PublicStoreContext publicStoreContext(String host){
  String domain=host==null?"":host.trim().toLowerCase(Locale.ROOT);
  if(domain.isBlank()||domain.equals("localhost")||domain.matches("^[0-9.]+$"))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No custom store domain is configured for this host");
  TenantDomain mapping=tenantDomains.findFirstByDomainIgnoreCaseAndStatus(domain,"VERIFIED")
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Store domain is not verified"));
  Tenant tenant=tenants.findById(mapping.getTenantId()).filter(t->"ACTIVE".equals(t.getStatus()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Store is not active"));
  return new PublicStoreContext(tenant.getSlug(),brandingView(tenant));
 }
 public BrandingView getBranding(Principal p){
  UUID t=vendor(p,"BRANDING_MANAGE");
  return brandingView(tenants.findById(t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found")));
 }
 @Transactional public BrandingView updateBranding(BrandingInput x,Principal p){
  UUID id=vendor(p,"BRANDING_MANAGE");
  assertFeatureEnabled(id,"customBranding");
  if(x.logoUrl()!=null&&!x.logoUrl().isBlank()&&!x.logoUrl().startsWith("/uploads/")&&!x.logoUrl().startsWith("https://"))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Logo URL must use HTTPS or the application's private upload path");
  if(!color(x.primaryColor())||!color(x.secondaryColor()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Brand colors must use six-digit hexadecimal values");
  Tenant t=tenants.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
  t.setDisplayName(trim(x.displayName()));t.setLogoUrl(trim(x.logoUrl()));t.setPrimaryColor(x.primaryColor().toUpperCase(Locale.ROOT));
  t.setSecondaryColor(x.secondaryColor().toUpperCase(Locale.ROOT));t.setContactEmail(trim(x.contactEmail()));t.setContactPhone(trim(x.contactPhone()));
  t.setSupportEmail(trim(x.supportEmail()));t.setSupportPhone(trim(x.supportPhone()));t.setUpdatedAt(Instant.now());tenants.save(t);
  BrandingView view=brandingView(t);audit(p,id,"TENANT_BRANDING_UPDATED","TENANT",id,view);return view;
 }
 private boolean color(String value){return value!=null&&value.matches("#[0-9A-Fa-f]{6}");}
 private BrandingView brandingView(Tenant t){return new BrandingView(t.getDisplayName()==null||t.getDisplayName().isBlank()?t.getName():t.getDisplayName(),
  t.getLogoUrl(),t.getPrimaryColor()==null?"#0B6B4B":t.getPrimaryColor(),t.getSecondaryColor()==null?"#F6C85F":t.getSecondaryColor(),
  t.getContactEmail(),t.getContactPhone(),t.getSupportEmail(),t.getSupportPhone());}
 @Transactional public TenantDomain requestDomain(String value,Principal p){
  UUID t=vendor(p,"BRANDING_MANAGE");String domain=value==null?"":value.trim().toLowerCase(Locale.ROOT);
  if(!domain.matches("(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}"))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid fully-qualified domain name");
  if(tenantDomains.existsByDomainIgnoreCase(domain))throw new ResponseStatusException(HttpStatus.CONFLICT,"Domain is already registered");
  TenantDomain d=new TenantDomain();d.setId(UUID.randomUUID());d.setTenantId(t);d.setDomain(domain);
  d.setVerificationToken(UUID.randomUUID().toString().replace("-",""));d.setStatus("PENDING");d.setCreatedAt(Instant.now());
  tenantDomains.save(d);audit(p,t,"CUSTOM_DOMAIN_REQUESTED","TENANT_DOMAIN",d.getId(),Map.of("domain",domain,"status","PENDING"));return d;
 }
 public List<TenantDomain> domains(Principal p){return tenantDomains.findByTenantIdOrderByCreatedAtDesc(vendor(p,"BRANDING_MANAGE"));}
 @Transactional public TenantDomain verifyDomain(UUID id,Principal p){
  UUID t=vendor(p,"BRANDING_MANAGE");TenantDomain d=tenantDomains.findByIdAndTenantId(id,t)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Domain not found"));
  if("VERIFIED".equals(d.getStatus()))return d;
  boolean verified=false;
  try{
   java.util.Hashtable<String,String> env=new java.util.Hashtable<>();env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.dns.DnsContextFactory");
   javax.naming.directory.DirContext context=new javax.naming.directory.InitialDirContext(env);
   try{
    javax.naming.directory.Attributes attrs=context.getAttributes(d.getDomain(),new String[]{"TXT"});
    javax.naming.directory.Attribute txt=attrs.get("TXT");
    if(txt!=null){javax.naming.NamingEnumeration<?> values=txt.getAll();while(values.hasMore()){String record=String.valueOf(values.next()).replace("\"","");if(record.contains("saumya-solar-verification="+d.getVerificationToken()))verified=true;}}
   }finally{context.close();}
  }catch(Exception ignored){verified=false;}
  if(!verified)throw new ResponseStatusException(HttpStatus.CONFLICT,"DNS TXT verification record was not found");
  d.setStatus("VERIFIED");d.setVerifiedAt(Instant.now());tenantDomains.save(d);
  audit(p,t,"CUSTOM_DOMAIN_VERIFIED","TENANT_DOMAIN",id,Map.of("domain",d.getDomain(),"status","VERIFIED"));return d;
 }
 private String trim(String x){return x==null||x.isBlank()?null:x.trim();}
 public record BrandingInput(String displayName,String logoUrl,String primaryColor,String secondaryColor,String contactEmail,String contactPhone,String supportEmail,String supportPhone){}
 public record BrandingView(String displayName,String logoUrl,String primaryColor,String secondaryColor,String contactEmail,String contactPhone,String supportEmail,String supportPhone){}
 public record PublicStoreContext(String slug,BrandingView branding){}
 public record BranchInput(String code,String name,String address,String phone,String email){}
 public record PlanInput(String planCode,String name,String description,String billingCycle,BigDecimal price,String currency,int maxUsers,int maxBranches,int maxWarehouses,long maxStorageMb,String entitlementsJson){}
 public record SubscriptionInput(String status,Instant startsAt,Instant endsAt,Instant renewsAt,String externalReference){}
}
