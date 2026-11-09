package com.saumyasolar.service;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.CustomerProfile;
import com.saumyasolar.domain.PasswordResetToken;
import com.saumyasolar.domain.MfaChallenge;
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
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
@Service
public class AuthLifecycleService {
    private static final java.util.Set<String> VENDOR_ROLE_PERMISSION_CODES = java.util.Set.of(
            "USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_DISABLE",
            "ROLE_VIEW", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_ASSIGN",
            "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_EXPORT",
            "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE",
            "PACKAGE_VIEW", "PACKAGE_CREATE", "PACKAGE_UPDATE", "PACKAGE_PUBLISH",
            "LEAD_VIEW", "LEAD_CREATE", "LEAD_UPDATE", "LEAD_ASSIGN", "LEAD_EXPORT",
            "SURVEY_VIEW", "SURVEY_CREATE", "SURVEY_ASSIGN", "SURVEY_COMPLETE",
            "QUOTATION_VIEW", "QUOTATION_CREATE", "QUOTATION_UPDATE", "QUOTATION_SEND",
            "QUOTATION_APPROVE_DISCOUNT", "QUOTATION_CONVERT",
            "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_CANCEL", "ORDER_ASSIGN",
            "INVENTORY_VIEW", "INVENTORY_RECEIVE", "INVENTORY_RESERVE", "INVENTORY_TRANSFER",
            "INVENTORY_ADJUST", "INVENTORY_EXPORT",
            "INSTALLATION_VIEW", "INSTALLATION_SCHEDULE", "INSTALLATION_ASSIGN",
            "INSTALLATION_UPDATE", "INSTALLATION_COMMISSION",
            "INVOICE_VIEW", "INVOICE_CREATE", "INVOICE_SEND", "INVOICE_VOID",
            "PAYMENT_VIEW", "PAYMENT_RECORD", "PAYMENT_REFUND",
            "SERVICE_VIEW", "SERVICE_CREATE", "SERVICE_ASSIGN", "SERVICE_UPDATE",
            "SERVICE_RESOLVE", "SERVICE_CLOSE", "REPORT_VIEW", "REPORT_EXPORT",
            "CMS_VIEW", "CMS_CREATE", "CMS_UPDATE", "CMS_PUBLISH", "AUDIT_VIEW"
    );
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EnterpriseSaasService enterpriseSaas;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuthenticationAuditService authenticationAudit;

    private void auditAuth(String action, AppUser user, String outcome, String reason, String entityId) {
        if (authenticationAudit == null) return;
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        var request = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet ? servlet.getRequest() : null;
        authenticationAudit.record(action, user == null ? null : user.getTenantId(), user == null ? null : user.getId(), outcome, reason, entityId, request);
    }
    @org.springframework.beans.factory.annotation.Value("${app.auth.lockout.max-failed-attempts:5}")
    int maximumFailedLoginAttempts;
    @org.springframework.beans.factory.annotation.Value("${app.auth.lockout.duration-minutes:15}")
    long lockoutDurationMinutes;
    @org.springframework.beans.factory.annotation.Value("${app.auth.refresh-token-ttl-days:7}")
    long refreshTokenTtlDays;
    final RefreshTokenRepository refresh;
    final PasswordResetTokenRepository resets;
    final MfaChallengeRepository mfaChallenges;
    final AppUserRepository users;
    final TenantRepository tenants;
    final RoleRepository roles;
    final PermissionRepository permissions;
    final CustomerProfileRepository customerProfiles;
    final ProductCategoryRepository categories;
    final PasswordEncoder encoder;
    final NotificationService notifications;
    
