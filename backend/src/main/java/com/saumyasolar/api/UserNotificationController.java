package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.UserNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class UserNotificationController {
    private final UserNotificationService notifications;
    public UserNotificationController(UserNotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public Page<UserNotificationService.NotificationView> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly, Authentication authentication) {
        return notifications.list(actor(authentication), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notifications.unreadCount(actor(authentication)));
    }

    @PatchMapping("/{id}/read")
    public UserNotificationService.NotificationView markRead(@PathVariable UUID id, Authentication authentication) {
        var row = notifications.markRead(id, actor(authentication));
        return new UserNotificationService.NotificationView(row.getId(), row.getEventType(), row.getTitle(), row.getBody(), row.getCreatedAt(), row.getReadAt());
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication authentication) {
        return Map.of("updated", notifications.markAllRead(actor(authentication)));
    }

    private Principal actor(Authentication authentication) {
        return (Principal) authentication.getPrincipal();
    }
}
