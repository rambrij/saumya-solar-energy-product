package com.saumyasolar.service;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Permission;
import com.saumyasolar.domain.Role;
import com.saumyasolar.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthLifecycleServiceTest {
    private AppUserRepository users;
    private RefreshTokenRepository refresh;
    private PasswordEncoder encoder;
    private PermissionRepository permissions;
    private RoleRepository roles;
    private AuthLifecycleService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        refresh = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resets = mock(PasswordResetTokenRepository.class);
        MfaChallengeRepository mfa = mock(MfaChallengeRepository.class);
        users = mock(AppUserRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        encoder = mock(PasswordEncoder.class);
        roles = mock(RoleRepository.class);
        permissions = mock(PermissionRepository.class);
        service = new AuthLifecycleService(refresh, resets, mfa, users, tenants,
                roles, permissions,
                mock(CustomerProfileRepository.class), mock(ProductCategoryRepository.class),
                encoder, mock(NotificationService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maximumFailedLoginAttempts", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "lockoutDurationMinutes", 15L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "refreshTokenTtlDays", 7L);
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@example.test");
        user.setAccountType("PLATFORM");
        user.setEnabled(true);
        user.setPasswordHash("encoded");
        when(users.findForUpdateByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void repeatedFailuresLockAccountAndReturnGenericUnauthorized() {
        when(encoder.matches("wrong", "encoded")).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                    () -> service.authenticate("admin@example.test", "wrong"));
            assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
            assertEquals("Invalid email or password.", failure.getReason());
        }
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));
        when(encoder.matches("correct", "encoded")).thenReturn(true);
        assertThrows(ResponseStatusException.class,
                () -> service.authenticate("admin@example.test", "correct"));
        assertEquals(5, user.getFailedLoginAttempts());
    }

    @Test
    void successfulLoginAfterLockoutExpiryResetsCounter() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        when(encoder.matches("correct", "encoded")).thenReturn(true);
        AppUser authenticated = service.authenticate("admin@example.test", "correct");
        assertSame(user, authenticated);
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertNotNull(user.getLastLoginAt());
    }

    @Test
    void disabledAccountCannotAuthenticate() {
        user.setEnabled(false);
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class,
                        () -> service.authenticate("admin@example.test", "correct")).getStatusCode());
        verify(encoder, never()).matches(anyString(), anyString());
    }
    @Test
    void customRoleAcceptsOnlySrsVendorPermissionCodes() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Permission leadView = new Permission();
        leadView.setId(UUID.randomUUID());
        leadView.setCode("LEAD_VIEW");
        when(roles.findByNameAndTenantId("CUSTOM_SALES_READ", tenantId)).thenReturn(Optional.empty());
        when(permissions.findByCode("LEAD_VIEW")).thenReturn(Optional.of(leadView));
        when(roles.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role created = service.createCustomRole(tenantId, actorId, "custom_sales_read", java.util.Set.of("LEAD_VIEW"));
        assertEquals("CUSTOM_SALES_READ", created.getName());
        assertEquals(tenantId, created.getTenantId());
        assertTrue(created.getPermissions().stream().anyMatch(permission -> "LEAD_VIEW".equals(permission.getCode())));

        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> service.createCustomRole(tenantId, actorId, "CUSTOM_PLATFORM", java.util.Set.of("PLATFORM_MANAGE")));
        assertEquals(HttpStatus.FORBIDDEN, blocked.getStatusCode());
    }

    @Test
    void customTenantRoleCanBeAssignedToAnEmployeeInTheSameTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        AppUser employee = new AppUser();
        employee.setId(employeeId);
        employee.setTenantId(tenantId);
        employee.setAccountType("VENDOR");
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName("CUSTOM_SALES_READ");
        when(users.findById(employeeId)).thenReturn(Optional.of(employee));
        when(roles.findByNameAndTenantId("CUSTOM_SALES_READ", tenantId)).thenReturn(Optional.of(role));
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser assigned = service.assignEmployeeRole(tenantId, actorId, employeeId, "CUSTOM_SALES_READ");

        assertEquals(1, assigned.getRoles().size());
        assertEquals(role.getId(), assigned.getRoles().iterator().next().getId());
    }

    @Test
    void replayOfRotatedRefreshTokenRevokesEveryTokenInItsFamily() {
        UUID familyId = UUID.randomUUID();
        var oldToken = new com.saumyasolar.domain.RefreshToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setUserId(user.getId());
        oldToken.setFamilyId(familyId);
        oldToken.setToken("old-refresh-token");
        oldToken.setRevoked(true);
        oldToken.setExpiresAt(Instant.now().plusSeconds(600));

        var activeDescendant = new com.saumyasolar.domain.RefreshToken();
        activeDescendant.setId(UUID.randomUUID());
        activeDescendant.setUserId(user.getId());
        activeDescendant.setFamilyId(familyId);
        activeDescendant.setToken("active-descendant");
        activeDescendant.setExpiresAt(Instant.now().plusSeconds(600));

        when(refresh.findByTokenForUpdate("old-refresh-token")).thenReturn(Optional.of(oldToken));
        when(refresh.findByFamilyIdForUpdate(familyId)).thenReturn(java.util.List.of(oldToken, activeDescendant));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.consumeRefresh("old-refresh-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
        assertTrue(oldToken.isRevoked());
        assertTrue(activeDescendant.isRevoked());
        verify(refresh).save(activeDescendant);
    }

}
