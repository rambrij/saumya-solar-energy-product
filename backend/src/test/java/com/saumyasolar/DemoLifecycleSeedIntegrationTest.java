package com.saumyasolar;

import com.saumyasolar.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DemoLifecycleSeedIntegrationTest {
 @Autowired TenantRepository tenants;
 @Autowired EnquiryRepository enquiries;
 @Autowired SalesLeadRepository leads;
 @Autowired SiteSurveyRepository surveys;
 @Autowired QuotationRepository quotations;
 @Autowired CustomerOrderRepository orders;
 @Autowired InvoiceRepository invoices;
 @Autowired PaymentRepository payments;
 @Autowired InstallationJobRepository installations;
 @Autowired ServiceRequestRepository serviceRequests;
 @Autowired TenantSubscriptionRepository tenantSubscriptions;
 @Autowired SubscriptionPlanRepository plans;

 @Test
 void optInDemoSeedCreatesOneConnectedSolarLifecycleAndTenantPlan() {
  var tenant=tenants.findBySlug("saumya-solar").orElseThrow();
  var enquiry=enquiries.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
    .filter(e->"DEMO-LIFECYCLE-2026".equals(e.getMessage())).findFirst().orElseThrow();
  assertEquals("CONVERTED",enquiry.getStatus());
  var lead=leads.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
    .filter(l->enquiry.getId().equals(l.getEnquiryId())).findFirst().orElseThrow();
  assertEquals("QUALIFIED",lead.getStage());
  var survey=surveys.findByLeadIdAndTenantIdOrderByIdDesc(lead.getId(),tenant.getId()).stream().findFirst().orElseThrow();
  assertEquals("APPROVED",survey.getStatus());
  var quotation=quotations.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
    .filter(q->"DEMO-QUO-2026-001".equals(q.getQuotationNumber())).findFirst().orElseThrow();
  assertEquals("ACCEPTED",quotation.getStatus());
  var order=orders.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
    .filter(o->quotation.getId().equals(o.getQuotationId())).findFirst().orElseThrow();
  assertEquals("CONFIRMED",order.getStatus());
  var invoice=invoices.findByOrderIdAndTenantId(order.getId(),tenant.getId()).orElseThrow();
  assertEquals("PAID",invoice.getStatus());
  assertTrue(payments.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
    .anyMatch(p->invoice.getId().equals(p.getInvoiceId())&&"DEMO-UPI-2026-001".equals(p.getReference())&&"CONFIRMED".equals(p.getStatus())));
  var installation=installations.findByTenantIdAndOrderId(tenant.getId(),order.getId()).orElseThrow();
  assertEquals("COMMISSIONED",installation.getStatus());
  var request=serviceRequests.findByTicketNumberAndTenantId("SR-DEMO-2026-001",tenant.getId()).orElseThrow();
  assertEquals(installation.getId(),request.getInstallationId());
  assertEquals("OPEN",request.getStatus());
  var assignment=tenantSubscriptions.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
    .filter(s->"ACTIVE".equals(s.getStatus())).findFirst().orElseThrow();
  assertEquals("STARTER",plans.findById(assignment.getPlanId()).orElseThrow().getPlanCode());
 }
}
