package com.saumyasolar.service;

import com.saumyasolar.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PlatformBusinessAnalyticsService {
    private final NamedParameterJdbcTemplate jdbc;
    public PlatformBusinessAnalyticsService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> overview(Principal actor, Instant from, Instant to) {
        if (actor == null || !"PLATFORM".equals(actor.accountType())
                || actor.roles() == null || !actor.roles().contains("PLATFORM_SUPER_ADMIN")
                || actor.permissions() == null || !actor.permissions().contains("SUBSCRIPTION_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Super Admin analytics access required");
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (end.isBefore(start) || Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analytics date range must be valid and no longer than 366 days");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(start)).addValue("to", Timestamp.from(end));
        Map<String,Object> tenants = jdbc.queryForMap("""
                SELECT COUNT(*) totalTenants,
                       COALESCE(SUM(status='ACTIVE'),0) activeTenants,
                       COALESCE(SUM(status='PENDING'),0) pendingTenants,
                       COALESCE(SUM(status='VERIFIED'),0) verifiedTenants,
                       COALESCE(SUM(status='SUSPENDED'),0) suspendedTenants,
                       COALESCE(SUM(status='CLOSED'),0) closedTenants
                FROM tenant
                """, new MapSqlParameterSource());
        Map<String,Object> orders = jdbc.queryForMap("""
                SELECT COUNT(*) orderCount,
                       COUNT(DISTINCT tenant_id) orderingTenants,
                       COALESCE(SUM(total),0) orderValue
                FROM customer_order
                WHERE created_at BETWEEN :from AND :to
                """, params);
        Map<String,Object> subscriptions = jdbc.queryForMap("""
                SELECT COALESCE(SUM(status='ACTIVE'),0) activeSubscriptions,
                       COALESCE(SUM(status='TRIAL'),0) trialSubscriptions,
                       COALESCE(SUM(status='PAST_DUE'),0) pastDueSubscriptions
                FROM tenant_subscription
                """, new MapSqlParameterSource());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("from", start);
        result.put("to", end);
        result.put("tenants", tenants);
        result.put("orders", orders);
        result.put("subscriptions", subscriptions);
        result.put("scope", "PLATFORM_AGGREGATE_ONLY");
        return result;
    }
}
