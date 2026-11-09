package com.saumyasolar.service;

import com.saumyasolar.domain.AppUser;
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
    private PasswordEncoder encoder;
    private AuthLifecycleService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        RefreshTokenRepository refresh = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resets = mock(PasswordResetTokenRepository.class);
        MfaChallengeRepository mfa = mock(MfaChallengeRepository.class);
        users = mock(AppUserRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new AuthLifecycleService(refresh, resets, mfa, users, tenants,
                mock(RoleRepository.class), mock(PermissionRepository.class),
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
}
