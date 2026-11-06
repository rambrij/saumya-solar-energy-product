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
                .andExpect(status().isForbidden());
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
                .andExpect(status().isForbidden());
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
        var foreignProduct = new Product();
        foreignProduct.setId(UUID.randomUUID());
        foreignProduct.setTenantId(foreignTenant.getId());
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
                                {"sku":"JOURNEY-%s","name":"Journey solar panel","categoryId":"%s","price":10000,"taxPercent":5}
                                """.formatted(UUID.randomUUID(), categoryId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var productId = json.readTree(productResponse).get("id").asText();

        var quoteResponse = mvc.perform(post("/api/v1/quotations")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","leadId":"%s","surveyId":"%s","discountPercent":0,"validUntil":"%s","items":[{"productId":"%s","quantity":2}]}
                                """.formatted(profile.getId(), leadId, surveyId,
                                java.time.LocalDate.now().plusDays(30), productId)))
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
}
