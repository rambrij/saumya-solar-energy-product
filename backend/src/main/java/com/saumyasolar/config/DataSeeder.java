package com.saumyasolar.config;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Configuration
public class DataSeeder {
 @Value("${app.seed.enabled:false}") boolean seedEnabled;
 private static final List<String> PERMISSIONS=List.of("PLATFORM_MANAGE","CATALOG_MANAGE","CATALOG_VIEW","DISCOUNT_POLICY_MANAGE","CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","DISCOUNT_APPROVE","ORDER_CREATE","USER_VIEW","USER_MANAGE","INVENTORY_MANAGE","SERVICE_MANAGE","FINANCE_MANAGE","REPORT_VIEW");
 private static final List<String> CATEGORIES=List.of("Solar Panels","Inverters","Batteries","Mounting Structures","Cables","AC/DC Distribution Boxes","Solar Accessories");
 @Bean CommandLineRunner seedData(TenantRepository tenants,PermissionRepository permissions,RoleRepository roles,AppUserRepository users,CustomerProfileRepository customers,ProductCategoryRepository categories,ProductRepository products,PasswordEncoder encoder){
  return args->{
   if(!seedEnabled) return;
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
   seedDemoProducts(defaultTenant.getId(),categories,products);
   ensureUser(users,encoder,null,"Platform Admin","platform@saumya.local","Platform@123","PLATFORM",platform);
   ensureUser(users,encoder,defaultTenant.getId(),"Vendor Admin","admin@saumya.local","Admin@123","VENDOR",ensureRole(roles,defaultTenant.getId(),"VENDOR_ADMIN"));
   ensureUser(users,encoder,defaultTenant.getId(),"Sales User","sales@saumya.local","Sales@123","VENDOR",ensureRole(roles,defaultTenant.getId(),"SALES_EXECUTIVE"));
   ensureUser(users,encoder,defaultTenant.getId(),"Demo Customer","customer@saumya.local","Customer@123","CUSTOMER",customer);
   for(AppUser u:users.findAll())if("CUSTOMER".equals(u.getAccountType())&&u.getTenantId()!=null)customers.findByTenantIdAndUserId(u.getTenantId(),u.getId()).orElseGet(()->{CustomerProfile cp=new CustomerProfile();cp.setId(UUID.randomUUID());cp.setTenantId(u.getTenantId());cp.setUserId(u.getId());cp.setName(u.getName());cp.setEmail(u.getEmail());return customers.save(cp);});
  };
 }
 public static Map<String,List<String>> employeeRolePermissions(){return Map.ofEntries(
  Map.entry("SALES_EXECUTIVE",List.of("CATALOG_VIEW","CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","ORDER_CREATE")),
  Map.entry("SALES_MANAGER",List.of("CATALOG_VIEW","CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","DISCOUNT_APPROVE","ORDER_CREATE","REPORT_VIEW")),
  Map.entry("MANAGER",List.of("CATALOG_VIEW","CUSTOMER_MANAGE","LEAD_MANAGE","SURVEY_MANAGE","QUOTATION_MANAGE","DISCOUNT_APPROVE","ORDER_CREATE","REPORT_VIEW")),
  Map.entry("INVENTORY_MANAGER",List.of("CATALOG_MANAGE","INVENTORY_MANAGE","ORDER_CREATE")),
  Map.entry("SERVICE_ENGINEER",List.of("SURVEY_MANAGE","SERVICE_MANAGE")),
  Map.entry("ACCOUNTANT",List.of("FINANCE_MANAGE","REPORT_VIEW")),
  Map.entry("INSTALLER",List.of("SURVEY_MANAGE","SERVICE_MANAGE")),
  Map.entry("OPERATIONS_MANAGER",List.of("ORDER_CREATE","SURVEY_MANAGE","INVENTORY_MANAGE","SERVICE_MANAGE","REPORT_VIEW")));
 }
 private static void seedDemoProducts(UUID tenantId,ProductCategoryRepository categories,ProductRepository products){
  record Demo(String category,String sku,String name,String manufacturer,String model,String technology,String description,String image,java.math.BigDecimal purchase,java.math.BigDecimal price,java.math.BigDecimal discount,int warranty,String unit,String hsn,String attrs){}
  List<Demo> demos=List.of(
   new Demo("Solar Panels","DEMO-PV-550","550W N-Type TOPCon Solar Module","Demo Solar","DSM-550","N-Type TOPCon","Illustrative 550W PV module. Verify electrical ratings and certification against the manufacturer datasheet before sale.","https://images.unsplash.com/photo-1509391366360-2e959784a276?auto=format&fit=crop&w=1200&q=85",new java.math.BigDecimal("8500"),new java.math.BigDecimal("11200"),new java.math.BigDecimal("5"),25,"PCS","85414300","{\"powerWp\":550,\"cellType\":\"N-Type TOPCon\",\"moduleEfficiencyPct\":22.5,\"vocV\":49.8,\"iscA\":14.0,\"maxSystemVoltageV\":1500}"),
   new Demo("Solar Panels","DEMO-PV-450","450W Mono PERC Solar Module","Demo Solar","DSM-450","Mono PERC","Illustrative 450W PV module. Electrical values are sample data only.","https://images.unsplash.com/photo-1445620466293-d6316372ab59?auto=format&fit=crop&w=1200&q=85",new java.math.BigDecimal("6900"),new java.math.BigDecimal("9200"),new java.math.BigDecimal("3"),25,"PCS","85414300","{\"powerWp\":450,\"cellType\":\"Mono PERC\",\"moduleEfficiencyPct\":20.7}"),
   new Demo("Inverters","DEMO-INV-5K","5kW Single Phase Grid-Tied Inverter","Demo Power","DPI-5K","Transformerless string inverter","Illustrative inverter listing. Grid compliance and protection settings require local validation.","https://images.unsplash.com/photo-1503427315916-2ba435dee667?auto=format&fit=crop&w=1200&q=85",new java.math.BigDecimal("42000"),new java.math.BigDecimal("56500"),new java.math.BigDecimal("4"),10,"PCS","85044090","{\"ratedAcPowerKw\":5,\"phase\":\"Single phase\",\"mpptCount\":2,\"topology\":\"Transformerless\"}"),
   new Demo("Batteries","DEMO-BAT-5K","5.12kWh LiFePO4 Solar Battery","Demo Storage","DSB-5120","Lithium Iron Phosphate (LFP)","Illustrative LFP storage unit. Confirm BMS compatibility and safety certificates before sale.","https://images.unsplash.com/photo-1649549762433-2d1e62394dd1?auto=format&fit=crop&w=1200&q=85",new java.math.BigDecimal("69000"),new java.math.BigDecimal("89500"),new java.math.BigDecimal("2"),5,"PCS","85076000","{\"nominalEnergyKwh\":5.12,\"nominalVoltageV\":51.2,\"capacityAh\":100,\"chemistry\":\"LiFePO4\",\"cycleLife\":6000}"),
   new Demo("Mounting Structures","DEMO-MNT-ROOF","Aluminium Rooftop Mounting Kit","Demo Structures","DRK-01","Anodized aluminium rail system","Illustrative rooftop mounting kit. Roof-specific wind-load and structural calculations are required.","https://images.unsplash.com/photo-1514488034139-5905ab173d22?auto=format&fit=crop&w=1200&q=85",new java.math.BigDecimal("4800"),new java.math.BigDecimal("6500"),new java.math.BigDecimal("0"),5,"SET","76109090","{\"material\":\"Anodized aluminium\",\"roofType\":\"Verify roof compatibility\",\"fastenerGrade\":\"Stainless steel - demo\"}"),
   new Demo("Cables","DEMO-CAB-DC6","6 sq mm Solar DC Cable (per metre)","Demo Cables","DCC-6","Tinned copper PV cable","Illustrative PV cable listing. Verify IS/IEC certification and current rating from the actual supplier.","https://images.unsplash.com/photo-1558618666-fcd25c85cd64?auto=format&fit=crop&w=1200&q=85",new java.math.BigDecimal("42"),new java.math.BigDecimal("58"),new java.math.BigDecimal("0"),1,"MTR","85444999","{\"conductor\":\"Tinned copper\",\"crossSectionSqmm\":6,\"insulation\":\"UV resistant - verify\"}")
  );
  for(Demo d:demos){ProductCategory cat=categories.findByNameAndTenantId(d.category(),tenantId).orElse(null);if(cat==null)continue;if(products.findByTenantIdOrderByIdDesc(tenantId).stream().anyMatch(p->d.sku().equalsIgnoreCase(p.getSku())))continue;Product p=new Product();p.setId(UUID.randomUUID());p.setTenantId(tenantId);p.setCategoryId(cat.getId());p.setSku(d.sku());p.setName(d.name());p.setManufacturer(d.manufacturer());p.setModel(d.model());p.setTechnology(d.technology());p.setDescription(d.description());p.setPurchasePrice(d.purchase());p.setPrice(d.price());p.setDiscountPercent(d.discount());p.setTaxPercent(new java.math.BigDecimal("18"));p.setWarrantyYears(d.warranty());p.setImageUrl(d.image());p.setReorderLevel(new java.math.BigDecimal("5"));p.setUnitOfMeasure(d.unit());p.setHsnCode(d.hsn());p.setSerialized(true);p.setAttributesJson(d.attrs());p.setActive(true);products.save(p);}
 }
 private static Role ensureRole(RoleRepository r,UUID tenant,String name){return r.findByNameAndTenantId(name,tenant).orElseGet(()->{Role x=new Role();x.setId(UUID.randomUUID());x.setTenantId(tenant);x.setName(name);return r.save(x);});}
 private static void grant(Role role,List<String> codes,Map<String,Permission> pm){codes.stream().map(pm::get).filter(Objects::nonNull).forEach(role.getPermissions()::add);}
 private static void ensureUser(AppUserRepository users,PasswordEncoder encoder,UUID tenant,String name,String email,String password,String type,Role role){if(users.findByEmailIgnoreCase(email).isPresent())return;AppUser u=new AppUser();u.setId(UUID.randomUUID());u.setTenantId(tenant);u.setName(name);u.setEmail(email);u.setPasswordHash(encoder.encode(password));u.setAccountType(type);u.setEnabled(true);u.getRoles().add(role);users.save(u);}
}