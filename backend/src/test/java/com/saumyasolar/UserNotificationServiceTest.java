package com.saumyasolar;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.UserNotification;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.UserNotificationRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.UserNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserNotificationServiceTest {
    @Test
    void marksOnlyTheAuthenticatedUsersNotificationAsRead() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UserNotificationService service = new UserNotificationService(repository, users);
        UUID userId = UUID.randomUUID(), tenantId = UUID.randomUUID(), notificationId = UUID.randomUUID();
        Principal actor = new Principal(userId, tenantId, "customer@example.test", "CUSTOMER",
                Set.of("CUSTOMER"), Set.of(), 0);
        UserNotification row = new UserNotification();
        row.setId(notificationId); row.setUserId(userId); row.setTenantId(tenantId);
        when(repository.findByIdAndUserIdAndTenantId(notificationId, userId, tenantId)).thenReturn(Optional.of(row));
        when(repository.save(row)).thenReturn(row);
        UserNotification result = service.markRead(notificationId, actor);
        assertNotNull(result.getReadAt());
        verify(repository).save(row);
    }

    @Test
    void doesNotDiscloseAnotherUsersNotification() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        UserNotificationService service = new UserNotificationService(repository, mock(AppUserRepository.class));
        UUID userId = UUID.randomUUID(), tenantId = UUID.randomUUID();
        Principal actor = new Principal(userId, tenantId, "customer@example.test", "CUSTOMER",
                Set.of("CUSTOMER"), Set.of(), 0);
        UUID otherUsersNotification = UUID.randomUUID();
        when(repository.findByIdAndUserIdAndTenantId(otherUsersNotification, userId, tenantId)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.markRead(otherUsersNotification, actor));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void marksAllReadOnlyWithinTheCurrentTenantAndUser() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        UserNotificationService service = new UserNotificationService(repository, mock(AppUserRepository.class));
        UUID userId = UUID.randomUUID(), tenantId = UUID.randomUUID();
        Principal actor = new Principal(userId, tenantId, "vendor@example.test", "VENDOR",
                Set.of("VENDOR_ADMIN"), Set.of("NOTIFICATION_VIEW"), 0);
        when(repository.markAllTenantRead(eq(userId), eq(tenantId), any(Instant.class))).thenReturn(3);
        assertEquals(3, service.markAllRead(actor));
        verify(repository).markAllTenantRead(eq(userId), eq(tenantId), any(Instant.class));
        verify(repository, never()).markAllPlatformRead(any(), any());
    }

    @Test
    void createsInboxRecordOnlyForAnEnabledUserInTheSameTenant() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UserNotificationService service = new UserNotificationService(repository, users);
        UUID userId = UUID.randomUUID(), tenantId = UUID.randomUUID();
        AppUser user = new AppUser(); user.setEnabled(true);
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
        when(repository.existsByDedupeKey(anyString())).thenReturn(false);
        when(repository.save(any(UserNotification.class))).thenAnswer(call -> call.getArgument(0));
        service.createBusinessNotification(userId, tenantId, "ORDER_CONFIRMED", "Order confirmed", "Your order is confirmed");
        verify(repository).save(argThat(row -> tenantId.equals(row.getTenantId())
                && userId.equals(row.getUserId()) && "ORDER_CONFIRMED".equals(row.getEventType())));
    }
}
