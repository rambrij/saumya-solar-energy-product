package com.saumyasolar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Enquiry;
import com.saumyasolar.domain.Product;
import com.saumyasolar.domain.ProductCategory;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.EnquiryRepository;
import com.saumyasolar.repo.ProductCategoryRepository;
import com.saumyasolar.repo.ProductRepository;
import com.saumyasolar.repo.CustomerOrderRepository;
import com.saumyasolar.repo.OrderItemRepository;
import com.saumyasolar.repo.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Release12TenantIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AppUserRepository users;
    @Autowired
    TenantRepository tenants;
    @Autowired
    ProductCategoryRepository categories;
    @Autowired
    ProductRepository products;
    @Autowired
    EnquiryRepository enquiries;
    @Autowired
    CustomerProfileRepository customerProfiles;
    @Autowired
    CustomerOrderRepository customerOrders;
    @Autowired
    OrderItemRepository orderItems;
    
    UUID vendorTenantId;
    
    @BeforeEach
    void findSeedVendorTenant() {
        vendorTenantId = users.findByEmailIgnoreCase("admin@saumya.local")
                .orElseThrow().getTenantId();
    }
    
    @Test
    void platformCanProvisionTenantAndVendorAdminCanManageSalesEmployee() throws Exception {
        var platformToken = accessToken("platform@saumya.local", "Platform@123");
        var suffix = UUID.randomUUID().toString().replace("-", "");
        var adminEmail = "admin-" + suffix + "@example.test";
        var adminPassword = "VendorAdmin-Pass-2026";
        var tenantBody = """
                {"name":"New Vendor","slug":"vendor-%s","adminName":"New Vendor Admin","adminEmail":"%s","adminPassword":"%s"}
                """.formatted(suffix, adminEmail, adminPassword);
        var tenantResponse = mvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tenantId = json.readTree(tenantResponse).get("id").asText();
        
        var vendorToken = accessToken(adminEmail, adminPassword);
        var rolesResponse = mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(rolesResponse).toString().contains("MANAGER"));
        var employeeEmail = "sales-" + suffix + "@example.test";
        var employeeBody = """
                {"name":"New Sales Executive","email":"%s","password":"SalesExec-Pass-2026","roleName":"SALES_EXECUTIVE"}
                """.formatted(employeeEmail);
        var employeeResponse = mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var employeeId = json.readTree(employeeResponse).get("id").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + employeeId + "/enabled")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"SalesExec-Pass-2026"}
                                """.formatted(employeeEmail)))
                .andExpect(status().isUnauthorized());
        
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/tenants/" + tenantId + "/status")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(adminEmail, adminPassword)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        var loginBody = """
                {"email":"admin@saumya.local","password":"Admin@123"}
                """;
        var loginResponse = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var session = json.readTree(loginResponse);
        var accessToken = session.get("accessToken").asText();
        var csrfToken = session.get("csrfToken").asText();
        var refreshCookie = loginResponse.getResponse().getCookie("saumya_refresh");
        var csrfCookie = loginResponse.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(refreshCookie);
        assertNotNull(csrfCookie);
        assertTrue(loginResponse.getResponse().getHeaders("Set-Cookie").stream().anyMatch(v -> v.contains("HttpOnly")));
        assertTrue(loginResponse.getResponse().getHeaders("Set-Cookie").stream().anyMatch(v -> v.contains("SameSite")));

        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void customerRegistrationCreatesProfileAndCanOpenCustomerQuotationList() throws Exception {
        var email = "registered-" + UUID.randomUUID() + "@example.test";
        var body = """
                {"storeSlug":"saumya-solar","name":"Registered Customer","email":"%s","password":"StrongPassphrase-2026"}
                """.formatted(email);
        var response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var token = json.readTree(response).get("accessToken").asText();
        var customer = users.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(customerProfiles.findByTenantIdOrderByIdDesc(customer.getTenantId()).stream()
                .anyMatch(profile -> customer.getId().equals(profile.getUserId())));
        mvc.perform(get("/api/v1/quotations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/customer/enquiries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyType":"RESIDENTIAL","monthlyBill":4500,"requiredCapacityKw":3,"message":"Need rooftop solar quotation"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer-enquiries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
    
    @Test
    void salesRoleCanReadButCannotManageCatalogue() throws Exception {
        var token = accessToken("sales@saumya.local", "Sales@123");
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized category\"}"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void productCannotReferenceAnotherTenantsCategory() throws Exception {
        var foreignTenant = newTenant();
        var category = new ProductCategory();
        category.setId(UUID.randomUUID());
        category.setTenantId(foreignTenant.getId());
        category.setName("Foreign category " + UUID.randomUUID());
        category.setActive(true);
        categories.save(category);
        
        var token = accessToken("admin@saumya.local", "Admin@123");
        var request = """
                {"name":"Invalid cross-tenant product","sku":"CROSS-%s","categoryId":"%s","price":500,"taxPercent":0}
                """.formatted(UUID.randomUUID(), category.getId());
        
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void productListDoesNotExposeAnotherTenantsProducts() throws Exception {
        var foreignTenant = newTenant();
        var foreignCategory = new ProductCategory();
        foreignCategory.setId(UUID.randomUUID());
        foreignCategory.setTenantId(foreignTenant.getId());
        foreignCategory.setName("Foreign category " + UUID.randomUUID());
        foreignCategory.setActive(true);
        categories.save(foreignCategory);
        var foreignProduct = new Product();
        foreignProduct.setId(UUID.randomUUID());
        foreignProduct.setTenantId(foreignTenant.getId());
        foreignProduct.setCategoryId(foreignCategory.getId());
        foreignProduct.setSku("FOREIGN-" + UUID.randomUUID());
        foreignProduct.setName("Foreign product");
        foreignProduct.setPrice(BigDecimal.TEN);
        foreignProduct.setTaxPercent(BigDecimal.ZERO);
        foreignProduct.setActive(true);
        products.save(foreignProduct);
        
        var token = accessToken("admin@saumya.local", "Admin@123");
        var response = mvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response);
        for (JsonNode row : result) {
            assertEquals(vendorTenantId.toString(), row.get("tenantId").asText());
            assertNotEquals(foreignProduct.getId().toString(), row.get("id").asText());
        }
    }
    
    @Test
    void leadCannotBeAssignedToEmployeeFromAnotherTenant() throws Exception {
        var foreignTenant = newTenant();
        var foreignEmployee = new AppUser();
        foreignEmployee.setId(UUID.randomUUID());
        foreignEmployee.setTenantId(foreignTenant.getId());
        foreignEmployee.setName("Foreign employee");
        foreignEmployee.setEmail("foreign-" + UUID.randomUUID() + "@example.test");
        foreignEmployee.setPasswordHash("not-used");
        foreignEmployee.setAccountType("VENDOR");
        foreignEmployee.setEnabled(true);
        users.save(foreignEmployee);
        
        var enquiry = new Enquiry();
        enquiry.setId(UUID.randomUUID());
        enquiry.setTenantId(vendorTenantId);
        enquiry.setName("Tenant isolation enquiry");
        enquiry.setStatus("NEW");
        enquiry.setCreatedAt(Instant.now());
        enquiries.save(enquiry);
        
        var token = accessToken("admin@saumya.local", "Admin@123");
        var request = """
                {"assignedUserId":"%s","priority":"MEDIUM"}
                """.formatted(foreignEmployee.getId());
        
        mvc.perform(post("/api/v1/enquiries/" + enquiry.getId() + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void vendorAdminCanCreateCategorizedProductAndConfigurablePackage() throws Exception {
        var token = accessToken("admin@saumya.local", "Admin@123");
        var categoriesJson = mvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var categoryId = json.readTree(categoriesJson).get(0).get("id").asText();
        var sku = "PANEL-" + UUID.randomUUID();
        var productBody = """
                {"sku":"%s","name":"Test solar panel","categoryId":"%s","price":12500,"taxPercent":5}
                """.formatted(sku, categoryId);
        var productResponse = mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(productBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var productId = json.readTree(productResponse).get("id").asText();
        
        mvc.perform(post("/api/v1/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"EMPTY-%s","name":"Invalid package","capacityKw":3,"packageType":"RESIDENTIAL","basePrice":25000,"items":[]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        
        mvc.perform(post("/api/v1/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PKG-%s","name":"Three kW package","capacityKw":3,"packageType":"RESIDENTIAL","basePrice":25000,"items":[{"productId":"%s","quantity":4,"optionalItem":false}]}
                                """.formatted(UUID.randomUUID(), productId)))
                .andExpect(status().isOk());
    }
    
    @Test
    void vendorAdminCanConfigureDiscountApprovalThreshold() throws Exception {
        var token = accessToken("admin@saumya.local", "Admin@123");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/tenant/settings/discount-threshold")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountPercent\":7.5}"))
                .andExpect(status().isOk());
        var response = mvc.perform(get("/api/v1/tenant/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(7.5, json.readTree(response).get("discountApprovalThreshold").asDouble());
    }
    
    @Test
    void customerCanRequestSurveyReceiveQuotationAcceptAndConvertExactlyOneOrder() throws Exception {
        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
        var customerEmail = "journey-" + UUID.randomUUID() + "@example.test";
        var registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeSlug":"saumya-solar","name":"Journey Customer","email":"%s","password":"StrongJourneyPassword-2026"}
                                """.formatted(customerEmail)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var customerToken = json.readTree(registration).get("accessToken").asText();
        var customer = users.findByEmailIgnoreCase(customerEmail).orElseThrow();
        var profile = customerProfiles.findByTenantIdAndUserId(customer.getTenantId(), customer.getId()).orElseThrow();
        
        var enquiryResponse = mvc.perform(post("/api/v1/customer/enquiries")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyType":"RESIDENTIAL","monthlyBill":5000,"requiredCapacityKw":3,"message":"Please quote a rooftop solar system"}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var enquiryId = json.readTree(enquiryResponse).get("id").asText();
        var vendor = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        
        var leadResponse = mvc.perform(post("/api/v1/enquiries/" + enquiryId + "/convert")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","priority":"HIGH"}
                                """.formatted(vendor.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var leadId = json.readTree(leadResponse).get("id").asText();
        var leadHistory = mvc.perform(get("/api/v1/leads/" + leadId + "/history")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(leadHistory.contains("ASSIGNMENT"));
        
        var surveyResponse = mvc.perform(post("/api/v1/surveys")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":"%s","assignedUserId":"%s","scheduledAt":"%s","propertyType":"RESIDENTIAL","roofType":"RCC","usableAreaSqft":800,"status":"SCHEDULED"}
                                """.formatted(leadId, vendor.getId(), Instant.now().plusSeconds(86400))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var surveyId = json.readTree(surveyResponse).get("id").asText();
        
        mvc.perform(patch("/api/v1/surveys/" + surveyId).header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sanctionedLoadKw\":5,\"findings\":\"Roof structure and shading assessed\",\"recommendation\":\"Proceed with 3 kW rooftop installation\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/surveys/" + surveyId + "/start").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/surveys/" + surveyId + "/submit").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(post("/api/v1/surveys/" + surveyId + "/approve").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        
        var categoriesJson = mvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var categoryId = json.readTree(categoriesJson).get(0).get("id").asText();
        var productResponse = mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"JOURNEY-%s","name":"Journey solar panel","categoryId":"%s","price":0.05,"taxPercent":18}
                                """.formatted(UUID.randomUUID(), categoryId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var productId = json.readTree(productResponse).get("id").asText();
        
        var quoteResponse = mvc.perform(post("/api/v1/quotations")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","leadId":"%s","surveyId":"%s","discountPercent":10,"validUntil":"%s","items":[{"productId":"%s","quantity":1},{"productId":"%s","quantity":1}]}
                                """.formatted(
                                profile.getId(), leadId, surveyId,
                                java.time.LocalDate.now().plusDays(30), productId, productId
                        )))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var quotationId = json.readTree(quoteResponse).get("quotation").get("id").asText();
        
        mvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/quotations/" + quotationId + "/accept")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        
        var key = "journey-" + UUID.randomUUID();
        var firstOrder = mvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-order")
                        .header("Authorization", "Bearer " + vendorToken)
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var secondOrder = mvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-order")
                        .header("Authorization", "Bearer " + vendorToken)
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(firstOrder).get("id").asText(), json.readTree(secondOrder).get("id").asText());
        
        var orderId = json.readTree(firstOrder).get("id").asText();
        var persistedOrder = customerOrders.findByIdAndTenantId(UUID.fromString(orderId), vendorTenantId).orElseThrow();
        assertEquals(0, new BigDecimal("0.10").compareTo(persistedOrder.getSubtotal()));
        assertEquals(0, new BigDecimal("10.00").compareTo(persistedOrder.getDiscountPercent()));
        assertEquals(0, new BigDecimal("0.02").compareTo(persistedOrder.getDiscountAmount()));
        assertEquals(0, new BigDecimal("0.02").compareTo(persistedOrder.getTaxTotal()));
        assertEquals(0, new BigDecimal("0.10").compareTo(persistedOrder.getTotal()));
        
        var invoiceResponse = mvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","taxTreatment":"IGST"}
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.subtotal").value(0.08))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.taxTotal").value(0.02))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.grandTotal").value(0.10))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.igstAmount").value(0.02))
                .andReturn().getResponse().getContentAsString();
        var invoiceId = json.readTree(invoiceResponse).get("id").asText();
        var invoiceLines = mvc.perform(get("/api/v1/invoices/" + invoiceId + "/items")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].taxableAmount").value(0.04))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].taxAmount").value(0.01))
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, json.readTree(invoiceLines).size());
    }
    
    @Test
    void platformCanManagePlansAndAssignReplacementWithHistory() throws Exception {
        String platformToken = accessToken("platform@saumya.local", "Platform@123");
        String vendorToken = accessToken("admin@saumya.local", "Admin@123");
        var plansJson = mvc.perform(get("/api/v1/platform/subscription-plans").header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var plansNode = json.readTree(plansJson);
        assertTrue(plansNode.size() >= 3);
        String starterId = null;
        for (JsonNode item : plansNode)
            if ("STARTER".equals(item.path("planCode").asText())) starterId = item.path("id").asText();
        assertNotNull(starterId);
        mvc.perform(get("/api/v1/platform/subscription-plans").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());
        
        String first = mvc.perform(post("/api/v1/platform/tenants/" + vendorTenantId + "/subscriptions")
                        .header("Authorization", "Bearer " + platformToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":"%s","status":"ACTIVE","billingCycle":"MONTHLY","autoRenew":true,"notes":"Integration test initial assignment"}
                                """.formatted(starterId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstId = json.readTree(first).path("id").asText();
        String second = mvc.perform(post("/api/v1/platform/tenants/" + vendorTenantId + "/subscriptions")
                        .header("Authorization", "Bearer " + platformToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":"%s","status":"TRIAL","billingCycle":"MONTHLY","trialEndsAt":"2027-01-15T00:00:00Z","autoRenew":false,"notes":"Integration test replacement"}
                                """.formatted(starterId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotEquals(firstId, json.readTree(second).path("id").asText());
        var history = mvc.perform(get("/api/v1/platform/tenants/" + vendorTenantId + "/subscription-history")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(history.contains("ASSIGNED"));
        assertTrue(history.contains("REPLACED"));
        mvc.perform(get("/api/v1/subscription").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.planCode").value("STARTER"));
    }
    
    @Test
    void platformPlanCreateRejectsDuplicatePlanCode() throws Exception {
        String platformToken = accessToken("platform@saumya.local", "Platform@123");
        String payload = """
                {"planCode":"STARTER","name":"Duplicate Starter","billingCycle":"MONTHLY","price":1200,"currency":"INR",
                 "maxUsers":5,"maxBranches":1,"maxWarehouses":1,"maxStorageMb":512,"maxProducts":100,"maxCustomers":100,
                 "trialPeriodDays":14,"displayOrder":1,"entitlementsJson":"{\\"branches\\":false}"}
                """;
        mvc.perform(post("/api/v1/platform/subscription-plans").header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
    }
    

    @Test
    void vendorAdminCannotEscalateEmployeeToPlatformSuperAdmin() throws Exception {
        String token = accessToken("admin@saumya.local", "Admin@123");
        String email = "escalation-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Escalation Attempt","email":"%s","password":"StrongEscalationPassword-2026","roleName":"PLATFORM_SUPER_ADMIN"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest());
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void missingAndMalformedBearerTokensAreUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private String accessToken(String email, String password) throws Exception {
        var body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        var response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }
    
    private Tenant newTenant() {
        var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Integration tenant");
        tenant.setSlug("test-" + UUID.randomUUID().toString().replace("-", ""));
        tenant.setStatus("ACTIVE");
        return tenants.save(tenant);
    }
    
    @Test
    void customerCanPlaceProductOrderAndVendorMustAcceptIt() throws Exception {
        var email = "direct-order-" + UUID.randomUUID() + "@example.test";
        var registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeSlug":"saumya-solar","name":"Direct Order Customer","email":"%s","password":"StrongPassphrase-2026"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var customerToken = json.readTree(registration).get("accessToken").asText();
        var catalogue = mvc.perform(get("/api/v1/catalog-products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var productsJson = json.readTree(catalogue);
        assertTrue(productsJson.size() > 0, "Demo catalogue should contain at least one active product");
        var product = productsJson.get(0);
        var productId = product.get("id").asText();
        var orderResponse = mvc.perform(post("/api/v1/customer/product-orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(productId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var orderJson = json.readTree(orderResponse);
        assertEquals("PENDING_VENDOR_ACCEPTANCE", orderJson.get("status").asText());
        var orderId = UUID.fromString(orderJson.get("id").asText());
        var persistedOrder = customerOrders.findByIdAndTenantId(orderId, vendorTenantId).orElseThrow();
        var line = orderItems.findByTenantIdOrderByIdDesc(vendorTenantId).stream()
                .filter(item -> orderId.equals(item.getOrderId())).findFirst().orElseThrow();
        assertEquals(2, line.getQuantity());
        assertNotNull(line.getTaxPercent());
        assertTrue(persistedOrder.getTotal().compareTo(BigDecimal.ZERO) > 0);
        
        var vendorToken = accessToken("sales@saumya.local", "Sales@123");
        mvc.perform(post("/api/v1/orders/" + orderId + "/vendor-decision")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACCEPT","reason":"Stock and delivery slot confirmed"}
                                """))
                .andExpect(status().isOk());
        assertEquals("CONFIRMED", customerOrders.findByIdAndTenantId(orderId, vendorTenantId).orElseThrow().getStatus());
        var customerOrdersJson = mvc.perform(get("/api/v1/customer/product-orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(customerOrdersJson).toString().contains(orderId.toString()));
    }
}
