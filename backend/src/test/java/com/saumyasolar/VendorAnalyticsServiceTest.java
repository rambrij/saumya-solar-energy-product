package com.saumyasolar;

import com.saumyasolar.repo.BranchRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.VendorAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorAnalyticsServiceTest {
    @Test
    void blocksVendorEmployeesWithoutVendorAdminRole() {
        VendorAnalyticsService service = new VendorAnalyticsService(mock(NamedParameterJdbcTemplate.class), mock(BranchRepository.class));
        Principal employee = new Principal(UUID.randomUUID(), UUID.randomUUID(), "sales@example.test", "VENDOR", Set.of("SALES_MANAGER"), Set.of("REPORT_VIEW", "BRANCH_MANAGE"), 0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.breakdowns(employee, null, null, null, null, null));
        assertEquals(403, error.getStatusCode().value());
    }
    
    @Test
    void blocksVendorAdminWithoutReportAndBranchPermissions() {
        VendorAnalyticsService service = new VendorAnalyticsService(mock(NamedParameterJdbcTemplate.class), mock(BranchRepository.class));
        Principal admin = new Principal(UUID.randomUUID(), UUID.randomUUID(), "admin@example.test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("REPORT_VIEW"), 0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.breakdowns(admin, null, null, null, null, null));
        assertEquals(403, error.getStatusCode().value());
    }
    
    @Test
    void validatesRankingLimitBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        VendorAnalyticsService service = new VendorAnalyticsService(jdbc, mock(BranchRepository.class));
        Principal admin = new Principal(UUID.randomUUID(), UUID.randomUUID(), "admin@example.test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("REPORT_VIEW", "BRANCH_MANAGE"), 0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.rankings(admin, null, null, null, null, null, "revenue", 500));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void everyAnalyticsOperationRequiresVendorAdminReportViewAndBranchManage() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        BranchRepository branches = mock(BranchRepository.class);
        VendorAnalyticsService service = new VendorAnalyticsService(jdbc, branches);

        Principal missingRole = new Principal(UUID.randomUUID(), UUID.randomUUID(), "employee@example.test",
                "VENDOR", Set.of("SALES_MANAGER"), Set.of("REPORT_VIEW", "BRANCH_MANAGE"), 0);
        Principal missingReport = new Principal(UUID.randomUUID(), UUID.randomUUID(), "admin@example.test",
                "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("BRANCH_MANAGE"), 0);
        Principal missingBranchManage = new Principal(UUID.randomUUID(), UUID.randomUUID(), "admin2@example.test",
                "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("REPORT_VIEW"), 0);

        for (Principal actor : List.of(missingRole, missingReport, missingBranchManage)) {
            assertEquals(403, assertThrows(ResponseStatusException.class,
                    () -> service.breakdowns(actor, null, null, null, null, null)).getStatusCode().value());
            assertEquals(403, assertThrows(ResponseStatusException.class,
                    () -> service.trends(actor, null, null, null, null, null, "DAY")).getStatusCode().value());
            assertEquals(403, assertThrows(ResponseStatusException.class,
                    () -> service.rankings(actor, null, null, null, null, null, "orders", 10)).getStatusCode().value());
        }
        verifyNoInteractions(jdbc, branches);
    }

    @Test
    void vendorAdminWithRequiredGrantsPassesAuthorizationForAllAnalyticsOperations() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        VendorAnalyticsService service = new VendorAnalyticsService(jdbc, branches);
        Principal admin = new Principal(UUID.randomUUID(), UUID.randomUUID(), "admin@example.test",
                "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("REPORT_VIEW", "BRANCH_MANAGE"), 0);

        assertDoesNotThrow(() -> service.breakdowns(admin, null, null, null, null, null));
        verify(jdbc, times(2)).queryForList(anyString(), any(SqlParameterSource.class));
        clearInvocations(jdbc);

        assertDoesNotThrow(() -> service.trends(admin, null, null, null, null, null, "DAY"));
        verify(jdbc, times(4)).queryForList(anyString(), any(SqlParameterSource.class));
        clearInvocations(jdbc);

        assertDoesNotThrow(() -> service.rankings(admin, null, null, null, null, null, "orders", 10));
        verify(jdbc, times(2)).queryForList(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void rejectsForeignBranchBeforeRunningAnalyticsSql() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        BranchRepository branches = mock(BranchRepository.class);
        UUID tenantId = UUID.randomUUID();
        UUID foreignBranchId = UUID.randomUUID();
        when(branches.findByIdAndTenantId(foreignBranchId, tenantId)).thenReturn(java.util.Optional.empty());
        VendorAnalyticsService service = new VendorAnalyticsService(jdbc, branches);
        Principal admin = new Principal(UUID.randomUUID(), tenantId, "admin@example.test",
                "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("REPORT_VIEW", "BRANCH_MANAGE"), 0);

        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> service.breakdowns(admin, null, null, foreignBranchId, null, null));
        assertEquals(404, denied.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }
}
