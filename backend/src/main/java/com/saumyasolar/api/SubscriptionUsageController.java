package com.saumyasolar.api;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.SubscriptionUsageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/subscription")
public class SubscriptionUsageController {
 private final SubscriptionUsageService usage;
 private final com.saumyasolar.service.TenantStorageMeteringService storage;
 public SubscriptionUsageController(SubscriptionUsageService usage,com.saumyasolar.service.TenantStorageMeteringService storage){this.usage=usage;this.storage=storage;}
 @GetMapping("/usage") public Map<String,Object> usage(Authentication authentication){return usage.current((Principal)authentication.getPrincipal());}
 @GetMapping("/storage") public Map<String,Object> storage(Authentication authentication){Principal p=(Principal)authentication.getPrincipal();if(!"VENDOR".equals(p.accountType())||p.tenantId()==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);return storage.summary(p.tenantId());}
}
