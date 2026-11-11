package com.saumyasolar.api;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.NotificationPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/v1/notifications/preferences")
public class NotificationPreferenceController {
  private final NotificationPreferenceService service;
  public NotificationPreferenceController(NotificationPreferenceService service){this.service=service;}
  @GetMapping public List<NotificationPreferenceService.View> list(Authentication auth){return service.list((Principal)auth.getPrincipal());}
  @PutMapping public NotificationPreferenceService.View update(@RequestBody Map<String,Object> body,Authentication auth){String channel=String.valueOf(body.getOrDefault("channel",""));boolean enabled=Boolean.TRUE.equals(body.get("enabled"));boolean consented=Boolean.TRUE.equals(body.get("consented"));return service.update((Principal)auth.getPrincipal(),channel,enabled,consented);}
}