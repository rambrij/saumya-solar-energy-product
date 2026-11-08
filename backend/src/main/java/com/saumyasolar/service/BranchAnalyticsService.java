package com.saumyasolar.service;

import com.saumyasolar.domain.Branch;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class BranchAnalyticsService {
 private final BranchRepository branches;
 private final BranchMembershipRepository memberships;
 private final SalesLeadRepository leads;
 private final SiteSurveyRepository surveys;
 private final QuotationRepository quotations;
 private final CustomerOrderRepository orders;
 private final InvoiceRepository invoices;
 private final ServiceRequestRepository tickets;
 private final InstallationJobRepository installations;

 public BranchAnalyticsService(BranchRepository branches,BranchMembershipRepository memberships,
  SalesLeadRepository leads,SiteSurveyRepository surveys,QuotationRepository quotations,
  CustomerOrderRepository orders,InvoiceRepository invoices,ServiceRequestRepository tickets,
  InstallationJobRepository installations){
  this.branches=branches;this.memberships=memberships;this.leads=leads;this.surveys=surveys;
  this.quotations=quotations;this.orders=orders;this.invoices=invoices;this.tickets=tickets;this.installations=installations;
 }

 public Map<String,Object> get(UUID branchId,Principal actor){
  if(actor==null||!"VENDOR".equals(actor.accountType())||actor.tenantId()==null
      ||actor.permissions()==null||!actor.permissions().contains("BRANCH_MANAGE"))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  UUID tenantId=actor.tenantId();
  Branch branch=branches.findByIdAndTenantId(branchId,tenantId)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found"));
  if(actor.roles()==null||!actor.roles().contains("VENDOR_ADMIN")){
   if(!memberships.existsByTenantIdAndBranchIdAndUserId(tenantId,branchId,actor.userId()))
    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found");
  }
  return Map.ofEntries(
   Map.entry("branchId",branch.getId()),Map.entry("branchCode",branch.getCode()),Map.entry("branchName",branch.getName()),
   Map.entry("active",branch.isActive()),
   Map.entry("leads",leads.countByTenantIdAndBranchId(tenantId,branchId)),
   Map.entry("surveys",surveys.countByTenantIdAndBranchId(tenantId,branchId)),
   Map.entry("quotations",quotations.countByTenantIdAndBranchId(tenantId,branchId)),
   Map.entry("sentQuotations",quotations.countByTenantIdAndBranchIdAndStatus(tenantId,branchId,"SENT")),
   Map.entry("orders",orders.countByTenantIdAndBranchId(tenantId,branchId)),
   Map.entry("orderValue",safe(orders.sumTotalByTenantAndBranch(tenantId,branchId))),
   Map.entry("invoices",invoices.countByTenantIdAndBranchId(tenantId,branchId)),
   Map.entry("invoiceValue",safe(invoices.sumGrandTotalByTenantAndBranch(tenantId,branchId))),
   Map.entry("serviceTickets",tickets.countByTenantIdAndBranchId(tenantId,branchId)),
   Map.entry("installations",installations.countByTenantIdAndBranchId(tenantId,branchId))
  );
 }
 private BigDecimal safe(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
}
