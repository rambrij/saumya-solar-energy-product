package com.saumyasolar.config;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedData(
            TenantRepository tr,
            PermissionRepository pr,
            RoleRepository rr,
            AppUserRepository ur,
            CustomerProfileRepository cr,
            PasswordEncoder e
    ) {
        return a -> {
            if (ur.count() > 0) return;
            var t = new Tenant();
            t.setId(UUID.randomUUID());
            t.setName("Saumya Solar");
            t.setSlug("saumya-solar");
            t.setStatus("ACTIVE");
            tr.save(t);
            var all = List.of("PLATFORM_MANAGE", "CATALOG_MANAGE", "CUSTOMER_MANAGE", "LEAD_MANAGE", "SURVEY_MANAGE", "QUOTATION_MANAGE", "DISCOUNT_APPROVE", "ORDER_CREATE", "USER_VIEW");
            var pm = new HashMap<String, Permission>();
            for (var c : all) {
                var p = new Permission();
                p.setId(UUID.randomUUID());
                p.setCode(c);
                pm.put(c, pr.save(p));
            }
            var platform = role(rr, null, "PLATFORM_SUPER_ADMIN", List.of(pm.get("PLATFORM_MANAGE")));
            var vendor = role(rr, t.getId(), "VENDOR_ADMIN", pm.values().stream().filter(x -> !x.getCode().equals("PLATFORM_MANAGE")).toList());
            var sales = role(rr, t.getId(), "SALES_EXECUTIVE", List.of(pm.get("CUSTOMER_MANAGE"), pm.get("LEAD_MANAGE"), pm.get("SURVEY_MANAGE"), pm.get("QUOTATION_MANAGE")));
            var customer = role(rr, null, "CUSTOMER", List.of());
            ur.save(user(null, "Platform Admin", "platform@saumya.local", "Platform@123", "PLATFORM", platform, e));
            ur.save(user(t.getId(), "Vendor Admin", "admin@saumya.local", "Admin@123", "VENDOR", vendor, e));
            ur.save(user(t.getId(), "Sales User", "sales@saumya.local", "Sales@123", "VENDOR", sales, e));
            var cu = ur.save(user(t.getId(), "Demo Customer", "customer@saumya.local", "Customer@123", "CUSTOMER", customer, e));
            var cp = new CustomerProfile();
            cp.setId(UUID.randomUUID());
            cp.setTenantId(t.getId());
            cp.setUserId(cu.getId());
            cp.setName(cu.getName());
            cp.setEmail(cu.getEmail());
            cr.save(cp);
        };
    }
    
    Role role(RoleRepository r, UUID t, String n, Collection<Permission> p) {
        var x = new Role();
        x.setId(UUID.randomUUID());
        x.setTenantId(t);
        x.setName(n);
        x.getPermissions().addAll(p);
        return r.save(x);
    }
    
    AppUser user(UUID t, String n, String mail, String pass, String type, Role role, PasswordEncoder e) {
        var u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(t);
        u.setName(n);
        u.setEmail(mail);
        u.setPasswordHash(e.encode(pass));
        u.setAccountType(type);
        u.setEnabled(true);
        u.getRoles().add(role);
        return u;
    }
}
