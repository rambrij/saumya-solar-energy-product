package com.saumyasolar.api;

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
 public VendorAnalyticsController(VendorAnalyticsService analytics){this.analytics=analytics;}
 private Principal actor(Authentication a){return (Principal)a.getPrincipal();}
 @GetMapping("/breakdowns") public Map<String,Object> breakdowns(
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
  @RequestParam(required=false) UUID branchId,@RequestParam(required=false) UUID categoryId,
  @RequestParam(required=false) UUID productId,Authentication a){
  return analytics.breakdowns(actor(a),from,to,branchId,categoryId,productId);
 }
 @GetMapping("/trends") public Map<String,Object> trends(
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
  @RequestParam(required=false) UUID branchId,@RequestParam(required=false) UUID categoryId,
  @RequestParam(required=false) UUID productId,@RequestParam(defaultValue="DAY") String granularity,Authentication a){
  return analytics.trends(actor(a),from,to,branchId,categoryId,productId,granularity);
 }
 @GetMapping("/rankings") public Map<String,Object> rankings(
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
  @RequestParam(required=false) UUID branchId,@RequestParam(required=false) UUID categoryId,
  @RequestParam(required=false) UUID productId,@RequestParam(defaultValue="orders") String metric,
  @RequestParam(defaultValue="10") int limit,Authentication a){
  return analytics.rankings(actor(a),from,to,branchId,categoryId,productId,metric,limit);
 }
}
