package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.BranchAnalyticsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchAnalyticsController {
    private final BranchAnalyticsService analytics;
    
    public BranchAnalyticsController(BranchAnalyticsService analytics) {
        this.analytics = analytics;
    }
    
    @GetMapping("/{branchId}/analytics")
    public Map<String, Object> analytics(@PathVariable UUID branchId, Authentication authentication) {
        return analytics.get(branchId, (Principal) authentication.getPrincipal());
    }
}
