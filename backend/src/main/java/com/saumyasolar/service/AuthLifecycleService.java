package com.saumyasolar.service;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.CustomerProfile;
import com.saumyasolar.domain.PasswordResetToken;
import com.saumyasolar.domain.RefreshToken;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.domain.Permission;
import com.saumyasolar.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@Service
public class AuthLifecycleService {
    final RefreshTokenRepository refresh;
    final PasswordResetTokenRepository resets;
    final AppUserRepository users;
    final TenantRepository tenants;
    final RoleRepository roles;
    final PermissionRepository permissions;
    final CustomerProfileRepository customerProfiles;
    final ProductCategoryRepository categories;
    final PasswordEncoder encoder;
    
    AuthLifecycleService(
            RefreshTokenRepository r,
            PasswordResetTokenRepository p,
            AppUserRepository u,
            TenantRepository t,
            RoleRepository rr,
            PermissionRepository permissions,
            CustomerProfileRepository customerProfiles,
            ProductCategoryRepository categories,
            PasswordEncoder e
    ) {
        refresh = r;
        resets = p;
        users = u;
        tenants = t;
        roles = rr;
        this.permissions = permissions;
        this.customerProfiles = customerProfiles;
        this.categories = categories;
        encoder = e;
    }
    
    public boolean tenantIsActive(AppUser user) {
        if (user.getTenantId() == null) return "PLATFORM".equals(user.getAccountType());
        return tenants.findById(user.getTenantId()).map(t -> "ACTIVE".equals(t.getStatus())).orElse(false);
    }

