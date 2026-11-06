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
    }

    @Test
    void salesRoleCannotReadCatalogueWithoutPermission() throws Exception {
        var token = accessToken("sales@saumya.local", "Sales@123");
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token))
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