    AuthLifecycleService(
            RefreshTokenRepository r,
            PasswordResetTokenRepository p,
            MfaChallengeRepository mfaChallenges,
            AppUserRepository u,
            TenantRepository t,
            RoleRepository rr,
            PermissionRepository permissions,
            CustomerProfileRepository customerProfiles,
            ProductCategoryRepository categories,
            PasswordEncoder e,
            NotificationService notifications
    ) {
        refresh = r;
        resets = p;
        this.mfaChallenges = mfaChallenges;
        users = u;
        tenants = t;
        roles = rr;
        this.permissions = permissions;
        this.customerProfiles = customerProfiles;
        this.categories = categories;
        encoder = e;
        this.notifications = notifications;
    }
    
    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AppUser authenticate(String email, String password) {
        String genericFailure = "Invalid email or password.";
        AppUser user = users.findForUpdateByEmailIgnoreCase(email == null ? "" : email.trim()).orElse(null);
        if (user == null || !user.isEnabled() || !tenantIsActive(user)) {
            auditAuth("LOGIN_FAILED", user, "DENIED", "INVALID_CREDENTIALS_OR_INACTIVE_ACCOUNT", null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, genericFailure);
        }
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditAuth("LOGIN_LOCKED", user, "DENIED", "ACCOUNT_TEMPORARILY_LOCKED", null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, genericFailure);
        }
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= Math.max(1, maximumFailedLoginAttempts))
                user.setLockedUntil(now.plus(Duration.ofMinutes(Math.max(1, lockoutDurationMinutes))));
            users.save(user);
            auditAuth(attempts >= Math.max(1, maximumFailedLoginAttempts) ? "LOGIN_LOCKOUT" : "LOGIN_FAILED", user, "DENIED", attempts >= Math.max(1, maximumFailedLoginAttempts) ? "FAILED_ATTEMPT_THRESHOLD_REACHED" : "INVALID_CREDENTIALS", null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, genericFailure);
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        AppUser authenticated = users.save(user);
        auditAuth("LOGIN_SUCCEEDED", authenticated, "SUCCESS", "PASSWORD_AUTHENTICATION", null);
        return authenticated;
    }

    public boolean tenantIsActive(AppUser user) {
        if (user.getTenantId() == null) return "PLATFORM".equals(user.getAccountType());
        return tenants.findById(user.getTenantId()).map(t -> "ACTIVE".equals(t.getStatus())).orElse(false);
    }
    
    public String newRefresh(AppUser u) { return newRefresh(u, UUID.randomUUID()); }

    private String newRefresh(AppUser u, UUID familyId) {
        var x = new RefreshToken();
        x.setId(UUID.randomUUID());
        x.setUserId(u.getId());
        x.setFamilyId(familyId);
        x.setCreatedAt(Instant.now());
        x.setToken(UUID.randomUUID() + "." + UUID.randomUUID());
        x.setExpiresAt(Instant.now().plus(Duration.ofDays(Math.max(1, refreshTokenTtlDays))));
        refresh.save(x);
        return x.getToken();
    }
    
    public record RefreshRotation(AppUser user, String refreshToken) {}

    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public RefreshRotation rotateRefresh(String token) {
        AppUser user = consumeRefresh(token);
        RefreshToken previous = refresh.findByToken(token).orElseThrow();
        String rotated = newRefresh(user, previous.getFamilyId());
        RefreshToken replacement = refresh.findByToken(rotated).orElseThrow();
        previous.setReplacedByTokenId(replacement.getId());
        refresh.save(previous);
        return new RefreshRotation(user, rotated);
    }

    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AppUser consumeRefresh(String token) {
        var x = refresh.findByTokenForUpdate(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (x.isRevoked()) {
            if (x.getFamilyId() != null) refresh.findByFamilyIdForUpdate(x.getFamilyId()).forEach(session -> { session.setRevoked(true); refresh.save(session); });
            else refresh.findByUserIdAndRevokedFalse(x.getUserId()).forEach(session -> { session.setRevoked(true); refresh.save(session); });
            auditAuth("REFRESH_REPLAY_DETECTED", users.findById(x.getUserId()).orElse(null), "DENIED", "REFRESH_TOKEN_REUSE", x.getFamilyId() == null ? null : x.getFamilyId().toString());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; token family revoked");
        }
        if (!x.getExpiresAt().isAfter(Instant.now())) {
            x.setRevoked(true);
            refresh.save(x);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        x.setRevoked(true);
        refresh.save(x);
        AppUser refreshed = users.findById(x.getUserId())
                .filter(u -> u.isEnabled() && tenantIsActive(u))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        auditAuth("REFRESH_ROTATED", refreshed, "SUCCESS", "REFRESH_TOKEN_ROTATED", x.getFamilyId() == null ? null : x.getFamilyId().toString());
        return refreshed;
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
            auditAuth("LOGOUT", users.findById(x.getUserId()).orElse(null), "SUCCESS", "SESSION_REVOKED", x.getFamilyId() == null ? null : x.getFamilyId().toString());
        });
    }
    
    public Map<String, String> forgot(String email) {
        Map<String, String> out = Map.of("message", "If the account exists, reset instructions were sent");
        if (email == null || email.isBlank()) return out;
        users.findByEmailIgnoreCase(email.trim()).filter(AppUser::isEnabled).ifPresent(user -> {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            var reset = new PasswordResetToken();
            reset.setId(UUID.randomUUID());
            reset.setUserId(user.getId());
            reset.setToken(hashResetToken(rawToken));
            reset.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
            resets.save(reset);
            auditAuth("PASSWORD_RESET_REQUESTED", user, "SUCCESS", "RESET_REQUEST_ACCEPTED", null);
            notifications.sendPasswordReset(user.getId(), user.getTenantId(), user.getEmail(), user.getName(), rawToken);
        });
        return out;
    }
    
    public String createMfaChallenge(AppUser user) {
        byte[] random = new byte[4];
        new SecureRandom().nextBytes(random);
        int value = java.nio.ByteBuffer.wrap(random).getInt() & 0x7fffffff;
        String code = String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
        MfaChallenge challenge = new MfaChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setUserId(user.getId());
        challenge.setCodeHash(hashResetToken(code));
        challenge.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        challenge.setAttempts(0);
        challenge.setUsed(false);
        challenge.setCreatedAt(Instant.now());
        mfaChallenges.save(challenge);
        if (!notifications.sendMfaCode(user.getId(), user.getTenantId(), user.getEmail(), user.getName(), code)) {
            challenge.setUsed(true);
            mfaChallenges.save(challenge);
            auditAuth("MFA_CHALLENGE_DELIVERY_FAILED", user, "FAILED", "EMAIL_MFA_DELIVERY_FAILED", challenge.getId().toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to send verification code");
        }
        auditAuth("MFA_CHALLENGE_CREATED", user, "SUCCESS", "EMAIL_MFA_CHALLENGE", challenge.getId().toString());
        return challenge.getId().toString();
    }
    
    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AppUser verifyMfaChallenge(String challengeId, String code) {
        UUID id;
        try {
            id = UUID.fromString(challengeId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        MfaChallenge challenge = mfaChallenges.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (challenge.isUsed() || !challenge.getExpiresAt().isAfter(Instant.now()) || challenge.getAttempts() >= 5) {
            challenge.setUsed(true);
            mfaChallenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verification code expired or invalid");
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        boolean matches = code != null && MessageDigest.isEqual(
                hashResetToken(code).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                challenge.getCodeHash().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        if (!matches) {
            if (challenge.getAttempts() >= 5) challenge.setUsed(true);
            mfaChallenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verification code expired or invalid");
        }
        challenge.setUsed(true);
        mfaChallenges.save(challenge);
        AppUser verified = users.findById(challenge.getUserId()).filter(AppUser::isEnabled)
                .filter(this::tenantIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        auditAuth("MFA_VERIFIED", verified, "SUCCESS", "EMAIL_MFA_VERIFICATION", challenge.getId().toString());
        return verified;
    }
    
    private String hashResetToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
    
    @Transactional
    public void reset(String token, String password) {
        var x = resets.findByToken(hashResetToken(token)).filter(v -> !v.isUsed() && v.getExpiresAt().isAfter(Instant.now())).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        var u = users.findById(x.getUserId()).orElseThrow();
        if (password == null || password.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters");
        u.setPasswordHash(encoder.encode(password));
        auditAuth("PASSWORD_RESET_COMPLETED", u, "SUCCESS", "PASSWORD_CHANGED", null);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
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
        slug = normalizeSlug(slug == null || slug.isBlank() ? name : slug);
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
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        tenants.save(tenant);
        
        var vendorAdmin = new com.saumyasolar.domain.Role();
        vendorAdmin.setId(UUID.randomUUID());
        vendorAdmin.setTenantId(tenant.getId());
        vendorAdmin.setName("VENDOR_ADMIN");
        permissions.findAll().stream().filter(permission -> !java.util.Set.of("PLATFORM_MANAGE", "SUBSCRIPTION_MANAGE", "TENANT_VIEW", "TENANT_UPDATE", "CATALOG_MANAGE", "CATALOG_VIEW", "DISCOUNT_POLICY_MANAGE", "CUSTOMER_MANAGE", "LEAD_MANAGE", "SURVEY_MANAGE", "QUOTATION_MANAGE", "DISCOUNT_APPROVE", "USER_MANAGE", "INVENTORY_MANAGE", "SERVICE_MANAGE", "FINANCE_MANAGE").contains(permission.getCode()))
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
    
    public java.util.List<Permission> vendorRolePermissions() {
        return permissions.findAll().stream()
                .filter(permission -> VENDOR_ROLE_PERMISSION_CODES.contains(permission.getCode()))
                .sorted(java.util.Comparator.comparing(Permission::getCode))
                .toList();
    }

    @Transactional
    public com.saumyasolar.domain.Role createCustomRole(UUID tenantId, UUID actorId, String name, java.util.Set<String> permissionCodes) {
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String normalized = name == null ? "" : name.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{2,49}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name must use 3-50 uppercase letters, digits or underscores");
        if (java.util.Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "PLATFORM_SUPPORT", "CUSTOMER").contains(normalized)
                || com.saumyasolar.config.DataSeeder.employeeRolePermissions().containsKey(normalized))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved role names cannot be created as custom roles");
        if (roles.findByNameAndTenantId(normalized, tenantId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists");
        var role = new com.saumyasolar.domain.Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName(normalized);
        role.setCustomRole(true);
        role.getPermissions().addAll(resolveVendorPermissions(permissionCodes));
        var saved = roles.save(role);
        auditAuth("CUSTOM_ROLE_CREATED", users.findById(actorId).orElse(null), "SUCCESS", "CUSTOM_ROLE_CREATED_" + normalized, saved.getId().toString());
        return saved;
    }

    @Transactional
    public com.saumyasolar.domain.Role updateCustomRole(UUID tenantId, UUID actorId, UUID roleId, java.util.Set<String> permissionCodes) {
        if (tenantId == null || roleId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var role = roles.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (java.util.Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "PLATFORM_SUPPORT", "CUSTOMER").contains(role.getName())
                || com.saumyasolar.config.DataSeeder.employeeRolePermissions().containsKey(role.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System roles cannot be modified");
        role.setCustomRole(true);
        role.getPermissions().clear();
        role.getPermissions().addAll(resolveVendorPermissions(permissionCodes));
        var saved = roles.save(role);
        auditAuth("CUSTOM_ROLE_UPDATED", users.findById(actorId).orElse(null), "SUCCESS", "CUSTOM_ROLE_UPDATED_" + role.getName(), saved.getId().toString());
        return saved;
    }

    @Transactional
    public AppUser assignEmployeeRole(UUID tenantId, UUID actorId, UUID employeeId, String roleName) {
        if (tenantId == null || employeeId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (roleName == null || java.util.Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "PLATFORM_SUPPORT", "CUSTOMER").contains(roleName))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This role cannot be assigned to a vendor employee");
        if (employeeId.equals(actorId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrators cannot change their own role");
        AppUser employee = users.findById(employeeId)
                .filter(user -> tenantId.equals(user.getTenantId()) && "VENDOR".equals(user.getAccountType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (employee.getRoles().stream().anyMatch(existing -> "VENDOR_ADMIN".equals(existing.getName())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendor administrator roles cannot be reassigned");
        var role = roles.findByNameAndTenantId(roleName, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role does not belong to this vendor"));
        assertVendorAssignableRole(role);
        employee.getRoles().clear();
        employee.getRoles().add(role);
        AppUser saved = users.save(employee);
        auditAuth("EMPLOYEE_ROLE_ASSIGNED", users.findById(actorId).orElse(null), "SUCCESS", "EMPLOYEE_ROLE_ASSIGNED_" + roleName, employeeId.toString());
        return saved;
    }

    @Transactional
    public void deleteCustomRole(UUID tenantId, UUID actorId, UUID roleId) {
        if (tenantId == null || roleId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var role = roles.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (java.util.Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "PLATFORM_SUPPORT", "CUSTOMER").contains(role.getName())
                || com.saumyasolar.config.DataSeeder.employeeRolePermissions().containsKey(role.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System roles cannot be deleted");
        if (users.existsByTenantIdAndRoles_Id(tenantId, roleId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role is assigned to one or more employees");
        roles.delete(role);
        auditAuth("CUSTOM_ROLE_DELETED", users.findById(actorId).orElse(null), "SUCCESS", "CUSTOM_ROLE_DELETED_" + role.getName(), roleId.toString());
    }

    private void assertVendorAssignableRole(com.saumyasolar.domain.Role role) {
        if (role.getPermissions() == null || role.getPermissions().stream()
                .anyMatch(permission -> !VENDOR_ROLE_PERMISSION_CODES.contains(permission.getCode())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role contains permissions not assignable to vendor employees");
    }

    private java.util.Set<Permission> resolveVendorPermissions(java.util.Set<String> codes) {
        var result = new java.util.HashSet<Permission>();
        if (codes == null) return result;
        for (String code : codes) {
            if (code == null || !VENDOR_ROLE_PERMISSION_CODES.contains(code))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission is not assignable to a vendor custom role");
            result.add(permissions.findByCode(code)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission is not installed: " + code)));
        }
        return result;
    }

    @Transactional
    public AppUser createEmployee(UUID tenantId, UUID actorId, String name, String email, String password, String roleName) {
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (name == null || name.isBlank() || email == null || email.isBlank()
                || password == null || password.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email and a password of at least 12 characters are required");
        if (roleName == null || java.util.Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "PLATFORM_SUPPORT", "CUSTOMER").contains(roleName))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This role cannot be assigned to a vendor employee");
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email exists");
        var role = roles.findByNameAndTenantId(roleName, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is not configured for this vendor"));
        assertVendorAssignableRole(role);
        var u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setName(name.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setAccountType("VENDOR");
        u.setEnabled(true);
        u.getRoles().add(role);
        AppUser created = users.save(u);
        auditAuth("USER_ROLE_ASSIGNED", users.findById(actorId).orElse(null), "SUCCESS", "VENDOR_EMPLOYEE_CREATED_WITH_ROLE_" + roleName, created.getId().toString());
        return created;
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
        AppUser updated = users.save(employee);
        auditAuth(enabled ? "USER_ACTIVATED" : "USER_DISABLED", users.findById(actorId).orElse(null), "SUCCESS", "VENDOR_EMPLOYEE_STATUS_CHANGED", employeeId.toString());
        return updated;
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
        for (String name : java.util.List.of(
                "Solar Panels", "Inverters", "Batteries", "Mounting Structures",
                "Cables", "AC/DC Distribution Boxes", "Solar Accessories"
        )) {
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
    
    private String normalizeSlug(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
    
    @Transactional
    public AppUser register(String slug, String name, String email, String password) {
        String normalizedSlug = normalizeSlug(slug);
        var store = tenants.findBySlug(normalizedSlug);
        if (store.isEmpty() && slug != null && !slug.isBlank()) {
            store = tenants.findByNameIgnoreCase(slug.trim());
        }
        var t = store.filter(x -> "ACTIVE".equals(x.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No active store matches '" + slug + "'. Enter the vendor store slug or exact store name."
                ));
        if (name == null || name.isBlank() || email == null || email.isBlank()
                || password == null || password.length() < 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email and a password of at least 12 characters are required");
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email exists");
        if (enterpriseSaas != null) enterpriseSaas.enforceCustomerLimit(t.getId());
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
