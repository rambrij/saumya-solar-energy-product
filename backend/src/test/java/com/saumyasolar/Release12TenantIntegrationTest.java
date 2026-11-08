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
import com.saumyasolar.repo.AuditLogRepository;
import com.saumyasolar.repo.NotificationDeliveryRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Release12TenantIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired ProductCategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired EnquiryRepository enquiries;
    @Autowired CustomerProfileRepository customerProfiles;
    @Autowired CustomerOrderRepository customerOrders;
    @Autowired OrderItemRepository orderItems;
    @Autowired AuditLogRepository auditLogs;
    @Autowired NotificationDeliveryRepository notificationDeliveries;
    @Autowired com.saumyasolar.service.NotificationService notifications;

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
                {"name":"New Vendor","slug":"vendor-%s","adminName":"New Vendor Admin","adminEmail":"%s"}
                """.formatted(suffix, adminEmail);
        var tenantResponse = mvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tenantId = json.readTree(tenantResponse).get("id").asText();
        var invitationDelivery = notificationDeliveries.findTop100ByTenantIdOrderByCreatedAtDesc(UUID.fromString(tenantId)).stream()
                .filter(delivery -> "VENDOR_ADMIN_INVITATION".equals(delivery.getEventType()))
                .filter(delivery -> delivery.getBody() != null)
                .findFirst().orElseThrow();
        var invitationMatcher = java.util.regex.Pattern.compile("#vendorInviteToken=([A-Za-z0-9_-]+)")
                .matcher(invitationDelivery.getBody());
        assertTrue(invitationMatcher.find(), "Invitation email must contain a one-time setup token");
        var invitationToken = invitationMatcher.group(1);
        mvc.perform(post("/api/v1/auth/vendor-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(invitationToken, adminPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/vendor-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(invitationToken, adminPassword)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(adminEmail, adminPassword)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/verification")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.verificationStatus").value("VERIFIED"));

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
        var refreshToken = session.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
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
                                {"code":"EMPTY-%s","name":"Invalid package","capacityKw":3,"packageType":"RESIDENTIAL","basePrice":25000,"taxPercent":18,"items":[]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        var packageResponse=mvc.perform(post("/api/v1/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PKG-%s","name":"Three kW package","capacityKw":3,"packageType":"RESIDENTIAL","basePrice":25000,"taxPercent":18,"items":[{"productId":"%s","quantity":4,"optionalItem":false}]}
                                """.formatted(UUID.randomUUID(), productId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String packageId=json.readTree(packageResponse).get("id").asText();
        var customer=customerProfiles.findByTenantIdOrderByIdDesc(vendorTenantId).get(0);
        var quoteResponse=mvc.perform(post("/api/v1/quotations").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","discountPercent":10,"validUntil":"%s","items":[{"packageId":"%s","quantity":1}]}
                                """.formatted(customer.getId(),java.time.LocalDate.now().plusDays(30),packageId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(4050.0,json.readTree(quoteResponse).get("quotation").get("taxAmount").asDouble());
        assertEquals(26550.0,json.readTree(quoteResponse).get("quotation").get("grandTotal").asDouble());
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
                                """.formatted(profile.getId(), leadId, surveyId,
                                java.time.LocalDate.now().plusDays(30), productId, productId)))
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
    void rejectedVendorCannotLoginOrBypassVerificationWithStatusEndpoint() throws Exception {
        String platformToken=accessToken("platform@saumya.local","Platform@123");
        String suffix=UUID.randomUUID().toString().replace("-","");
        String email="rejected-"+suffix+"@example.test";
        String body="""
                {"name":"Rejected Vendor","slug":"rejected-%s","adminName":"Rejected Admin","adminEmail":"%s"}
                """.formatted(suffix,email);
        String tenantJson=mvc.perform(post("/api/v1/tenants").header("Authorization","Bearer "+platformToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(tenantJson).get("id").asText();
        mvc.perform(post("/api/v1/platform/tenants/"+id+"/verification")
                .header("Authorization","Bearer "+platformToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"reason\":\"GST certificate is missing\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.verificationStatus").value("REJECTED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/tenants/"+id+"/status")
                .header("Authorization","Bearer "+platformToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"NotAccepted-Password-2026\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vendorVerificationDocumentsArePrivateAndPlatformReviewable() throws Exception {
        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
        var platformToken = accessToken("platform@saumya.local", "Platform@123");
        var customerToken = accessToken("customer@saumya.local", "Customer@123");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "business-registration.pdf",
                "application/pdf", "%PDF-1.4\\nVendor registration evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var uploaded = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/vendor/verification-documents")
                        .file(file).param("documentType", "BUSINESS_REGISTRATION")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var documentId = json.readTree(uploaded).get("id").asText();

        mvc.perform(get("/api/v1/vendor/verification-documents").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].documentType").value("BUSINESS_REGISTRATION"));
        mvc.perform(get("/api/v1/platform/tenants/" + vendorTenantId + "/verification-documents")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value(documentId));
        mvc.perform(get("/api/v1/platform/verification-documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/v1/platform/verification-documents/" + documentId + "/review")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"notes\":\"Registration document checked\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reviewStatus").value("APPROVED"));
        mvc.perform(get("/api/v1/platform/tenants/" + vendorTenantId + "/verification-documents")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolePermissionMatrixRejectsPrivilegedActionsForSalesCustomerAndPlatformUsers() throws Exception {
        var salesToken = accessToken("sales@saumya.local", "Sales@123");
        var customerToken = accessToken("customer@saumya.local", "Customer@123");
        var platformToken = accessToken("platform@saumya.local", "Platform@123");

        mvc.perform(get("/api/v1/roles/permissions").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"UNAUTHORIZED_ROLE\",\"permissionCodes\":[\"CATALOG_VIEW\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/invoices").header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\""+UUID.randomUUID()+"\",\"taxTreatment\":\"IGST\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized product\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void notificationInboxIsScopedToAuthenticatedRecipientAndSupportsReadState() throws Exception {
        var vendor = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        var customer = users.findByEmailIgnoreCase("customer@saumya.local").orElseThrow();
        notifications.sendBusinessNotification(vendor.getId(), vendorTenantId, "TEST_VENDOR_NOTICE",
                vendor.getEmail(), "Vendor notice", "Private test message");
        notifications.sendBusinessNotification(customer.getId(), vendorTenantId, "TEST_CUSTOMER_NOTICE",
                customer.getEmail(), "Customer notice", "Private customer message");

        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
        var customerToken = accessToken("customer@saumya.local", "Customer@123");
        var vendorResponse = mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(vendorResponse.contains("TEST_VENDOR_NOTICE"));
        assertFalse(vendorResponse.contains("TEST_CUSTOMER_NOTICE"));
        var vendorRows = json.readTree(vendorResponse);
        String vendorNoticeId = null;
        for (JsonNode row : vendorRows) {
            if ("TEST_VENDOR_NOTICE".equals(row.path("eventType").asText())) {
                vendorNoticeId = row.path("id").asText();
                break;
            }
        }
        assertNotNull(vendorNoticeId);

        var customerResponse = mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(customerResponse.contains("TEST_CUSTOMER_NOTICE"));
        assertFalse(customerResponse.contains("TEST_VENDOR_NOTICE"));

        mvc.perform(post("/api/v1/notifications/" + vendorNoticeId + "/read")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.readAt").isNotEmpty());
        mvc.perform(post("/api/v1/notifications/" + vendorNoticeId + "/read")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditLogViewerIsRestrictedToVendorAndPlatformAdministrators() throws Exception {
        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
        var platformToken = accessToken("platform@saumya.local", "Platform@123");
        var customerToken = accessToken("customer@saumya.local", "Customer@123");
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedApiRequestsAreWrittenToTenantAwareAuditLog() throws Exception {
        String token=accessToken("admin@saumya.local","Admin@123");
        var user=users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        assertTrue(auditLogs.findTop20ByActorIdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(row -> "/api/v1/me".equals(row.getRequestPath())
                        && vendorTenantId.equals(row.getTenantId())
                        && row.getResponseStatus()==200));
    }

    @Test
    void employeeEndpointsCannotCrossTenantOrEscalateToPrivilegedRoles() throws Exception {
        Tenant foreignTenant=newTenant();
        AppUser foreignEmployee=new AppUser();
        foreignEmployee.setId(UUID.randomUUID());
        foreignEmployee.setTenantId(foreignTenant.getId());
        foreignEmployee.setName("Foreign employee");
        foreignEmployee.setEmail("foreign-user-"+UUID.randomUUID()+"@example.test");
        foreignEmployee.setPasswordHash("not-used");
        foreignEmployee.setAccountType("VENDOR");
        foreignEmployee.setEnabled(true);
        users.save(foreignEmployee);
        String vendorToken=accessToken("admin@saumya.local","Admin@123");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/"+foreignEmployee.getId())
                .header("Authorization","Bearer "+vendorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Taken over\",\"email\":\"takeover@example.test\",\"roleName\":\"VENDOR_ADMIN\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/"+foreignEmployee.getId()+"/enabled")
                .header("Authorization","Bearer "+vendorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+vendorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Escalation\",\"email\":\"escalation-"+UUID.randomUUID()+"@example.test\",\"password\":\"StrongEmployeePass-2026\",\"roleName\":\"VENDOR_ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void platformAndCustomerPrincipalsCannotCallVendorOnlyApis() throws Exception {
        String platformToken=accessToken("platform@saumya.local","Platform@123");
        mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+platformToken))
                .andExpect(status().isForbidden());
        String customerEmail="rbac-"+UUID.randomUUID()+"@example.test";
        String customerJson=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"storeSlug":"saumya-solar","name":"RBAC Customer","email":"%s","password":"StrongCustomerPass-2026"}
                        """.formatted(customerEmail)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String customerToken=json.readTree(customerJson).get("accessToken").asText();
        mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void vendorAdminCanCreateScopedCustomRolesButCannotDelegatePlatformPrivileges() throws Exception {
        String admin=accessToken("admin@saumya.local","Admin@123");
        mvc.perform(post("/api/v1/roles").header("Authorization","Bearer "+admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CUSTOM_CATALOG_VIEWER\",\"permissionCodes\":[\"CATALOG_VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name").value("CUSTOM_CATALOG_VIEWER"));
        mvc.perform(post("/api/v1/roles").header("Authorization","Bearer "+admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CUSTOM_PLATFORM_ADMIN\",\"permissionCodes\":[\"PLATFORM_MANAGE\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/roles").header("Authorization","Bearer "+admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CUSTOM_USER_ADMIN\",\"permissionCodes\":[\"USER_MANAGE\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/roles/permissions").header("Authorization","Bearer "+admin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isArray());
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
        tenant.setVerificationStatus("VERIFIED");
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
        var customerOrderView=json.readTree(customerOrdersJson).get(0);
        assertEquals(orderId.toString(),customerOrderView.get("id").asText());
        assertEquals("NOT_INVOICED",customerOrderView.get("paymentStatus").asText());
        assertEquals(1,customerOrderView.get("items").size());
        assertTrue(customerOrderView.get("items").get(0).get("description").asText().contains(product.get("name").asText()));
        mvc.perform(get("/api/v1/customer/product-orders/"+orderId).header("Authorization","Bearer "+customerToken))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].quantity").value(2));
        String otherCustomerEmail="order-owner-"+UUID.randomUUID()+"@example.test";
        String otherCustomerJson=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"storeSlug":"saumya-solar","name":"Other Customer","email":"%s","password":"StrongCustomerPass-2026"}
                        """.formatted(otherCustomerEmail)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String otherCustomerToken=json.readTree(otherCustomerJson).get("accessToken").asText();
        mvc.perform(get("/api/v1/customer/product-orders/"+orderId).header("Authorization","Bearer "+otherCustomerToken))
                .andExpect(status().isNotFound());
    }
}
