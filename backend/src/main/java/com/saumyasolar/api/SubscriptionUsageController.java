package com.saumyasolar.api;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.SubscriptionUsageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/subscription")
public class SubscriptionUsageController {
 private final SubscriptionUsageService usage;
 public SubscriptionUsageController(SubscriptionUsageService usage){this.usage=usage;}
 @GetMapping("/usage") public Map<String,Object> usage(Authentication authentication){return usage.current((Principal)authentication.getPrincipal());}
}
