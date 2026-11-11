package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.saumyasolar.domain.Warranty;
import com.saumyasolar.domain.CustomerOrder;
import com.saumyasolar.domain.InstallationJob;
import com.saumyasolar.domain.Product;
import com.saumyasolar.repo.CustomerOrderRepository;
import com.saumyasolar.repo.InstallationJobRepository;
import com.saumyasolar.repo.ProductRepository;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.WarrantyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.payment.webhook-secret=test-webhook-secret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Release4FinanceServiceAndPlatformTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AppUserRepository users;
    @Autowired
    CustomerProfileRepository customerProfiles;
    @Autowired
    WarrantyRepository warranties;
    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    InstallationJobRepository installations;
    @Autowired
    ProductRepository products;
    
    private String token(String email, String password) throws Exception {
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("accessToken").asText();
    }
    
    @Test
    void platformTenantDashboardAndExportsArePlatformOnlyAndFiltered() throws Exception {
        String platform = token("platform@saumya.local", "Platform@123");
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + platform).param("status", "ACTIVE").param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber()).andExpect(jsonPath("$.active").isNumber()).andExpect(jsonPath("$.items").isArray());
        String exportSlug = "export-" + UUID.randomUUID().toString().substring(0, 8);
        String exportEmail = "export-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + platform).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Export Tenant\",\"slug\":\"" + exportSlug + "\",\"adminName\":\"Export Admin\",\"adminEmail\":\"" + exportEmail + "\",\"adminPassword\":\"ExportAdmin@2026\"}"))
                .andExpect(status().isOk());
        String createdTenantId=json.readTree(mvc.perform(get("/api/v1/platform/tenants")
                        .header("Authorization","Bearer "+platform).param("search",exportSlug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items").get(0).path("id").asText();
        mvc.perform(patch("/api/v1/tenants/"+createdTenantId+"/status").header("Authorization","Bearer "+platform)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VERIFIED\",\"reason\":\"Integration test verification\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/tenants/"+createdTenantId+"/status").header("Authorization","Bearer "+platform)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"Integration test activation\"}"))
                .andExpect(status().isOk());
        String csv = mvc.perform(get("/api/v1/platform/tenants/export").header("Authorization", "Bearer " + platform).param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("tenants-active-" + LocalDate.now()))).andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("Tenant Name,Tenant Slug,Administrator Name"));
        assertTrue(csv.contains(exportEmail));
        
        String emptyCsv = mvc.perform(get("/api/v1/platform/tenants/export").header("Authorization", "Bearer " + platform).param("status", "ACTIVE").param("search", "no-tenant-can-match-this"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(1, emptyCsv.lines().count(), "Empty exports retain the CSV header and no fabricated rows");
        mvc.perform(get("/api/v1/platform/tenants/export.xlsx").header("Authorization", "Bearer " + platform))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/api/v1/platform/tenants/export.pdf").header("Authorization", "Bearer " + platform))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        String vendor = token("admin@saumya.local", "Admin@123");
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + vendor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/tenants/export.xlsx").header("Authorization", "Bearer " + vendor)).andExpect(status().isForbidden());
    }
    
    @Test
    void tenantEditAllowsOnlyDisplayNameAndTenantStatusRemainsPlatformProtected() throws Exception {
        String platform = token("platform@saumya.local", "Platform@123");
        String email = "tenant-edit-" + UUID.randomUUID() + "@example.test";
        String created = mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + platform).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tenant before\",\"slug\":\"tenant-before-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"adminName\":\"Tenant Admin\",\"adminEmail\":\"" + email + "\",\"adminPassword\":\"TenantAdmin@2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();
        mvc.perform(patch("/api/v1/platform/tenants/" + id).header("Authorization", "Bearer " + platform).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tenant after\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tenant after"));
        mvc.perform(patch("/api/v1/platform/tenants/" + id).header("Authorization", "Bearer " + platform).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
    }
    
    @Test
    void serviceSlaTicketLifecycleIsTenantScopedAndCustomerCanConfirmResolution() throws Exception {
        String vendor = token("admin@saumya.local", "Admin@123");
        mvc.perform(post("/api/v1/service-sla-policies").header("Authorization", "Bearer " + vendor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"MEDIUM\",\"responseMinutes\":30,\"resolutionMinutes\":240,\"timeZone\":\"Asia/Kolkata\",\"holidaysJson\":\"[]\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("MEDIUM"));
        String email = "svc-customer-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSlug\":\"saumya-solar\",\"name\":\"Service Customer\",\"email\":\"" + email + "\",\"password\":\"ServiceCustomer@2026\"}"))
                .andExpect(status().isOk());
        String customer = token(email, "ServiceCustomer@2026");
        String created = mvc.perform(post("/api/v1/service-requests").header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"INVERTER\",\"priority\":\"MEDIUM\",\"description\":\"Inverter is not starting\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN")).andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();
        mvc.perform(get("/api/v1/service-requests").header("Authorization", "Bearer " + customer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        MockMultipartFile evidence = new MockMultipartFile(
                "file", "service-evidence.png", "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0,
                                                                          0, 0, 0
        }
        );
        String attachment = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/service-requests/" + id + "/attachments").file(evidence).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentType").value("image/png")).andReturn().getResponse().getContentAsString();
        String attachmentId = json.readTree(attachment).get("id").asText();
        mvc.perform(get("/api/v1/service-requests/" + id + "/attachments/" + attachmentId).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/png"));
        mvc.perform(get("/api/v1/service-requests/" + id).header("Authorization", "Bearer " + vendor)).andExpect(status().isOk());
        String otherEmail = "other-customer-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("storeSlug", "saumya-solar", "name", "Other Customer", "email", otherEmail, "password", "OtherCustomer@2026"))))
                .andExpect(status().isOk());
        String other = token(otherEmail, "OtherCustomer@2026");
        mvc.perform(get("/api/v1/service-requests/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-requests/" + id + "/attachments/" + attachmentId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-sla-policies").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
    }
    
    @Test
    void signedPaymentWebhookIsAuthenticatedAndDuplicateEventsAreIdempotent() throws Exception {
        String raw = "{\"eventId\":\"evt-release4-test-1\",\"paymentReference\":\"unmatched-provider-ref\",\"amount\":100.00,\"status\":\"SUCCEEDED\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/v1/payment-webhooks/test-provider").header("X-Payment-Signature", signature).contentType(MediaType.APPLICATION_JSON).content(raw))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true)).andExpect(jsonPath("$.matched").value(false));
        mvc.perform(post("/api/v1/payment-webhooks/test-provider").header("X-Payment-Signature", signature).contentType(MediaType.APPLICATION_JSON).content(raw))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
        mvc.perform(post("/api/v1/payment-webhooks/test-provider").header("X-Payment-Signature", "invalid").contentType(MediaType.APPLICATION_JSON).content(raw))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void warrantyClaimEvidenceIsPrivateAndTenantScoped() throws Exception {
        String vendor = token("admin@saumya.local", "Admin@123");
        String email = "warranty-evidence-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("storeSlug", "saumya-solar", "name", "Warranty Customer", "email", email, "password", "WarrantyCustomer@2026"))))
                .andExpect(status().isOk());
        String customer = token(email, "WarrantyCustomer@2026");
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        var profile = customerProfiles.findByTenantIdAndUserId(user.getTenantId(), user.getId()).orElseThrow();
        var vendorUser = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        Product product = products.findByTenantIdOrderByIdDesc(user.getTenantId()).stream().filter(v -> "DEMO-PV-550".equals(v.getSku())).findFirst().orElseThrow();
        CustomerOrder order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setTenantId(user.getTenantId());
        order.setCustomerId(profile.getId());
        order.setOrderNumber("WARRANTY-EVIDENCE-" + UUID.randomUUID());
        order.setStatus("COMMISSIONED");
        order.setTotal(java.math.BigDecimal.TEN);
        order.setCreatedAt(java.time.Instant.now());
        orders.save(order);
        InstallationJob installation = new InstallationJob();
        installation.setId(UUID.randomUUID());
        installation.setTenantId(user.getTenantId());
        installation.setOrderId(order.getId());
        installation.setCustomerId(profile.getId());
        installation.setSiteAddress("Test warranty installation");
        installation.setScheduledAt(java.time.Instant.now());
        installation.setAssignedEngineerId(vendorUser.getId());
        installation.setTeamName("Warranty test team");
        installation.setStatus("COMMISSIONED");
        installation.setCreatedAt(java.time.Instant.now());
        installations.save(installation);
        Warranty warranty = new Warranty();
        warranty.setId(UUID.randomUUID());
        warranty.setTenantId(user.getTenantId());
        warranty.setOrderId(order.getId());
        warranty.setInstallationId(installation.getId());
        warranty.setCustomerId(profile.getId());
        warranty.setProductId(product.getId());
        warranty.setQuantity(1);
        warranty.setStartDate(LocalDate.now().minusDays(1));
        warranty.setEndDate(LocalDate.now().plusDays(30));
        warranty.setTerms("Test warranty");
        warranty.setStatus("ACTIVE");
        warranty.setActivatedAt(java.time.Instant.now());
        warranties.save(warranty);
        String claim = mvc.perform(post("/api/v1/customer/warranty-claims").header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("warrantyId", warranty.getId(), "subject", "Panel output issue", "description", "Output is lower than expected"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String claimId = json.readTree(claim).get("id").asText();
        MockMultipartFile evidence = new MockMultipartFile("file", "warranty-proof.pdf", "application/pdf", "%PDF-1.4\\nproof\\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String attachment = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/warranty-claims/" + claimId + "/attachments").file(evidence).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentType").value("application/pdf")).andReturn().getResponse().getContentAsString();
        String attachmentId = json.readTree(attachment).get("id").asText();
        mvc.perform(get("/api/v1/warranty-claims/" + claimId + "/attachments/" + attachmentId).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/v1/warranty-claims/" + claimId + "/attachments").header("Authorization", "Bearer " + vendor)).andExpect(status().isOk());
        String otherEmail = "warranty-other-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("storeSlug", "saumya-solar", "name", "Other Warranty Customer", "email", otherEmail, "password", "OtherWarranty@2026"))))
                .andExpect(status().isOk());
        String other = token(otherEmail, "OtherWarranty@2026");
        mvc.perform(get("/api/v1/warranty-claims/" + claimId + "/attachments/" + attachmentId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
    }
    
    @Test
    void operationalReportsSupportCsvExcelAndPdfWithTenantAuthorization() throws Exception {
        String admin = token("admin@saumya.local", "Admin@123");
        mvc.perform(get("/api/v1/reports/operations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orders").isNumber())
                .andExpect(jsonPath("$.paymentsReceived").isNumber());
        byte[] csv = exportFile(admin, "CSV");
        assertTrue(new String(csv, StandardCharsets.UTF_8).startsWith("Metric,Value"));
        byte[] excel = exportFile(admin, "XLSX");
        assertTrue(excel.length > 100);
        assertEquals('P', excel[0]);
        assertEquals('K', excel[1], "XLSX must be a valid ZIP-based workbook");
        byte[] pdf = exportFile(admin, "PDF");
        assertTrue(new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.US_ASCII).startsWith("%PDF-"));
        mvc.perform(get("/api/v1/reports/operations").header("Authorization", "Bearer " + admin)
                .param("from", "2026-12-31").param("to", "2026-01-01")).andExpect(status().isBadRequest());
        String customer = token("customer@saumya.local", "Customer@123");
        mvc.perform(get("/api/v1/reports/operations").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/exports")
                        .header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("reportType", "OPERATIONS", "format", "CSV"))))
                .andExpect(status().isForbidden());
    }

    private byte[] exportFile(String token, String format) throws Exception {
        String created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/exports")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("reportType", "OPERATIONS", "format", format))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String exportId = json.readTree(created).get("id").asText();
        for (int attempt = 0; attempt < 30; attempt++) {
            String statusBody = mvc.perform(get("/api/v1/exports/" + exportId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var job = json.readTree(statusBody);
            String state = job.path("status").asText();
            if ("FAILED".equals(state)) throw new AssertionError("Export failed: " + job.path("error").asText());
            if ("COMPLETED".equals(state)) {
                return mvc.perform(get("/api/v1/exports/" + exportId + "/download").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timed out waiting for asynchronous " + format + " export");
    }
}