    public String newRefresh(AppUser u) {
        var x = new RefreshToken();
        x.setId(UUID.randomUUID());
        x.setUserId(u.getId());
        x.setToken(UUID.randomUUID() + "." + UUID.randomUUID());
        x.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        refresh.save(x);
        return x.getToken();
    }
    
    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AppUser consumeRefresh(String token) {
        var x = refresh.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (x.isRevoked()) {
            refresh.findByUserIdAndRevokedFalse(x.getUserId()).forEach(session -> {
                session.setRevoked(true);
                refresh.save(session);
            });
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; sessions revoked");
        }
        if (!x.getExpiresAt().isAfter(Instant.now())) {
            x.setRevoked(true);
            refresh.save(x);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        x.setRevoked(true);
        refresh.save(x);
        return users.findById(x.getUserId())
                .filter(u -> u.isEnabled() && tenantIsActive(u))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        refresh.findByToken(token).ifPresent(x -> {
            x.setRevoked(true);
            refresh.save(x);
            users.findById(x.getUserId()).ifPresent(user -> {
                user.setTokenVersion(user.getTokenVersion() + 1);
                users.save(user);
            });
            refresh.findByUserIdAndRevokedFalse(x.getUserId()).forEach(session -> {
                session.setRevoked(true);
                refresh.save(session);
            });
        });
    }
    
    public Map<String, String> forgot(String email) {
        var out = new HashMap<String, String>();
        out.put("message", "If the account exists, reset instructions were created");
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            var x = new PasswordResetToken();
            x.setId(UUID.randomUUID());
            x.setUserId(u.getId());
            x.setToken(UUID.randomUUID().toString());
            x.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
            resets.save(x);
            out.put("devResetToken", x.getToken());
        });
        return out;
    }
    
    @Transactional
    public void reset(String token, String password) {
        var x = resets.findByToken(token).filter(v -> !v.isUsed() && v.getExpiresAt().isAfter(Instant.now())).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        var u = users.findById(x.getUserId()).orElseThrow();
        if (password == null || password.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters");
        u.setPasswordHash(encoder.encode(password));
        u.setTokenVersion(u.getTokenVersion() + 1);
        users.save(u);
        x.setUsed(true);
        resets.save(x);
        refresh.findByUserIdAndRevokedFalse(u.getId()).forEach(session -> {
            session.setRevoked(true);
            refresh.save(session);
        });
    }
    
    @Transactional
    public Tenant createTenant(String name, String slug, String adminName, String adminEmail, String adminPassword) {
        if (name == null || name.isBlank() || slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || adminName == null || adminName.isBlank() || adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant and initial vendor administrator details are required");
        if (tenants.findBySlug(slug).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant slug already exists");
        if (users.findByEmailIgnoreCase(adminEmail).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrator email already exists");

        var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name.trim());
        tenant.setSlug(slug);
        tenant.setStatus("ACTIVE");
        tenants.save(tenant);

        var vendorAdmin = new com.saumyasolar.domain.Role();
        vendorAdmin.setId(UUID.randomUUID());
        vendorAdmin.setTenantId(tenant.getId());
        vendorAdmin.setName("VENDOR_ADMIN");
        permissions.findAll().stream().filter(permission -> !"PLATFORM_MANAGE".equals(permission.getCode()))
                .forEach(vendorAdmin.getPermissions()::add);
        roles.save(vendorAdmin);

        provisionEmployeeRoles(tenant.getId());
        seedCategories(tenant.getId());

        var admin = new AppUser();
        admin.setId(UUID.randomUUID());
        admin.setTenantId(tenant.getId());
        admin.setName(adminName.trim());
        admin.setEmail(adminEmail.trim().toLowerCase());
        admin.setPasswordHash(encoder.encode(adminPassword));
        admin.setAccountType("VENDOR");
        admin.setEnabled(true);
        admin.getRoles().add(vendorAdmin);
        users.save(admin);
        return tenant;
    }

    @Transactional
    public AppUser createEmployee(UUID tenantId, String name, String email, String password, String roleName) {
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (name == null || name.isBlank() || email == null || email.isBlank()
                || password == null || password.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email and a password of at least 12 characters are required");
        if (roleName == null || !com.saumyasolar.config.DataSeeder.employeeRolePermissions().containsKey(roleName))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a supported employee role");
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email exists");
        var role = roles.findByNameAndTenantId(roleName, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is not configured for this vendor"));
        var u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setName(name.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setAccountType("VENDOR");
        u.setEnabled(true);
        u.getRoles().add(role);
        return users.save(u);
    }

    @Transactional
    public AppUser setEmployeeEnabled(UUID tenantId, UUID actorId, UUID employeeId, boolean enabled) {
        if (tenantId == null || employeeId == null || employeeId.equals(actorId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change this employee account");
        var employee = users.findById(employeeId)
                .filter(u -> tenantId.equals(u.getTenantId()) && "VENDOR".equals(u.getAccountType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found in this vendor"));
        if (employee.getRoles().stream().anyMatch(role -> "VENDOR_ADMIN".equals(role.getName())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendor admin accounts cannot be disabled through this endpoint");
        employee.setEnabled(enabled);
        employee.setTokenVersion(employee.getTokenVersion() + 1);
        return users.save(employee);
    }

    private void provisionEmployeeRoles(UUID tenantId) {
        var permissionByCode = permissions.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Permission::getCode, x -> x));
        com.saumyasolar.config.DataSeeder.employeeRolePermissions().forEach((name, codes) -> {
            var role = roles.findByNameAndTenantId(name, tenantId).orElseGet(() -> {
                var created = new com.saumyasolar.domain.Role();
                created.setId(UUID.randomUUID());
                created.setTenantId(tenantId);
                created.setName(name);
                return created;
            });
            codes.stream().map(permissionByCode::get).filter(java.util.Objects::nonNull)
                    .forEach(role.getPermissions()::add);
            roles.save(role);
        });
    }

    private void seedCategories(UUID tenantId) {
        for (String name : java.util.List.of("Solar Panels", "Inverters", "Batteries", "Mounting Structures",
                "Cables", "AC/DC Distribution Boxes", "Solar Accessories")) {
            categories.findByNameAndTenantId(name, tenantId).orElseGet(() -> {
                var category = new com.saumyasolar.domain.ProductCategory();
                category.setId(UUID.randomUUID());
                category.setTenantId(tenantId);
                category.setName(name);
                category.setDescription("Solar catalogue category");
                category.setActive(true);
                return categories.save(category);
            });
        }
    }

    @Transactional
    public AppUser register(String slug, String name, String email, String password) {
        var t = tenants.findBySlug(slug).filter(x -> "ACTIVE".equals(x.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active store not found"));
        if (name == null || name.isBlank() || email == null || email.isBlank()
                || password == null || password.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email and a password of at least 12 characters are required");
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email exists");
        var u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(t.getId());
        u.setName(name);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setAccountType("CUSTOMER");
        u.setEnabled(true);
        u.getRoles().add(roles.findByNameAndTenantIdIsNull("CUSTOMER").orElseThrow());
        var saved = users.save(u);
        var profile = new CustomerProfile();
        profile.setId(UUID.randomUUID());
        profile.setTenantId(t.getId());
        profile.setUserId(saved.getId());
        profile.setName(saved.getName());
        profile.setEmail(saved.getEmail());
        customerProfiles.save(profile);
        return saved;
    }
}
