package com.saumyasolar.api;

import com.saumyasolar.api.NotificationModels.NotificationView;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.NotificationInboxService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationInboxService inbox;
    public NotificationController(NotificationInboxService inbox) { this.inbox = inbox; }
    private Principal principal(Authentication auth) { return (Principal) auth.getPrincipal(); }

    @GetMapping
    List<NotificationView> list(Authentication auth) { return inbox.list(principal(auth)); }

    @PostMapping("/{id}/read")
    NotificationView markRead(@PathVariable UUID id, Authentication auth) {
        return inbox.markRead(id, principal(auth));
    }
}
