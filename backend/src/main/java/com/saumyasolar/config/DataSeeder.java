package com.saumyasolar.config;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
@Configuration
public class DataSeeder {
 private static final List<String> PERMISSIONS=List.of("PLATFORM_MANAGE","CATALOG_MANAGE","CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","DISCOUNT_APPROVE","ORDER_CREATE","USER_VIEW","USER_MANAGE","INVENTORY_MANAGE","SERVICE_MANAGE","FINANCE_MANAGE","REPORT_VIEW");
 private static final List<String> CATEGORIES=List.of("Solar Panels","Inverters","Batteries","Mounting Structures","Cables","AC/DC Distribution Boxes","Solar Accessories");
 @Bean CommandLineRunner seedData(TenantRepository tenants,PermissionRepository permissions,RoleRepository roles,AppUserRepository users,CustomerProfileRepository customers,ProductCategoryRepository categories,PasswordEncoder encoder){
  return args->{
   Tenant defaultTenant=tenants.findBySlug("saumya-solar").orElseGet(()->{Tenant t=new Tenant();t.setId(UUID.randomUUID());t.setName("Saumya Solar");t.setSlug("saumya-solar");t.setStatus("ACTIVE");return tenants.save(t);});
   Map<String,Permission> pm=new HashMap<>();
   for(String code:PERMISSIONS){Permission p=permissions.findByCode(code).orElseGet(()->{Permission x=new Permission();x.setId(UUID.randomUUID());x.setCode(code);return permissions.save(x);});pm.put(code,p);}
   Role platform=ensureRole(roles,null,"PLATFORM_SUPER_ADMIN");grant(platform,List.of("PLATFORM_MANAGE"),pm);roles.save(platform);
   Role customer=roles.findByNameAndTenantIdIsNull("CUSTOMER").orElseGet(()->{Role r=new Role();r.setId(UUID.randomUUID());r.setName("CUSTOMER");return roles.save(r);});
   for(Tenant tenant:tenants.findAll()){
    Role admin=ensureRole(roles,tenant.getId(),"VENDOR_ADMIN");grant(admin,PERMISSIONS.stream().filter(x->!x.equals("PLATFORM_MANAGE")).toList(),pm);roles.save(admin);
    employeeRolePermissions().forEach((name,codes)->{Role role=ensureRole(roles,tenant.getId(),name);grant(role,codes,pm);roles.save(role);});
    for(String name:CATEGORIES)categories.findByNameAndTenantId(name,tenant.getId()).orElseGet(()->{ProductCategory x=new ProductCategory();x.setId(UUID.randomUUID());x.setTenantId(tenant.getId());x.setName(name);x.setDescription("Solar catalogue category");x.setActive(true);return categories.save(x);});
   }
   ensureUser(users,encoder,null,"Platform Admin","platform@saumya.local","Platform@123","PLATFORM",platform);
   ensureUser(users,encoder,defaultTenant.getId(),"Vendor Admin","admin@saumya.local","Admin@123","VENDOR",ensureRole(roles,defaultTenant.getId(),"VENDOR_ADMIN"));
   ensureUser(users,encoder,defaultTenant.getId(),"Sales User","sales@saumya.local","Sales@123","VENDOR",ensureRole(roles,defaultTenant.getId(),"SALES_EXECUTIVE"));
   ensureUser(users,encoder,defaultTenant.getId(),"Demo Customer","customer@saumya.local","Customer@123","CUSTOMER",customer);
   for(AppUser u:users.findAll())if("CUSTOMER".equals(u.getAccountType())&&u.getTenantId()!=null)customers.findByTenantIdAndUserId(u.getTenantId(),u.getId()).orElseGet(()->{CustomerProfile cp=new CustomerProfile();cp.setId(UUID.randomUUID());cp.setTenantId(u.getTenantId());cp.setUserId(u.getId());cp.setName(u.getName());cp.setEmail(u.getEmail());return customers.save(cp);});
  };
 }
 public static Map<String,List<String>> employeeRolePermissions(){return Map.ofEntries(
  Map.entry("SALES_EXECUTIVE",List.of("CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","ORDER_CREATE")),
  Map.entry("SALES_MANAGER",List.of("CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","DISCOUNT_APPROVE","ORDER_CREATE","REPORT_VIEW")),
  Map.entry("MANAGER",List.of("CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","DISCOUNT_APPROVE","ORDER_CREATE","REPORT_VIEW")),
  Map.entry("INVENTORY_MANAGER",List.of("CATALOG_MANAGE","INVENTORY_MANAGE","ORDER_CREATE")),
  Map.entry("SERVICE_ENGINEER",List.of("SURVEY_MANAGE","SERVICE_MANAGE")),
  Map.entry("ACCOUNTANT",List.of("FINANCE_MANAGE","REPORT_VIEW")),
  Map.entry("INSTALLER",List.of("SURVEY_MANAGE","SERVICE_MANAGE")),
  Map.entry("OPERATIONS_MANAGER",List.of("ORDER_CREATE","SURVEY_MANAGE","INVENTORY_MANAGE","SERVICE_MANAGE","REPORT_VIEW")));
 }
 private static Role ensureRole(RoleRepository r,UUID tenant,String name){return r.findByNameAndTenantId(name,tenant).orElseGet(()->{Role x=new Role();x.setId(UUID.randomUUID());x.setTenantId(tenant);x.setName(name);return r.save(x);});}
 private static void grant(Role role,List<String> codes,Map<String,Permission> pm){codes.stream().map(pm::get).filter(Objects::nonNull).forEach(role.getPermissions()::add);}
 private static void ensureUser(AppUserRepository users,PasswordEncoder encoder,UUID tenant,String name,String email,String password,String type,Role role){if(users.findByEmailIgnoreCase(email).isPresent())return;AppUser u=new AppUser();u.setId(UUID.randomUUID());u.setTenantId(tenant);u.setName(name);u.setEmail(email);u.setPasswordHash(encoder.encode(password));u.setAccountType(type);u.setEnabled(true);u.getRoles().add(role);users.save(u);}
}