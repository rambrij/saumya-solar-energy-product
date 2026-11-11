package com.saumyasolar.api;

import io.swagger.v3.oas.annotations.Operation;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.VendorAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/vendor/analytics")
public class VendorAnalyticsController {
    private final VendorAnalyticsService analytics;
    
    public VendorAnalyticsController(VendorAnalyticsService analytics) {
        this.analytics = analytics;
    }
    
    private Principal actor(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    @Operation(
            summary = "Vendor category and product analytics",
            description = "Returns tenant-scoped order, revenue, lead and enquiry aggregates. Requires VENDOR_ADMIN, REPORT_VIEW and BRANCH_MANAGE."
    )
    @GetMapping("/breakdowns")
    public Map<String, Object> breakdowns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId, Authentication a
    ) {
        return analytics.breakdowns(actor(a), from, to, branchId, categoryId, productId);
    }
    
    @Operation(
            summary = "Vendor sales and lead trends",
            description = "Returns day, week or month series for leads, enquiries, orders and revenue with optional date, branch, category and product filters."
    )
    @GetMapping("/trends")
    public Map<String, Object> trends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "DAY") String granularity,
            Authentication a
    ) {
        return analytics.trends(actor(a), from, to, branchId, categoryId, productId, granularity);
    }
    
    @Operation(
            summary = "Top vendor products and categories",
            description = "Returns tenant-scoped product and category rankings by orders, revenue or leads."
    )
    @Operation(summary = "Vendor operational analytics", description = "Returns tenant-scoped installation, service, low-stock, outstanding invoice and employee aggregates. Supports branch and employee filters.")
    @GetMapping("/operations")
    public Map<String,Object> operations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID employeeId,
            Authentication a
    ) {
        return analytics.operations(actor(a), from, to, branchId, employeeId);
    }

    @GetMapping("/rankings")
    public Map<String, Object> rankings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId, @RequestParam(defaultValue = "orders") String metric,
            @RequestParam(defaultValue = "10") int limit, Authentication a
    ) {
        return analytics.rankings(actor(a), from, to, branchId, categoryId, productId, metric, limit);
    }
}
