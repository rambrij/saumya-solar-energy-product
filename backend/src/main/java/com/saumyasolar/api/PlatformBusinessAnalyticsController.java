package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PlatformBusinessAnalyticsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/analytics")
public class PlatformBusinessAnalyticsController {
    private final PlatformBusinessAnalyticsService analytics;
    public PlatformBusinessAnalyticsController(PlatformBusinessAnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/overview")
    public Map<String,Object> overview(@RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to,
                                       Authentication authentication) {
        return analytics.overview((Principal) authentication.getPrincipal(), from, to);
    }
}
