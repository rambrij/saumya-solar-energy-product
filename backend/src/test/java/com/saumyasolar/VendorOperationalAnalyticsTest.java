package com.saumyasolar;

import com.saumyasolar.repo.BranchRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.VendorAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorOperationalAnalyticsTest {
    @Test
    void returnsAllTenantOperationalReportFamiliesWithBoundedTenantAndEmployeeFilters() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
        BranchRepository branches=mock(BranchRepository.class);
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of());
        UUID tenant=UUID.randomUUID(),employee=UUID.randomUUID();
        Principal actor=new Principal(UUID.randomUUID(),tenant,"admin@example.test","VENDOR",
                Set.of("VENDOR_ADMIN"),Set.of("REPORT_VIEW","BRANCH_MANAGE"),0);
        var result=new VendorAnalyticsService(jdbc,branches).operations(actor,null,null,null,employee);
        assertEquals("TENANT_SCOPED",result.get("scope"));
        assertEquals(employee,result.get("employeeId"));
        assertTrue(result.containsKey("installationsByStatus"));
        assertTrue(result.containsKey("serviceTicketsByStatus"));
        assertTrue(result.containsKey("lowStockProducts"));
        assertTrue(result.containsKey("outstandingInvoices"));
        assertTrue(result.containsKey("employeePerformance"));
        verify(jdbc,times(5)).queryForList(anyString(),any(SqlParameterSource.class));
    }

    @Test
    void rejectsForeignBranchBeforeOperationalQueries() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
        BranchRepository branches=mock(BranchRepository.class);
        UUID tenant=UUID.randomUUID(),foreignBranch=UUID.randomUUID();
        when(branches.findByIdAndTenantId(foreignBranch,tenant)).thenReturn(java.util.Optional.empty());
        Principal actor=new Principal(UUID.randomUUID(),tenant,"admin@example.test","VENDOR",
                Set.of("VENDOR_ADMIN"),Set.of("REPORT_VIEW","BRANCH_MANAGE"),0);
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->new VendorAnalyticsService(jdbc,branches).operations(actor,null,null,foreignBranch,null));
        assertEquals(404,error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }
}
