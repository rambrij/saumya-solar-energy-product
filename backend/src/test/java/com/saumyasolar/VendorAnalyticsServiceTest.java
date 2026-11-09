package com.saumyasolar;

import com.saumyasolar.repo.BranchRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.VendorAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorAnalyticsServiceTest {
 @Test void blocksVendorEmployeesWithoutVendorAdminRole(){
  VendorAnalyticsService service=new VendorAnalyticsService(mock(NamedParameterJdbcTemplate.class),mock(BranchRepository.class));
  Principal employee=new Principal(UUID.randomUUID(),UUID.randomUUID(),"sales@example.test","VENDOR",Set.of("SALES_MANAGER"),Set.of("REPORT_VIEW","BRANCH_MANAGE"),0);
  ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.breakdowns(employee,null,null,null,null,null));
  assertEquals(403,error.getStatusCode().value());
 }
 @Test void blocksVendorAdminWithoutReportAndBranchPermissions(){
  VendorAnalyticsService service=new VendorAnalyticsService(mock(NamedParameterJdbcTemplate.class),mock(BranchRepository.class));
  Principal admin=new Principal(UUID.randomUUID(),UUID.randomUUID(),"admin@example.test","VENDOR",Set.of("VENDOR_ADMIN"),Set.of("REPORT_VIEW"),0);
  ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.breakdowns(admin,null,null,null,null,null));
  assertEquals(403,error.getStatusCode().value());
 }
 @Test void validatesRankingLimitBeforeDatabaseAccess(){
  NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
  VendorAnalyticsService service=new VendorAnalyticsService(jdbc,mock(BranchRepository.class));
  Principal admin=new Principal(UUID.randomUUID(),UUID.randomUUID(),"admin@example.test","VENDOR",Set.of("VENDOR_ADMIN"),Set.of("REPORT_VIEW","BRANCH_MANAGE"),0);
  ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.rankings(admin,null,null,null,null,null,"revenue",500));
  assertEquals(400,error.getStatusCode().value());verifyNoInteractions(jdbc);
 }
}
