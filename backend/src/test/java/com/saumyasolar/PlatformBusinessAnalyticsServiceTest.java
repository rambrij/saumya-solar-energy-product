package com.saumyasolar;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PlatformBusinessAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBusinessAnalyticsServiceTest {
    @Test
    void requiresPlatformSuperAdminAndSubscriptionGrant() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        PlatformBusinessAnalyticsService service = new PlatformBusinessAnalyticsService(jdbc);
        Principal vendor = new Principal(UUID.randomUUID(), UUID.randomUUID(), "admin@example.test",
                "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("SUBSCRIPTION_MANAGE"), 0);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.overview(vendor, null, null)).getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsDateRangesLongerThan366DaysBeforeQuerying() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        PlatformBusinessAnalyticsService service = new PlatformBusinessAnalyticsService(jdbc);
        Principal platform = new Principal(UUID.randomUUID(), null, "platform@example.test",
                "PLATFORM", Set.of("PLATFORM_SUPER_ADMIN"), Set.of("SUBSCRIPTION_MANAGE"), 0);
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-02T00:00:00Z");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.overview(platform, from, to)).getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void returnsAggregateOnlyPlatformMetricsWithoutTenantRows() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(Map.of("totalTenants", 3L, "activeTenants", 2L));
        PlatformBusinessAnalyticsService service = new PlatformBusinessAnalyticsService(jdbc);
        Principal platform = new Principal(UUID.randomUUID(), null, "platform@example.test",
                "PLATFORM", Set.of("PLATFORM_SUPER_ADMIN"), Set.of("SUBSCRIPTION_MANAGE"), 0);
        Map<String,Object> result = service.overview(platform, null, null);
        assertEquals("PLATFORM_AGGREGATE_ONLY", result.get("scope"));
        assertTrue(result.containsKey("tenants"));
        assertTrue(result.containsKey("orders"));
        assertTrue(result.containsKey("subscriptions"));
        assertFalse(result.containsKey("tenantRows"));
        verify(jdbc,times(3)).queryForMap(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }
}
