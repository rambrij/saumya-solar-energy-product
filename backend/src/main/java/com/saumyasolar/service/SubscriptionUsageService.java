package com.saumyasolar.service;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class SubscriptionUsageService {
 private final TenantSubscriptionRepository subscriptions;
 private final SubscriptionPlanRepository plans;
 private final AppUserRepository users;
 private final BranchRepository branches;
 private final WarehouseRepository warehouses;
 private final SubscriptionUsageRepository usage;
 public SubscriptionUsageService(TenantSubscriptionRepository s,SubscriptionPlanRepository p,AppUserRepository u,BranchRepository b,WarehouseRepository w,SubscriptionUsageRepository m){
  subscriptions=s;plans=p;users=u;branches=b;warehouses=w;usage=m;
 }
 @Transactional public Map<String,Object> current(Principal p){
  if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  UUID tenant=p.tenantId();Instant now=Instant.now();
  LocalDate start=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1),end=start.plusMonths(1).minusDays(1);
  long userCount=users.findByTenantId(tenant).stream().filter(u->"VENDOR".equals(u.getAccountType())&&u.isEnabled()).count();
  long branchCount=branches.countByTenantIdAndActiveTrue(tenant);
  long warehouseCount=warehouses.findByTenantIdOrderByNameAsc(tenant).stream().filter(Warehouse::isActive).count();
  Optional<TenantSubscription> active=subscriptions.findByTenantIdOrderByCreatedAtDesc(tenant).stream()
   .filter(s->Set.of("ACTIVE","TRIAL").contains(s.getStatus())&&s.getStartsAt()!=null&&!s.getStartsAt().isAfter(now)&&(s.getEndsAt()==null||s.getEndsAt().isAfter(now)))
   .max(Comparator.comparing(TenantSubscription::getStartsAt));
  SubscriptionPlan plan=active.flatMap(s->plans.findById(s.getPlanId())).orElse(null);
  List<Map<String,Object>> meters=new ArrayList<>();
  meters.add(meter(tenant,"ACTIVE_USERS",userCount,plan==null?null:plan.getMaxUsers(),start,end));
  meters.add(meter(tenant,"ACTIVE_BRANCHES",branchCount,plan==null?null:plan.getMaxBranches(),start,end));
  meters.add(meter(tenant,"ACTIVE_WAREHOUSES",warehouseCount,plan==null?null:plan.getMaxWarehouses(),start,end));
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("subscriptionStatus",active.map(TenantSubscription::getStatus).orElse(subscriptions.findByTenantIdOrderByCreatedAtDesc(tenant).isEmpty()?"GRANDFATHERED":"INACTIVE"));
  result.put("plan",plan);result.put("periodStart",start);result.put("periodEnd",end);result.put("meters",meters);
  return result;
 }
 private Map<String,Object> meter(UUID tenant,String code,long quantity,Number limit,LocalDate start,LocalDate end){
  SubscriptionUsage row=usage.findByTenantIdAndMeterCodeAndPeriodStartAndPeriodEnd(tenant,code,start,end).orElseGet(()->{
   SubscriptionUsage value=new SubscriptionUsage();value.setId(UUID.randomUUID());value.setTenantId(tenant);value.setMeterCode(code);value.setPeriodStart(start);value.setPeriodEnd(end);return value;
  });
  row.setQuantity(quantity);row.setUpdatedAt(Instant.now());usage.save(row);
  Map<String,Object> result=new LinkedHashMap<>();result.put("code",code);result.put("used",quantity);result.put("limit",limit);
  result.put("remaining",limit==null?null:Math.max(0,limit.longValue()-quantity));return result;
 }
}
