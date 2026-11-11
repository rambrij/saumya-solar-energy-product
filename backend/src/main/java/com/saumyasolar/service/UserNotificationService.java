package com.saumyasolar.service;

import com.saumyasolar.domain.UserNotification;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.UserNotificationRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserNotificationService {
    private final UserNotificationRepository notifications;
    private final AppUserRepository users;

    public UserNotificationService(UserNotificationRepository notifications, AppUserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void createBusinessNotification(UUID userId, UUID tenantId, String eventType, String title, String body) {
        if (userId == null) return;
        if (tenantId == null || users.findByIdAndTenantId(userId, tenantId)
                .filter(user -> user.isEnabled()).isEmpty()) return;
        String safeEvent = eventType == null ? "BUSINESS_EVENT" : eventType.trim();
        String safeTitle = title == null || title.isBlank() ? safeEvent : title.trim();
        String safeBody = body == null ? "" : body.trim();
        if (safeEvent.length() > 60 || safeTitle.length() > 255)
            throw new IllegalArgumentException("Notification title or event type exceeds its limit");
        if (safeBody.length() > 4000) safeBody = safeBody.substring(0, 4000);
        String dedupe = UUID.nameUUIDFromBytes((tenantId + ":" + userId + ":" + safeEvent + ":" + safeTitle + ":" + safeBody)
                .getBytes(StandardCharsets.UTF_8)).toString();
        if (notifications.existsByDedupeKey(dedupe)) return;
        UserNotification row = new UserNotification();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setEventType(safeEvent);
        row.setTitle(safeTitle);
        row.setBody(safeBody);
        row.setDedupeKey(dedupe);
        row.setCreatedAt(Instant.now());
        notifications.save(row);
    }

    public Page<NotificationView> list(Principal actor, int page, int size, boolean unreadOnly) {
        validate(actor, page, size);
        PageRequest request = PageRequest.of(page, size);
        Page<UserNotification> pageResult;
        if (actor.tenantId() == null)
            pageResult = unreadOnly
                    ? notifications.findByUserIdAndTenantIdIsNullAndReadAtIsNullOrderByCreatedAtDesc(actor.userId(), request)
                    : notifications.findByUserIdAndTenantIdIsNullOrderByCreatedAtDesc(actor.userId(), request);
        else
            pageResult = unreadOnly
                    ? notifications.findByUserIdAndTenantIdAndReadAtIsNullOrderByCreatedAtDesc(actor.userId(), actor.tenantId(), request)
                    : notifications.findByUserIdAndTenantIdOrderByCreatedAtDesc(actor.userId(), actor.tenantId(), request);
        return pageResult.map(this::view);
    }

    private NotificationView view(UserNotification row) {
        return new NotificationView(row.getId(), row.getEventType(), row.getTitle(), row.getBody(),
                row.getCreatedAt(), row.getReadAt());
    }

    public long unreadCount(Principal actor) {
        validate(actor, 0, 1);
        return actor.tenantId() == null
                ? notifications.countByUserIdAndTenantIdIsNullAndReadAtIsNull(actor.userId())
                : notifications.countByUserIdAndTenantIdAndReadAtIsNull(actor.userId(), actor.tenantId());
    }

    @Transactional
    public UserNotification markRead(UUID id, Principal actor) {
        validate(actor, 0, 1);
        UserNotification row = actor.tenantId() == null
                ? notifications.findByIdAndUserIdAndTenantIdIsNull(id, actor.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"))
                : notifications.findByIdAndUserIdAndTenantId(id, actor.userId(), actor.tenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (row.getReadAt() == null) {
            row.setReadAt(Instant.now());
            notifications.save(row);
        }
        return row;
    }

    @Transactional
    public int markAllRead(Principal actor) {
        validate(actor, 0, 1);
        Instant now = Instant.now();
        return actor.tenantId() == null
                ? notifications.markAllPlatformRead(actor.userId(), now)
                : notifications.markAllTenantRead(actor.userId(), actor.tenantId(), now);
    }

    public record NotificationView(UUID id, String eventType, String title, String body,
                                   Instant createdAt, Instant readAt) { }

    private void validate(Principal actor, int page, int size) {
        if (actor == null || actor.userId() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if ("VENDOR".equals(actor.accountType())) {
            if (actor.permissions() == null || !actor.permissions().contains("NOTIFICATION_VIEW"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: NOTIFICATION_VIEW");
        } else if (!"CUSTOMER".equals(actor.accountType()) && !"PLATFORM".equals(actor.accountType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
    }
}
