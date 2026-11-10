package com.saumyasolar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Branch;
import com.saumyasolar.domain.CustomerAddress;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.Warehouse;
import com.saumyasolar.domain.InventoryBalance;
import com.saumyasolar.domain.InventoryTransaction;
import com.saumyasolar.domain.ProductCategory;
import com.saumyasolar.domain.Product;
import com.saumyasolar.domain.SalesLead;
import com.saumyasolar.domain.SolarPackage;
import com.saumyasolar.domain.Enquiry;
import com.saumyasolar.domain.Product;
import com.saumyasolar.domain.ProductCategory;
import com.saumyasolar.domain.CustomerProfile;
import com.saumyasolar.domain.CustomerOrder;
import com.saumyasolar.domain.Quotation;
import com.saumyasolar.domain.Invoice;
import com.saumyasolar.domain.ServiceRequest;
import com.saumyasolar.domain.InstallationJob;
import com.saumyasolar.domain.Warranty;
import com.saumyasolar.domain.WarrantyClaim;
import com.saumyasolar.domain.WarrantyClaimAttachment;
import com.saumyasolar.domain.BranchMembership;
import com.saumyasolar.domain.Role;
import com.saumyasolar.domain.Permission;
import com.saumyasolar.domain.Enquiry;
import com.saumyasolar.domain.SalesLead;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.repo.WarrantyClaimAttachmentRepository;
import com.saumyasolar.repo.BranchMembershipRepository;
import com.saumyasolar.repo.PermissionRepository;
import com.saumyasolar.repo.EnquiryRepository;
import com.saumyasolar.repo.SalesLeadRepository;
import com.saumyasolar.repo.WarehouseRepository;
import com.saumyasolar.repo.InventoryBalanceRepository;
import com.saumyasolar.repo.InventoryTransactionRepository;
import com.saumyasolar.repo.ProductCategoryRepository;
import com.saumyasolar.repo.ProductRepository;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.CustomerAddressRepository;
import com.saumyasolar.repo.BranchRepository;
import com.saumyasolar.repo.SiteSurveyRepository;
import com.saumyasolar.repo.SalesLeadRepository;
import com.saumyasolar.repo.SolarPackageRepository;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.RoleRepository;
import com.saumyasolar.repo.AuditEventRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.saumyasolar.repo.EnquiryRepository;
import com.saumyasolar.repo.ProductCategoryRepository;
import com.saumyasolar.repo.ProductRepository;
import com.saumyasolar.repo.CustomerOrderRepository;
import com.saumyasolar.repo.OrderItemRepository;
import com.saumyasolar.repo.QuotationRepository;
import com.saumyasolar.repo.InvoiceRepository;
import com.saumyasolar.repo.ServiceRequestRepository;
import com.saumyasolar.repo.InstallationJobRepository;
import com.saumyasolar.repo.WarrantyRepository;
import com.saumyasolar.repo.WarrantyClaimRepository;
import com.saumyasolar.repo.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = "app.warranty.attachment-location=${java.io.tmpdir}/saumya-warranty-attachment-it")
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
    RoleRepository roles;
    @Autowired
    AuditEventRepository auditEvents;
    @Autowired
    PasswordEncoder passwordEncoder;
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
    CustomerAddressRepository customerAddresses;
    @Autowired
    BranchRepository branches;
    @Autowired
    SiteSurveyRepository surveys;
    @Autowired
    SalesLeadRepository leads;
    @Autowired
    SolarPackageRepository packages;
    @Autowired
    CustomerOrderRepository customerOrders;
    @Autowired
    OrderItemRepository orderItems;
    @Autowired
    QuotationRepository quotations;
    @Autowired
    InvoiceRepository invoices;
    @Autowired
    ServiceRequestRepository serviceRequests;
    @Autowired
    InstallationJobRepository installationJobs;
    @Autowired
    WarrantyRepository warranties;
    @Autowired
    WarrantyClaimRepository warrantyClaims;
    @Autowired
    WarrantyClaimAttachmentRepository warrantyClaimAttachments;
    @Autowired BranchMembershipRepository branchMemberships;
    @Autowired PermissionRepository permissions;
    @Autowired EnquiryRepository enquiryRecords;
    @Autowired SalesLeadRepository salesLeadRecords;
    @Autowired WarehouseRepository warehouseRecords;
    @Autowired InventoryBalanceRepository inventoryBalances;
    @Autowired InventoryTransactionRepository inventoryTransactions;
    @Autowired ProductCategoryRepository productCategories;
    @Autowired ProductRepository productRecords;
    
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
    void platformSupportCanReadTenantDirectoryButCannotMutatePlatformState() throws Exception {
        var supportRole = roles.findByNameAndTenantIdIsNull("PLATFORM_SUPPORT").orElseThrow();
        var support = new AppUser();
        support.setId(UUID.randomUUID());
        support.setName("Read Only Support");
        support.setEmail("support-" + UUID.randomUUID() + "@example.test");
        support.setPasswordHash(passwordEncoder.encode("Support-Password-2026"));
        support.setAccountType("PLATFORM");
        support.setEnabled(true);
        support.getRoles().add(supportRole);
        users.save(support);
        var token = accessToken(support.getEmail(), "Support-Password-2026");
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        var from = Instant.now().minusSeconds(60);
        var to = Instant.now().plusSeconds(60);
        mvc.perform(get("/api/v1/audit-events").param("from", from.toString()).param("to", to.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertTrue(auditEvents.findTop200ByCreatedAtBetweenOrderByCreatedAtDesc(from, to).stream()
                .anyMatch(event -> "PLATFORM_SUPPORT_TENANT_DIRECTORY_VIEWED".equals(event.getAction())
                        || "PLATFORM_SUPPORT_AUDIT_LOG_VIEWED".equals(event.getAction())));
        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Support Must Not Create","slug":"support-must-not-create","adminName":"Support","adminEmail":"support-new@example.test","adminPassword":"Support-Password-2026"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfBootstrapReturnsTheReadableDoubleSubmitCookieValue() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@saumya.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn();
        var csrfCookie = login.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        mvc.perform(get("/api/v1/auth/csrf").cookie(csrfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").value(csrfCookie.getValue()));
    }

    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        var loginBody = """
                {"email":"admin@saumya.local","password":"Admin@123"}
                """;
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn();
        var loginResponse = loginResult.getResponse().getContentAsString();
        var session = json.readTree(loginResponse);
        var accessToken = session.get("accessToken").asText();
        var csrfToken = session.get("csrfToken").asText();
        var refreshCookie = responseCookie(loginResult, "saumya_refresh");
        var csrfCookie = responseCookie(loginResult, "XSRF-TOKEN");
        assertNotNull(refreshCookie);
        assertNotNull(csrfCookie);
        assertFalse(session.has("refreshToken"));
        assertTrue(loginResult.getResponse().getHeaders("Set-Cookie").stream().anyMatch(v -> v.contains("HttpOnly")));
        assertTrue(loginResult.getResponse().getHeaders("Set-Cookie").stream().anyMatch(v -> v.contains("SameSite")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isForbidden());

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
    void concurrentRefreshReuseRevokesTheWholeDatabaseTokenFamily() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@saumya.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn();
        var csrfToken = json.readTree(login.getResponse().getContentAsString()).get("csrfToken").asText();
        var refreshCookie = login.getResponse().getCookie("saumya_refresh");
        var csrfCookie = login.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(refreshCookie);
        assertNotNull(csrfCookie);

        var gate = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> request = () -> {
                gate.await();
                return mvc.perform(post("/api/v1/auth/refresh")
                                .cookie(refreshCookie, csrfCookie)
                                .header("X-CSRF-Token", csrfToken))
                        .andReturn();
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            gate.countDown();
            var firstResult = first.get();
            var secondResult = second.get();
            int firstStatus = firstResult.getResponse().getStatus();
            int secondStatus = secondResult.getResponse().getStatus();
            assertTrue((firstStatus == 200 && secondStatus == 401) || (firstStatus == 401 && secondStatus == 200),
                    "Exactly one concurrent refresh can rotate the same token");
            var rotated = firstStatus == 200 ? firstResult : secondResult;
            var rotatedCookie = responseCookie(rotated, "saumya_refresh");
            var rotatedCsrfCookie = responseCookie(rotated, "XSRF-TOKEN");
            var rotatedCsrf = json.readTree(rotated.getResponse().getContentAsString()).get("csrfToken").asText();
            assertNotNull(rotatedCookie);
            assertNotNull(rotatedCsrfCookie);
            mvc.perform(post("/api/v1/auth/refresh")
                            .cookie(rotatedCookie, rotatedCsrfCookie)
                            .header("X-CSRF-Token", rotatedCsrf))
                    .andExpect(status().isUnauthorized());
        } finally {
            pool.shutdownNow();
        }
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
    void customServiceViewerCanOnlyReadTicketsAssignedToItsUser() throws Exception {
        var adminToken = accessToken("admin@saumya.local", "Admin@123");
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CUSTOM_SERVICE_READ","permissions":["SERVICE_VIEW"]}
                                """))
                .andExpect(status().isOk());
        var email = "custom-service-" + UUID.randomUUID() + "@example.test";
        var password = "Custom-Service-Password-2026";
        var userResponse = mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Custom Service Reader","email":"%s","password":"%s","roleName":"CUSTOM_SERVICE_READ"}
                                """.formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var customUserId = UUID.fromString(json.readTree(userResponse).get("id").asText());
        var otherUserId = users.findByEmailIgnoreCase("sales@saumya.local").orElseThrow().getId();

        ServiceRequest assigned = new ServiceRequest();
        assigned.setId(UUID.randomUUID());
        assigned.setTenantId(vendorTenantId);
        assigned.setTicketNumber("CUSTOM-ASSIGNED-" + UUID.randomUUID().toString().substring(0, 8));
        assigned.setCategory("GENERAL");
        assigned.setPriority("LOW");
        assigned.setWarrantyStatus("UNKNOWN");
        assigned.setStatus("ASSIGNED");
        assigned.setDescription("Assigned custom-role test ticket");
        assigned.setAssignedEngineerId(customUserId);
        assigned.setCreatedAt(Instant.now());
        assigned.setUpdatedAt(Instant.now());
        serviceRequests.save(assigned);

        ServiceRequest other = new ServiceRequest();
        other.setId(UUID.randomUUID());
        other.setTenantId(vendorTenantId);
        other.setTicketNumber("CUSTOM-OTHER-" + UUID.randomUUID().toString().substring(0, 8));
        other.setCategory("GENERAL");
        other.setPriority("LOW");
        other.setWarrantyStatus("UNKNOWN");
        other.setStatus("ASSIGNED");
        other.setDescription("Other employee ticket");
        other.setAssignedEngineerId(otherUserId);
        other.setCreatedAt(Instant.now());
        other.setUpdatedAt(Instant.now());
        serviceRequests.save(other);

        var customToken = accessToken(email, password);
        var list = mvc.perform(get("/api/v1/service-requests").header("Authorization", "Bearer " + customToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains(assigned.getTicketNumber()));
        assertFalse(list.contains(other.getTicketNumber()));
        mvc.perform(get("/api/v1/service-requests/" + other.getId())
                        .header("Authorization", "Bearer " + customToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void vendorCannotReadAnotherTenantsOrdersQuotesInvoicesServiceOrInstallationRecords() throws Exception {
        var foreignTenant = new Tenant();
        foreignTenant.setId(UUID.randomUUID());
        foreignTenant.setName("Foreign Vendor");
        foreignTenant.setSlug("foreign-" + UUID.randomUUID().toString().replace("-", ""));
        foreignTenant.setStatus("ACTIVE");
        foreignTenant.setCreatedAt(Instant.now());
        foreignTenant.setUpdatedAt(Instant.now());
        tenants.save(foreignTenant);

        var customer = new CustomerProfile();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(foreignTenant.getId());
        customer.setName("Foreign Customer");
        customer.setEmail("foreign-" + UUID.randomUUID() + "@example.test");
        customerProfiles.save(customer);

        var quote = new Quotation();
        quote.setId(UUID.randomUUID());
        quote.setTenantId(foreignTenant.getId());
        quote.setQuoteGroupId(UUID.randomUUID());
        quote.setQuotationNumber("FOREIGN-" + UUID.randomUUID().toString().substring(0, 8));
        quote.setCustomerId(customer.getId());
        quote.setStatus("DRAFT");
        quote.setSubtotal(BigDecimal.ZERO);
        quote.setDiscountPercent(BigDecimal.ZERO);
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setTaxAmount(BigDecimal.ZERO);
        quote.setGrandTotal(BigDecimal.ZERO);
        quote.setCreatedAt(Instant.now());
        quotations.save(quote);

        var order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setTenantId(foreignTenant.getId());
        order.setQuotationId(quote.getId());
        order.setCustomerId(customer.getId());
        order.setOrderNumber("FOREIGN-ORDER-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus("PENDING");
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscountPercent(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxTotal(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        order.setCreatedAt(Instant.now());
        customerOrders.save(order);

        var invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(foreignTenant.getId());
        invoice.setOrderId(order.getId());
        invoice.setInvoiceNumber("FOREIGN-INV-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setStatus("DRAFT");
        invoice.setFiscalYear("2026-27");
        invoice.setCustomerName(customer.getName());
        invoice.setCustomerEmail(customer.getEmail());
        invoice.setVendorName(foreignTenant.getName());
        invoice.setSubtotal(BigDecimal.ZERO);
        invoice.setTaxTotal(BigDecimal.ZERO);
        invoice.setGrandTotal(BigDecimal.ZERO);
        invoice.setTaxTreatment("INTRA_STATE");
        invoice.setDueDate(java.time.LocalDate.now().plusDays(30));
        invoice.setCreatedAt(Instant.now());
        invoices.save(invoice);

        var ticket = new ServiceRequest();
        ticket.setId(UUID.randomUUID());
        ticket.setTenantId(foreignTenant.getId());
        ticket.setCustomerId(customer.getId());
        ticket.setTicketNumber("FOREIGN-SVC-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setCategory("GENERAL");
        ticket.setPriority("LOW");
        ticket.setWarrantyStatus("UNKNOWN");
        ticket.setStatus("OPEN");
        ticket.setDescription("Foreign tenant test record");
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        serviceRequests.save(ticket);

        var installation = new InstallationJob();
        installation.setId(UUID.randomUUID());
        installation.setTenantId(foreignTenant.getId());
        installation.setCustomerId(customer.getId());
        installation.setOrderId(order.getId());
        installation.setStatus("SCHEDULED");
        installation.setCapacityKw(BigDecimal.ZERO);
        installation.setScheduledAt(Instant.now());
        installation.setCreatedAt(Instant.now());
        installationJobs.save(installation);

        var token = accessToken("admin@saumya.local", "Admin@123");
        mvc.perform(get("/api/v1/orders/" + order.getId() + "/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/quotations/" + quote.getId() + "/revisions").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invoices/" + invoice.getId() + "/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-requests/" + ticket.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/installations/" + installation.getId() + "/checklist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void salesExecutiveCanOnlyReadAndUpdateAssignedLeads() throws Exception {
        var adminToken = accessToken("admin@saumya.local", "Admin@123");
        var salesUser = users.findByEmailIgnoreCase("sales@saumya.local").orElseThrow();
        var managerEmail = "sales-manager-" + UUID.randomUUID() + "@example.test";
        var managerBody = """
                {"name":"Sales Manager Test","email":"%s","password":"SalesManager-Pass-2026","roleName":"SALES_MANAGER"}
                """.formatted(managerEmail);
        var managerResponse = mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(managerBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var managerId = json.readTree(managerResponse).get("id").asText();

        var firstEnquiry = mvc.perform(post("/api/v1/enquiries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Assigned Lead","email":"assigned-%s@example.test","phone":"9000000001","propertyType":"Residential"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var firstEnquiryId = json.readTree(firstEnquiry).get("id").asText();
        var assignedLead = mvc.perform(post("/api/v1/enquiries/" + firstEnquiryId + "/convert")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","priority":"MEDIUM"}
                                """.formatted(salesUser.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var assignedLeadId = json.readTree(assignedLead).get("id").asText();

        var secondEnquiry = mvc.perform(post("/api/v1/enquiries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other Lead","email":"other-%s@example.test","phone":"9000000002","propertyType":"Residential"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var secondEnquiryId = json.readTree(secondEnquiry).get("id").asText();
        var otherLead = mvc.perform(post("/api/v1/enquiries/" + secondEnquiryId + "/convert")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","priority":"MEDIUM"}
                                """.formatted(managerId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var otherLeadId = json.readTree(otherLead).get("id").asText();

        var salesToken = accessToken("sales@saumya.local", "Sales@123");
        var visibleLeads = mvc.perform(get("/api/v1/leads").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(visibleLeads.contains(assignedLeadId));
        assertFalse(visibleLeads.contains(otherLeadId));
        mvc.perform(get("/api/v1/leads/" + otherLeadId + "/history").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/leads/" + assignedLeadId)
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","stage":"CONTACTED","priority":"HIGH"}
                                """.formatted(managerId)))
                .andExpect(status().isForbidden());
    }


    @Test
    void customSalesRoleCannotReadUnassignedLeadsOrAssignOutsideItsScope() throws Exception {
        var adminToken = accessToken("admin@saumya.local", "Admin@123");
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CUSTOM_SALES_READ","permissions":["LEAD_VIEW","LEAD_UPDATE"]}
                                """))
                .andExpect(status().isOk());
        var suffix = UUID.randomUUID().toString().replace("-", "");
        var customEmail = "custom-sales-" + suffix + "@example.test";
        var customPassword = "Custom-Sales-Password-2026";
        var customUser = mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Custom Sales Reader","email":"%s","password":"%s","roleName":"CUSTOM_SALES_READ"}
                                """.formatted(customEmail, customPassword)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var customUserId = json.readTree(customUser).get("id").asText();
        var assignedEnquiry = mvc.perform(post("/api/v1/enquiries").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Custom Assigned","email":"assigned-%s@example.test","propertyType":"Residential"}
                                """.formatted(suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var assignedEnquiryId = json.readTree(assignedEnquiry).get("id").asText();
        var assignedLead = mvc.perform(post("/api/v1/enquiries/" + assignedEnquiryId + "/convert")
                        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","priority":"MEDIUM"}
                                """.formatted(customUserId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var assignedLeadId = json.readTree(assignedLead).get("id").asText();
        var otherEnquiry = mvc.perform(post("/api/v1/enquiries").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Custom Other","email":"other-%s@example.test","propertyType":"Residential"}
                                """.formatted(suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var otherEnquiryId = json.readTree(otherEnquiry).get("id").asText();
        var otherLead = mvc.perform(post("/api/v1/enquiries/" + otherEnquiryId + "/convert")
                        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","priority":"MEDIUM"}
                                """.formatted(users.findByEmailIgnoreCase("sales@saumya.local").orElseThrow().getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var otherLeadId = json.readTree(otherLead).get("id").asText();

        var customToken = accessToken(customEmail, customPassword);
        var visible = mvc.perform(get("/api/v1/leads").header("Authorization", "Bearer " + customToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(visible.contains(assignedLeadId));
        assertFalse(visible.contains(otherLeadId));
        mvc.perform(patch("/api/v1/leads/" + assignedLeadId).header("Authorization", "Bearer " + customToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s","stage":"CONTACTED"}
                                """.formatted(users.findByEmailIgnoreCase("sales@saumya.local").orElseThrow().getId())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CUSTOM_PLATFORM_ESCAPE","permissions":["PLATFORM_MANAGE"]}
                                """))
                .andExpect(status().isForbidden());
        var auditFrom = Instant.now().minusSeconds(60);
        var auditTo = Instant.now().plusSeconds(60);
        assertTrue(auditEvents.findTop200ByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorTenantId, auditFrom, auditTo)
                .stream().anyMatch(event -> "ROLE_PRIVILEGE_ESCALATION_BLOCKED".equals(event.getAction())
                        && "DENIED".equals(event.getOutcome())),
                "Blocked custom-role platform permission escalation must be durably audited");
    }


    @Test
    void everySeededEmployeeRoleHasItsDeclaredFineGrainedSrsPermissionSet() {
        var forbidden = java.util.Set.of("PLATFORM_MANAGE", "SUBSCRIPTION_MANAGE", "TENANT_VIEW", "TENANT_UPDATE",
                "CATALOG_MANAGE", "CATALOG_VIEW", "CUSTOMER_MANAGE", "LEAD_MANAGE", "SURVEY_MANAGE",
                "QUOTATION_MANAGE", "DISCOUNT_APPROVE", "USER_MANAGE", "INVENTORY_MANAGE", "SERVICE_MANAGE", "FINANCE_MANAGE");
        com.saumyasolar.config.DataSeeder.employeeRolePermissions().forEach((roleName, expected) -> {
            var role = roles.findByNameAndTenantId(roleName, vendorTenantId).orElseThrow();
            var actual = role.getPermissions().stream().map(com.saumyasolar.domain.Permission::getCode)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(actual.containsAll(expected), "Missing declared permission for " + roleName);
            assertTrue(java.util.Collections.disjoint(actual, forbidden), "Legacy/platform permission on " + roleName);
        });
    }

    @Test
    void vendorAdminAndCustomRolesNeverReceivePlatformOrLegacyBroadPermissions() {
        var admin = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        var codes = admin.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(com.saumyasolar.domain.Permission::getCode).collect(java.util.stream.Collectors.toSet());
        assertFalse(codes.contains("PLATFORM_MANAGE"));
        assertFalse(codes.contains("SUBSCRIPTION_MANAGE"));
        assertFalse(codes.contains("TENANT_VIEW"));
        assertFalse(codes.contains("TENANT_UPDATE"));
        for (String legacy : java.util.List.of("CATALOG_MANAGE", "CATALOG_VIEW", "CUSTOMER_MANAGE",
                "LEAD_MANAGE", "SURVEY_MANAGE", "QUOTATION_MANAGE", "INVENTORY_MANAGE",
                "SERVICE_MANAGE", "FINANCE_MANAGE", "USER_MANAGE", "DISCOUNT_APPROVE"))
            assertFalse(codes.contains(legacy), "Legacy broad permission must not be granted: " + legacy);
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
                .andExpect(status().isForbidden());
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void missingAndMalformedBearerTokensAreUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private jakarta.servlet.http.Cookie responseCookie(org.springframework.test.web.servlet.MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + name + " response cookie"));
        String value = header.substring(name.length() + 1).split(";", 2)[0];
        return new jakarta.servlet.http.Cookie(name, value);
    }


    @Test
    void customerAddressEndpointsEnforceAuthenticatedCustomerOwnershipAndIgnoreTamperedOwnerIds() throws Exception {
        var customerA = createCustomer(vendorTenantId, "address-owner-a");
        var customerB = createCustomer(vendorTenantId, "address-owner-b");
        var foreignTenant = newTenant();
        var customerC = createCustomer(foreignTenant.getId(), "address-owner-c");
        var addressA = createAddress(customerA.profile().getId(), vendorTenantId);
        var addressB = createAddress(customerB.profile().getId(), vendorTenantId);
        var addressC = createAddress(customerC.profile().getId(), foreignTenant.getId());

        var token = accessToken(customerA.user().getEmail(), "Customer-Password-2026");
        var listed = mvc.perform(get("/api/v1/customer/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(listed.contains(addressA.getId().toString()));
        assertFalse(listed.contains(addressB.getId().toString()));
        assertFalse(listed.contains(addressC.getId().toString()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/customer/addresses/" + addressB.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody()))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/customer/addresses/" + addressC.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        var created = mvc.perform(post("/api/v1/customer/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Tampered","line1":"1 Test Road","city":"Raipur","state":"Chhattisgarh","pincode":"492001","tenantId":"%s","customerId":"%s"}
                                """.formatted(foreignTenant.getId(), customerC.profile().getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var createdJson = json.readTree(created);
        assertEquals(vendorTenantId.toString(), createdJson.get("tenantId").asText());
        assertEquals(customerA.profile().getId().toString(), createdJson.get("customerId").asText());
    }



    @Test
    void genericVendorListEndpointsNeverReturnForeignTenantResources() throws Exception {
        var foreignTenant = newTenant();

        AppUser foreignVendor = new AppUser();
        foreignVendor.setId(UUID.randomUUID());
        foreignVendor.setTenantId(foreignTenant.getId());
        foreignVendor.setName("Foreign Vendor User");
        foreignVendor.setEmail("foreign-vendor-" + UUID.randomUUID() + "@example.test");
        foreignVendor.setPasswordHash("not-used");
        foreignVendor.setAccountType("VENDOR");
        foreignVendor.setEnabled(true);
        users.save(foreignVendor);

        ProductCategory category = new ProductCategory();
        category.setId(UUID.randomUUID());
        category.setTenantId(foreignTenant.getId());
        category.setName("Foreign category " + UUID.randomUUID());
        category.setActive(true);
        categories.save(category);

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setTenantId(foreignTenant.getId());
        product.setCategoryId(category.getId());
        product.setSku("FOREIGN-" + UUID.randomUUID());
        product.setName("Foreign product");
        product.setPrice(BigDecimal.TEN);
        product.setTaxPercent(BigDecimal.ZERO);
        product.setActive(true);
        products.save(product);

        SolarPackage solarPackage = new SolarPackage();
        solarPackage.setId(UUID.randomUUID());
        solarPackage.setTenantId(foreignTenant.getId());
        solarPackage.setCode("FOREIGN-" + UUID.randomUUID().toString().substring(0, 8));
        solarPackage.setName("Foreign package");
        solarPackage.setActive(true);
        packages.save(solarPackage);

        CustomerProfile customer = createCustomer(foreignTenant.getId(), "generic-list-customer").profile();

        Enquiry enquiry = new Enquiry();
        enquiry.setId(UUID.randomUUID());
        enquiry.setTenantId(foreignTenant.getId());
        enquiry.setCustomerId(customer.getId());
        enquiry.setName("Foreign enquiry");
        enquiry.setStatus("NEW");
        enquiry.setCreatedAt(Instant.now());
        enquiries.save(enquiry);

        SalesLead lead = new SalesLead();
        lead.setId(UUID.randomUUID());
        lead.setTenantId(foreignTenant.getId());
        lead.setEnquiryId(enquiry.getId());
        lead.setCustomerId(customer.getId());
        lead.setStage("NEW");
        lead.setPriority("LOW");
        lead.setCreatedAt(Instant.now());
        leads.save(lead);

        SiteSurvey survey = new SiteSurvey();
        survey.setId(UUID.randomUUID());
        survey.setTenantId(foreignTenant.getId());
        survey.setLeadId(lead.getId());
        survey.setPropertyType("RESIDENTIAL");
        survey.setRoofType("RCC");
        survey.setStatus("SCHEDULED");
        survey.setScheduledAt(Instant.now().plusSeconds(3600));
        surveys.save(survey);

        Quotation quotation = new Quotation();
        quotation.setId(UUID.randomUUID());
        quotation.setTenantId(foreignTenant.getId());
        quotation.setQuoteGroupId(UUID.randomUUID());
        quotation.setQuotationNumber("FOREIGN-" + UUID.randomUUID().toString().substring(0, 8));
        quotation.setRevision(1);
        quotation.setCustomerId(customer.getId());
        quotation.setLeadId(lead.getId());
        quotation.setSurveyId(survey.getId());
        quotation.setStatus("DRAFT");
        quotation.setSubtotal(BigDecimal.ZERO);
        quotation.setDiscountPercent(BigDecimal.ZERO);
        quotation.setDiscountAmount(BigDecimal.ZERO);
        quotation.setTaxAmount(BigDecimal.ZERO);
        quotation.setGrandTotal(BigDecimal.ZERO);
        quotation.setCreatedBy(foreignVendor.getId());
        quotation.setCreatedAt(Instant.now());
        quotations.save(quotation);

        CustomerOrder order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setTenantId(foreignTenant.getId());
        order.setQuotationId(quotation.getId());
        order.setCustomerId(customer.getId());
        order.setOrderNumber("FOREIGN-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus("PENDING");
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscountPercent(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxTotal(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        order.setCreatedAt(Instant.now());
        customerOrders.save(order);

        var token = accessToken("admin@saumya.local", "Admin@123");
        mvc.perform(patch("/api/v1/products/" + product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"TAMPERED","name":"Tampered foreign product","price":1,"purchasePrice":0,"discountPercent":0,"taxPercent":0,"reorderLevel":0}
                                """.formatted(category.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/products/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tampered foreign category\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/packages/" + solarPackage.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TAMPERED","name":"Tampered foreign package","capacityKw":1,"packageType":"RESIDENTIAL","basePrice":1,"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(product.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/packages/" + solarPackage.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        Map<String, UUID> foreignIds = Map.of(
                "users", foreignVendor.getId(),
                "categories", category.getId(),
                "products", product.getId(),
                "packages", solarPackage.getId(),
                "customers", customer.getId(),
                "enquiries", enquiry.getId(),
                "leads", lead.getId(),
                "surveys", survey.getId(),
                "quotations", quotation.getId(),
                "orders", order.getId()
        );
        for (var entry : foreignIds.entrySet()) {
            String body = mvc.perform(get("/api/v1/" + entry.getKey())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(entry.getValue().toString()),
                    "Foreign tenant resource leaked from /" + entry.getKey());
        }

        var customerActor = createCustomer(vendorTenantId, "catalog-customer");
        var customerToken = accessToken(customerActor.user().getEmail(), "Customer-Password-2026");
        for (String type : List.of("catalog-categories", "catalog-products", "catalog-packages")) {
            String body = mvc.perform(get("/api/v1/" + type)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(category.getId().toString()));
            assertFalse(body.contains(product.getId().toString()));
            assertFalse(body.contains(solarPackage.getId().toString()));
        }
        mvc.perform(post("/api/v1/customer/product-orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":1,"tenantId":"%s","customerId":"%s"}
                                """.formatted(product.getId(), foreignTenant.getId(), customer.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void operationsCsvXlsxAndPdfExportsContainOnlyAuthenticatedTenantAggregates() throws Exception {
        var foreignTenant = newTenant();
        CustomerProfile foreignCustomer = createCustomer(foreignTenant.getId(), "report-export-foreign").profile();

        CustomerOrder foreignOrder = new CustomerOrder();
        foreignOrder.setId(UUID.randomUUID());
        foreignOrder.setTenantId(foreignTenant.getId());
        foreignOrder.setCustomerId(foreignCustomer.getId());
        foreignOrder.setOrderNumber("FOREIGN-EXPORT-" + UUID.randomUUID().toString().substring(0, 8));
        foreignOrder.setStatus("CONFIRMED");
        foreignOrder.setSubtotal(BigDecimal.valueOf(987654));
        foreignOrder.setDiscountPercent(BigDecimal.ZERO);
        foreignOrder.setDiscountAmount(BigDecimal.ZERO);
        foreignOrder.setTaxTotal(BigDecimal.ZERO);
        foreignOrder.setTotal(BigDecimal.valueOf(987654));
        foreignOrder.setCreatedAt(Instant.now());
        customerOrders.save(foreignOrder);

        var token = accessToken("admin@saumya.local", "Admin@123");
        var reportResponse = mvc.perform(get("/api/v1/reports/operations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode report = json.readTree(reportResponse);
        long expectedOrders = customerOrders.findByTenantIdOrderByIdDesc(vendorTenantId).stream()
                .filter(x -> x.getCreatedAt() != null).count();
        assertEquals(expectedOrders, report.get("orders").asLong());

        var csv = mvc.perform(get("/api/v1/reports/operations/export.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("orders," + expectedOrders));
        assertFalse(csv.contains("987654"));
        assertFalse(csv.contains(foreignOrder.getId().toString()));

        byte[] xlsx = mvc.perform(get("/api/v1/reports/operations/export.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheet("Operations");
            assertNotNull(sheet);
            boolean foundOrders = false;
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                var cells = sheet.getRow(row);
                if (cells != null && "orders".equals(cells.getCell(0).getStringCellValue())) {
                    assertEquals(String.valueOf(expectedOrders), cells.getCell(1).getStringCellValue());
                    foundOrders = true;
                }
            }
            assertTrue(foundOrders, "XLSX must contain the authenticated tenant's order aggregate");
            assertFalse(xlsx.length == 0);
            assertFalse(new String(xlsx, java.nio.charset.StandardCharsets.ISO_8859_1).contains("987654"));
        }

        byte[] pdf = mvc.perform(get("/api/v1/reports/operations/export.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String pdfText = new com.lowagie.text.pdf.parser.PdfTextExtractor(
                new com.lowagie.text.pdf.PdfReader(pdf)).getTextFromPage(1, true);
        assertTrue(pdfText.contains("orders"));
        assertTrue(pdfText.contains(String.valueOf(expectedOrders)));
        assertFalse(pdfText.contains("987654"));
    }

    @Test
    void warrantyClaimListCreateAndDecisionAreTenantAndCustomerScoped() throws Exception {
        var customerA = createCustomer(vendorTenantId, "claim-customer-a");
        var customerB = createCustomer(vendorTenantId, "claim-customer-b");
        var foreignTenant = newTenant();
        var foreignCustomer = createCustomer(foreignTenant.getId(), "claim-customer-foreign");

        Warranty foreignWarranty = new Warranty();
        foreignWarranty.setId(UUID.randomUUID());
        foreignWarranty.setTenantId(foreignTenant.getId());
        foreignWarranty.setCustomerId(foreignCustomer.profile().getId());
        foreignWarranty.setStatus("ACTIVE");
        foreignWarranty.setStartDate(java.time.LocalDate.now().minusDays(5));
        foreignWarranty.setEndDate(java.time.LocalDate.now().plusDays(30));
        warranties.save(foreignWarranty);

        WarrantyClaim foreignClaim = new WarrantyClaim();
        foreignClaim.setId(UUID.randomUUID());
        foreignClaim.setTenantId(foreignTenant.getId());
        foreignClaim.setWarrantyId(foreignWarranty.getId());
        foreignClaim.setCustomerId(foreignCustomer.profile().getId());
        foreignClaim.setClaimNumber("FOREIGN-CLAIM-" + UUID.randomUUID().toString().substring(0, 8));
        foreignClaim.setSubject("Foreign warranty claim");
        foreignClaim.setDescription("Tenant isolation fixture");
        foreignClaim.setStatus("SUBMITTED");
        foreignClaim.setCreatedAt(Instant.now());
        foreignClaim.setUpdatedAt(Instant.now());
        warrantyClaims.save(foreignClaim);

        Warranty customerBWarranty = new Warranty();
        customerBWarranty.setId(UUID.randomUUID());
        customerBWarranty.setTenantId(vendorTenantId);
        customerBWarranty.setCustomerId(customerB.profile().getId());
        customerBWarranty.setStatus("ACTIVE");
        customerBWarranty.setStartDate(java.time.LocalDate.now().minusDays(5));
        customerBWarranty.setEndDate(java.time.LocalDate.now().plusDays(30));
        warranties.save(customerBWarranty);
        WarrantyClaim customerBClaim = new WarrantyClaim();
        customerBClaim.setId(UUID.randomUUID());
        customerBClaim.setTenantId(vendorTenantId);
        customerBClaim.setWarrantyId(customerBWarranty.getId());
        customerBClaim.setCustomerId(customerB.profile().getId());
        customerBClaim.setClaimNumber("CUSTOMER-B-CLAIM-" + UUID.randomUUID().toString().substring(0, 8));
        customerBClaim.setSubject("Customer B claim");
        customerBClaim.setDescription("Same-tenant customer isolation fixture");
        customerBClaim.setStatus("SUBMITTED");
        customerBClaim.setCreatedAt(Instant.now());
        customerBClaim.setUpdatedAt(Instant.now());
        warrantyClaims.save(customerBClaim);

        var customerAToken = accessToken(customerA.user().getEmail(), "Customer-Password-2026");
        var customerBToken = accessToken(customerB.user().getEmail(), "Customer-Password-2026");
        String customerAClaims = mvc.perform(get("/api/v1/customer/warranty-claims")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(customerAClaims.contains(foreignClaim.getId().toString()));
        assertFalse(customerAClaims.contains(customerBClaim.getId().toString()));

        String customerBClaims = mvc.perform(get("/api/v1/customer/warranty-claims")
                        .header("Authorization", "Bearer " + customerBToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(customerBClaims.contains(foreignClaim.getId().toString()));
        assertTrue(customerBClaims.contains(customerBClaim.getId().toString()));

        mvc.perform(post("/api/v1/customer/warranty-claims")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warrantyId":"%s","subject":"Tampered claim","description":"Cross-customer warranty"}
                                """.formatted(foreignWarranty.getId())))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/customer/warranty-claims")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warrantyId":"%s","subject":"Same-tenant customer tampering","description":"Attempt to claim another customer's warranty"}
                                """.formatted(customerBWarranty.getId())))
                .andExpect(status().isNotFound());

        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
        String vendorClaims = mvc.perform(get("/api/v1/warranty-claims")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(vendorClaims.contains(foreignClaim.getId().toString()));

        mvc.perform(post("/api/v1/warranty-claims/" + foreignClaim.getId() + "/approve")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Tampered cross-tenant decision\"}"))
                .andExpect(status().isNotFound());
        assertEquals("SUBMITTED", warrantyClaims.findById(foreignClaim.getId()).orElseThrow().getStatus());
    }

    @Test
    void customerCannotReadAnotherCustomersOrdersInvoicesOrServiceTickets() throws Exception {
        var customerA = createCustomer(vendorTenantId, "customer-scope-a");
        var customerB = createCustomer(vendorTenantId, "customer-scope-b");
        var vendor = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();

        CustomerOrder order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setTenantId(vendorTenantId);
        order.setCustomerId(customerB.profile().getId());
        order.setOrderNumber("CUSTOMER-B-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus("CONFIRMED");
        order.setSubtotal(BigDecimal.TEN);
        order.setDiscountPercent(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxTotal(BigDecimal.ZERO);
        order.setTotal(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());
        customerOrders.save(order);

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(vendorTenantId);
        invoice.setOrderId(order.getId());
        invoice.setInvoiceNumber("CUSTOMER-B-INV-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setFiscalYear("2026-27");
        invoice.setStatus("ISSUED");
        invoice.setCustomerName(customerB.profile().getName());
        invoice.setCustomerEmail(customerB.profile().getEmail());
        invoice.setVendorName("Saumya Solar");
        invoice.setCurrency("INR");
        invoice.setSubtotal(BigDecimal.TEN);
        invoice.setTaxTotal(BigDecimal.ZERO);
        invoice.setGrandTotal(BigDecimal.TEN);
        invoice.setTaxTreatment("INTRA_STATE");
        invoice.setDueDate(java.time.LocalDate.now().plusDays(30));
        invoice.setIssuedAt(Instant.now());
        invoice.setCreatedBy(vendor.getId());
        invoice.setCreatedAt(Instant.now());
        invoices.save(invoice);

        ServiceRequest ticket = new ServiceRequest();
        ticket.setId(UUID.randomUUID());
        ticket.setTenantId(vendorTenantId);
        ticket.setCustomerId(customerB.profile().getId());
        ticket.setTicketNumber("CUSTOMER-B-SVC-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setCategory("GENERAL");
        ticket.setPriority("LOW");
        ticket.setDescription("Private customer B service ticket");
        ticket.setWarrantyStatus("UNKNOWN");
        ticket.setStatus("OPEN");
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        serviceRequests.save(ticket);

        var token = accessToken(customerA.user().getEmail(), "Customer-Password-2026");
        var ordersJson = mvc.perform(get("/api/v1/customer/product-orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(ordersJson.contains(order.getId().toString()));

        var invoicesJson = mvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(invoicesJson.contains(invoice.getId().toString()));
        mvc.perform(get("/api/v1/invoices/" + invoice.getId() + "/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invoices/" + invoice.getId() + "/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        var ticketsJson = mvc.perform(get("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(ticketsJson.contains(ticket.getId().toString()));
        mvc.perform(get("/api/v1/service-requests/" + ticket.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void vendorBranchEndpointsCannotReadOrMutateAnotherTenantsBranch() throws Exception {
        var foreignTenant = newTenant();
        Branch foreignBranch = new Branch();
        foreignBranch.setId(UUID.randomUUID());
        foreignBranch.setTenantId(foreignTenant.getId());
        foreignBranch.setCode("FOREIGN-" + UUID.randomUUID().toString().substring(0, 8));
        foreignBranch.setName("Foreign branch");
        foreignBranch.setActive(true);
        foreignBranch.setCreatedAt(Instant.now());
        foreignBranch.setUpdatedAt(Instant.now());
        branches.save(foreignBranch);

        var token = accessToken("admin@saumya.local", "Admin@123");
        mvc.perform(get("/api/v1/branches/" + foreignBranch.getId() + "/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/branches/" + foreignBranch.getId() + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/branches/" + foreignBranch.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TAMPERED","name":"Attempted foreign update"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/branches/" + foreignBranch.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/branches/" + foreignBranch.getId() + "/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(users.findByEmailIgnoreCase("sales@saumya.local").orElseThrow().getId())))
                .andExpect(status().isNotFound());
    }


    @Test
    void vendorCannotReadForeignSurveyOrDownloadForeignServiceEvidence() throws Exception {
        var foreignTenant = newTenant();
        var foreignCustomer = createCustomer(foreignTenant.getId(), "foreign-evidence-customer");
        ServiceRequest foreignTicket = new ServiceRequest();
        foreignTicket.setId(UUID.randomUUID());
        foreignTicket.setTenantId(foreignTenant.getId());
        foreignTicket.setTicketNumber("FOREIGN-EVIDENCE-" + UUID.randomUUID().toString().substring(0, 8));
        foreignTicket.setCustomerId(foreignCustomer.profile().getId());
        foreignTicket.setCategory("GENERAL");
        foreignTicket.setPriority("LOW");
        foreignTicket.setDescription("Foreign tenant private evidence");
        foreignTicket.setWarrantyStatus("UNKNOWN");
        foreignTicket.setStatus("OPEN");
        foreignTicket.setCreatedAt(Instant.now());
        foreignTicket.setUpdatedAt(Instant.now());
        serviceRequests.save(foreignTicket);

        SiteSurvey foreignSurvey = new SiteSurvey();
        foreignSurvey.setId(UUID.randomUUID());
        foreignSurvey.setTenantId(foreignTenant.getId());
        foreignSurvey.setScheduledAt(Instant.now().plusSeconds(3600));
        foreignSurvey.setPropertyType("RESIDENTIAL");
        foreignSurvey.setRoofType("RCC");
        foreignSurvey.setStatus("SCHEDULED");
        surveys.save(foreignSurvey);

        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
        var reportBefore = mvc.perform(get("/api/v1/reports/operations")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long serviceTicketsBefore = json.readTree(reportBefore).get("serviceTickets").asLong();
        mvc.perform(get("/api/v1/reports/operations/export.csv")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("serviceTickets," + serviceTicketsBefore + "\n")));
        mvc.perform(get("/api/v1/platform/tenants/export")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/service-requests/" + foreignTicket.getId() + "/attachments")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-requests/" + foreignTicket.getId() + "/attachments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/surveys/" + foreignSurvey.getId())
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/surveys/" + foreignSurvey.getId() + "/attachments")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());
    }

    private CustomerFixture createCustomer(UUID tenantId, String prefix) {
        String password = "Customer-Password-2026";
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setName(prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAccountType("CUSTOMER");
        user.setEnabled(true);
        user.getRoles().add(roles.findByNameAndTenantIdIsNull("CUSTOMER").orElseThrow());
        users.save(user);

        CustomerProfile profile = new CustomerProfile();
        profile.setId(UUID.randomUUID());
        profile.setTenantId(tenantId);
        profile.setUserId(user.getId());
        profile.setName(prefix);
        profile.setEmail(user.getEmail());
        profile.setActive(true);
        customerProfiles.save(profile);
        return new CustomerFixture(user, profile);
    }

    private CustomerAddress createAddress(UUID customerId, UUID tenantId) {
        CustomerAddress address = new CustomerAddress();
        address.setId(UUID.randomUUID());
        address.setTenantId(tenantId);
        address.setCustomerId(customerId);
        address.setLabel("Home");
        address.setLine1("1 Test Road");
        address.setCity("Raipur");
        address.setState("Chhattisgarh");
        address.setPincode("492001");
        address.setCountry("India");
        address.setDefault(true);
        address.setCreatedAt(Instant.now());
        address.setUpdatedAt(Instant.now());
        return customerAddresses.save(address);
    }

    private String addressBody() {
        return """
                {"label":"Updated","line1":"2 Test Road","city":"Raipur","state":"Chhattisgarh","pincode":"492001"}
                """;
    }

    private record CustomerFixture(AppUser user, CustomerProfile profile) {
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
    void warrantyClaimAttachmentHttpUploadAndDownloadAreBoundToTenantAndParentClaim() throws Exception {
        var customerA = createCustomer(vendorTenantId, "attachment-customer-a");
        var customerB = createCustomer(vendorTenantId, "attachment-customer-b");
        var foreignTenant = newTenant();
        var foreignCustomer = createCustomer(foreignTenant.getId(), "attachment-foreign-customer");

        Warranty warrantyA = new Warranty();
        warrantyA.setId(UUID.randomUUID());
        warrantyA.setTenantId(vendorTenantId);
        warrantyA.setCustomerId(customerA.profile().getId());
        warrantyA.setStatus("ACTIVE");
        warrantyA.setStartDate(java.time.LocalDate.now().minusDays(1));
        warrantyA.setEndDate(java.time.LocalDate.now().plusDays(30));
        warranties.save(warrantyA);

        Warranty warrantyB = new Warranty();
        warrantyB.setId(UUID.randomUUID());
        warrantyB.setTenantId(vendorTenantId);
        warrantyB.setCustomerId(customerB.profile().getId());
        warrantyB.setStatus("ACTIVE");
        warrantyB.setStartDate(java.time.LocalDate.now().minusDays(1));
        warrantyB.setEndDate(java.time.LocalDate.now().plusDays(30));
        warranties.save(warrantyB);

        Warranty foreignWarranty = new Warranty();
        foreignWarranty.setId(UUID.randomUUID());
        foreignWarranty.setTenantId(foreignTenant.getId());
        foreignWarranty.setCustomerId(foreignCustomer.profile().getId());
        foreignWarranty.setStatus("ACTIVE");
        foreignWarranty.setStartDate(java.time.LocalDate.now().minusDays(1));
        foreignWarranty.setEndDate(java.time.LocalDate.now().plusDays(30));
        warranties.save(foreignWarranty);

        WarrantyClaim claimA = warrantyClaim(vendorTenantId, customerA.profile().getId(), warrantyA.getId());
        WarrantyClaim claimB = warrantyClaim(vendorTenantId, customerB.profile().getId(), warrantyB.getId());
        WarrantyClaim foreignClaim = warrantyClaim(foreignTenant.getId(), foreignCustomer.profile().getId(), foreignWarranty.getId());
        warrantyClaims.saveAllAndFlush(List.of(claimA, claimB, foreignClaim));

        String vendorToken = accessToken("admin@saumya.local", "Admin@123");
        byte[] pdf = "%PDF-1.7\nclaim evidence\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile evidence = new MockMultipartFile("file", "claim-evidence.pdf", "application/pdf", pdf);
        String uploaded = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/warranty-claims/" + claimA.getId() + "/attachments")
                        .file(evidence)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(vendorTenantId.toString()))
                .andExpect(jsonPath("$.claimId").value(claimA.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID attachmentId = UUID.fromString(json.readTree(uploaded).get("id").asText());

        mvc.perform(get("/api/v1/warranty-claims/" + claimA.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", org.hamcrest.Matchers.containsString("claim-evidence.pdf")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf));

        mvc.perform(get("/api/v1/warranty-claims/" + claimB.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/warranty-claims/" + foreignClaim.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/warranty-claims/" + foreignClaim.getId() + "/attachments")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());

        WarrantyClaimAttachment stored = warrantyClaimAttachments.findByIdAndTenantIdAndClaimId(
                attachmentId, vendorTenantId, claimA.getId()).orElseThrow();
        assertTrue(stored.getStorageKey().startsWith(vendorTenantId + "/warranty-claims/"));

        // Customer HTTP access is restricted to the customer's own claim, even
        // when the caller knows another customer's claim and attachment UUIDs.
        String customerAToken = accessToken(customerA.user().getEmail(), "Customer-Password-2026");
        String customerBToken = accessToken(customerB.user().getEmail(), "Customer-Password-2026");
        mvc.perform(get("/api/v1/warranty-claims/" + claimA.getId() + "/attachments")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachmentId.toString()));
        mvc.perform(get("/api/v1/warranty-claims/" + claimA.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/warranty-claims/" + claimB.getId() + "/attachments")
                        .file(evidence)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/warranty-claims/" + claimB.getId() + "/attachments")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/warranty-claims/" + claimA.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + customerBToken))
                .andExpect(status().isNotFound());

        // Vendor access must not permit an upload to a foreign tenant's claim.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/warranty-claims/" + foreignClaim.getId() + "/attachments")
                        .file(evidence)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isNotFound());

        // Reject unsupported media types at the HTTP boundary before storage.
        MockMultipartFile unsupported = new MockMultipartFile(
                "file", "claim-evidence.txt", "text/plain", "not evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/warranty-claims/" + claimA.getId() + "/attachments")
                        .file(unsupported)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isBadRequest());
    }

    private WarrantyClaim warrantyClaim(UUID tenantId, UUID customerId, UUID warrantyId) {
        WarrantyClaim claim = new WarrantyClaim();
        claim.setId(UUID.randomUUID());
        claim.setTenantId(tenantId);
        claim.setWarrantyId(warrantyId);
        claim.setCustomerId(customerId);
        claim.setClaimNumber("HTTP-CLAIM-" + UUID.randomUUID().toString().substring(0, 8));
        claim.setSubject("Controller upload test");
        claim.setDescription("Private evidence controller integration test");
        claim.setStatus("SUBMITTED");
        claim.setCreatedAt(Instant.now());
        claim.setUpdatedAt(Instant.now());
        return claim;
    }

    @org.springframework.transaction.annotation.Transactional
    @Test
    void branchAssignedMemberListsOnlyAssignedBranchEnquiriesLeadsSurveysQuotationsAndOrders() throws Exception {
        Branch branchA = branch(vendorTenantId, "BR-A-" + UUID.randomUUID().toString().substring(0, 6));
        Branch branchB = branch(vendorTenantId, "BR-B-" + UUID.randomUUID().toString().substring(0, 6));
        branches.saveAllAndFlush(List.of(branchA, branchB));

        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(vendorTenantId);
        role.setName("BRANCH_SCOPE_TEST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        for (String code : List.of("LEAD_VIEW", "SURVEY_VIEW", "QUOTATION_VIEW", "ORDER_VIEW", "BRANCH_MANAGE", "INVOICE_VIEW", "SERVICE_VIEW", "SERVICE_ASSIGN", "INSTALLATION_VIEW", "INSTALLATION_ASSIGN", "INVENTORY_VIEW")) {
            Permission permission = permissions.findByCode(code).orElseThrow();
            role.getPermissions().add(permission);
        }
        roles.saveAndFlush(role);

        AppUser member = new AppUser();
        member.setId(UUID.randomUUID());
        member.setTenantId(vendorTenantId);
        member.setName("Branch Assigned Member");
        member.setEmail("branch-member-" + UUID.randomUUID() + "@example.test");
        member.setPasswordHash(passwordEncoder.encode("Branch-Member-Password-2026"));
        member.setAccountType("VENDOR");
        member.setEnabled(true);
        member.getRoles().add(role);
        users.saveAndFlush(member);

        BranchMembership membership = new BranchMembership();
        membership.setId(UUID.randomUUID());
        membership.setTenantId(vendorTenantId);
        membership.setBranchId(branchA.getId());
        membership.setUserId(member.getId());
        membership.setCreatedAt(Instant.now());
        branchMemberships.saveAndFlush(membership);

        Enquiry enquiryA = enquiry(vendorTenantId, branchA.getId(), "A");
        Enquiry enquiryB = enquiry(vendorTenantId, branchB.getId(), "B");
        enquiryRecords.saveAllAndFlush(List.of(enquiryA, enquiryB));

        SalesLead leadA = lead(vendorTenantId, branchA.getId(), member.getId(), enquiryA.getId());
        SalesLead leadB = lead(vendorTenantId, branchB.getId(), member.getId(), enquiryB.getId());
        salesLeadRecords.saveAllAndFlush(List.of(leadA, leadB));

        SiteSurvey surveyA = survey(vendorTenantId, branchA.getId(), member.getId(), leadA.getId());
        SiteSurvey surveyB = survey(vendorTenantId, branchB.getId(), member.getId(), leadB.getId());
        surveys.saveAllAndFlush(List.of(surveyA, surveyB));

        var branchCustomer = createCustomer(vendorTenantId, "branch-scope-customer");
        Quotation quoteA = quotation(vendorTenantId, branchA.getId(), member.getId(), leadA.getId(), branchCustomer.profile().getId());
        Quotation quoteB = quotation(vendorTenantId, branchB.getId(), member.getId(), leadB.getId(), branchCustomer.profile().getId());
        quotations.saveAllAndFlush(List.of(quoteA, quoteB));

        CustomerOrder orderA = order(vendorTenantId, branchA.getId(), quoteA.getId(), branchCustomer.profile().getId());
        CustomerOrder orderB = order(vendorTenantId, branchB.getId(), quoteB.getId(), branchCustomer.profile().getId());
        customerOrders.saveAllAndFlush(List.of(orderA, orderB));

        String token = accessToken(member.getEmail(), "Branch-Member-Password-2026");
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/enquiries?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), enquiryA.getId(), enquiryB.getId());
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/leads?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), leadA.getId(), leadB.getId());
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/surveys?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), surveyA.getId(), surveyB.getId());
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/quotations?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), quoteA.getId(), quoteB.getId());
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/orders?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), orderA.getId(), orderB.getId());

        Invoice invoiceA = branchInvoice(vendorTenantId, branchA.getId(), orderA.getId(), member.getId());
        Invoice invoiceB = branchInvoice(vendorTenantId, branchB.getId(), orderB.getId(), member.getId());
        invoices.saveAllAndFlush(List.of(invoiceA, invoiceB));
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/invoices?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), invoiceA.getId(), invoiceB.getId());

        ServiceRequest ticketA = branchTicket(vendorTenantId, branchA.getId(), branchCustomer.profile().getId());
        ServiceRequest ticketB = branchTicket(vendorTenantId, branchB.getId(), branchCustomer.profile().getId());
        serviceRequests.saveAllAndFlush(List.of(ticketA, ticketB));
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/service-requests?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ticketA.getId(), ticketB.getId());

        InstallationJob installationA = branchInstallation(vendorTenantId, branchA.getId(), orderA.getId(), branchCustomer.profile().getId(), member.getId());
        InstallationJob installationB = branchInstallation(vendorTenantId, branchB.getId(), orderB.getId(), branchCustomer.profile().getId(), member.getId());
        installationJobs.saveAllAndFlush(List.of(installationA, installationB));
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/installations?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), installationA.getId(), installationB.getId());

        mvc.perform(get("/api/v1/branches/" + branchA.getId() + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/branches/" + branchB.getId() + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        ProductCategory stockCategory = new ProductCategory();
        stockCategory.setId(UUID.randomUUID());
        stockCategory.setTenantId(vendorTenantId);
        stockCategory.setName("Branch inventory category " + UUID.randomUUID());
        stockCategory.setActive(true);
        productCategories.saveAndFlush(stockCategory);
        Product stockProduct = new Product();
        stockProduct.setId(UUID.randomUUID());
        stockProduct.setTenantId(vendorTenantId);
        stockProduct.setCategoryId(stockCategory.getId());
        stockProduct.setSku("BR-STOCK-" + UUID.randomUUID().toString().substring(0, 8));
        stockProduct.setName("Branch stock product");
        stockProduct.setPrice(BigDecimal.ZERO);
        stockProduct.setPurchasePrice(BigDecimal.ZERO);
        stockProduct.setDiscountPercent(BigDecimal.ZERO);
        stockProduct.setTaxPercent(BigDecimal.ZERO);
        stockProduct.setReorderLevel(BigDecimal.ZERO);
        stockProduct.setWarrantyYears(1);
        stockProduct.setUnitOfMeasure("PCS");
        stockProduct.setActive(true);
        productRecords.saveAndFlush(stockProduct);

        Warehouse warehouseA = branchWarehouse(vendorTenantId, branchA.getId(), "A");
        Warehouse warehouseB = branchWarehouse(vendorTenantId, branchB.getId(), "B");
        warehouseRecords.saveAllAndFlush(List.of(warehouseA, warehouseB));
        InventoryBalance balanceA = branchBalance(vendorTenantId, warehouseA.getId(), stockProduct.getId());
        InventoryBalance balanceB = branchBalance(vendorTenantId, warehouseB.getId(), stockProduct.getId());
        inventoryBalances.saveAllAndFlush(List.of(balanceA, balanceB));
        InventoryTransaction movementA = branchMovement(vendorTenantId, warehouseA.getId(), stockProduct.getId(), member.getId());
        InventoryTransaction movementB = branchMovement(vendorTenantId, warehouseB.getId(), stockProduct.getId(), member.getId());
        inventoryTransactions.saveAllAndFlush(List.of(movementA, movementB));
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/warehouses?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), warehouseA.getId(), warehouseB.getId());
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/inventory/balances?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), balanceA.getId(), balanceB.getId());
        assertBranchListContainsOnly(mvc.perform(get("/api/v1/inventory/transactions?branchId=" + branchB.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), movementA.getId(), movementB.getId());
    }

    private Branch branch(UUID tenantId, String code) {
        Branch branch = new Branch();
        branch.setId(UUID.randomUUID());
        branch.setTenantId(tenantId);
        branch.setCode(code);
        branch.setName(code);
        branch.setActive(true);
        branch.setCreatedAt(Instant.now());
        branch.setUpdatedAt(Instant.now());
        return branch;
    }

    private Enquiry enquiry(UUID tenantId, UUID branchId, String suffix) {
        Enquiry row = new Enquiry();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setBranchId(branchId);
        row.setName("Branch enquiry " + suffix);
        row.setEmail("branch-" + suffix + "-" + UUID.randomUUID() + "@example.test");
        row.setPhone("900000000" + suffix);
        row.setPropertyType("RESIDENTIAL");
        row.setStatus("NEW");
        row.setCreatedAt(Instant.now());
        return row;
    }

    private SalesLead lead(UUID tenantId, UUID branchId, UUID assignedUserId, UUID enquiryId) {
        SalesLead row = new SalesLead();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setBranchId(branchId);
        row.setEnquiryId(enquiryId);
        row.setAssignedUserId(assignedUserId);
        row.setStage("NEW");
        row.setPriority("MEDIUM");
        row.setCreatedAt(Instant.now());
        return row;
    }

    private SiteSurvey survey(UUID tenantId, UUID branchId, UUID assignedUserId, UUID leadId) {
        SiteSurvey row = new SiteSurvey();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setBranchId(branchId);
        row.setLeadId(leadId);
        row.setAssignedUserId(assignedUserId);
        row.setScheduledAt(Instant.now().plusSeconds(3600));
        row.setStatus("SCHEDULED");
        return row;
    }

    private Quotation quotation(UUID tenantId, UUID branchId, UUID createdBy, UUID leadId, UUID customerId) {
        Quotation row = new Quotation();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setBranchId(branchId);
        row.setQuotationNumber("BR-Q-" + UUID.randomUUID().toString().substring(0, 8));
        row.setRevision(1);
        row.setLeadId(leadId);
        row.setCustomerId(customerId);
        row.setCreatedBy(createdBy);
        row.setStatus("DRAFT");
        row.setSubtotal(BigDecimal.ZERO);
        row.setDiscountPercent(BigDecimal.ZERO);
        row.setDiscountAmount(BigDecimal.ZERO);
        row.setTaxAmount(BigDecimal.ZERO);
        row.setGrandTotal(BigDecimal.ZERO);
        row.setCreatedAt(Instant.now());
        return row;
    }

    private CustomerOrder order(UUID tenantId, UUID branchId, UUID quotationId, UUID customerId) {
        CustomerOrder row = new CustomerOrder();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setBranchId(branchId);
        row.setQuotationId(quotationId);
        row.setCustomerId(customerId);
        row.setOrderNumber("BR-O-" + UUID.randomUUID().toString().substring(0, 8));
        row.setStatus("DRAFT");
        row.setSubtotal(BigDecimal.ZERO);
        row.setDiscountPercent(BigDecimal.ZERO);
        row.setDiscountAmount(BigDecimal.ZERO);
        row.setTaxTotal(BigDecimal.ZERO);
        row.setTotal(BigDecimal.ZERO);
        row.setCreatedAt(Instant.now());
        return row;
    }

    private Warehouse branchWarehouse(UUID tenantId, UUID branchId, String suffix) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setTenantId(tenantId);
        warehouse.setBranchId(branchId);
        warehouse.setCode("BR-WH-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6));
        warehouse.setName("Branch warehouse " + suffix);
        warehouse.setActive(true);
        warehouse.setCreatedAt(Instant.now());
        return warehouse;
    }

    private InventoryBalance branchBalance(UUID tenantId, UUID warehouseId, UUID productId) {
        InventoryBalance balance = new InventoryBalance();
        balance.setId(UUID.randomUUID());
        balance.setTenantId(tenantId);
        balance.setWarehouseId(warehouseId);
        balance.setProductId(productId);
        balance.setOnHand(BigDecimal.TEN);
        balance.setReserved(BigDecimal.ZERO);
        balance.setDamaged(BigDecimal.ZERO);
        return balance;
    }

    private InventoryTransaction branchMovement(UUID tenantId, UUID warehouseId, UUID productId, UUID actorId) {
        InventoryTransaction movement = new InventoryTransaction();
        movement.setId(UUID.randomUUID());
        movement.setTenantId(tenantId);
        movement.setWarehouseId(warehouseId);
        movement.setProductId(productId);
        movement.setType("RECEIPT");
        movement.setQuantityDelta(BigDecimal.TEN);
        movement.setReservedDelta(BigDecimal.ZERO);
        movement.setDamagedDelta(BigDecimal.ZERO);
        movement.setReason("Branch isolation integration fixture");
        movement.setActorId(actorId);
        movement.setCreatedAt(Instant.now());
        return movement;
    }

    private Invoice branchInvoice(UUID tenantId, UUID branchId, UUID orderId, UUID actorId) {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantId);
        invoice.setBranchId(branchId);
        invoice.setOrderId(orderId);
        invoice.setInvoiceNumber("BR-I-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setFiscalYear("2026-27");
        invoice.setStatus("ISSUED");
        invoice.setCustomerName("Branch scope customer");
        invoice.setVendorName("Branch scope vendor");
        invoice.setCurrency("INR");
        invoice.setSubtotal(BigDecimal.ZERO);
        invoice.setTaxTotal(BigDecimal.ZERO);
        invoice.setGrandTotal(BigDecimal.ZERO);
        invoice.setTaxTreatment("INTRA_STATE");
        invoice.setCreatedBy(actorId);
        invoice.setIssuedAt(Instant.now());
        invoice.setCreatedAt(Instant.now());
        return invoice;
    }

    private ServiceRequest branchTicket(UUID tenantId, UUID branchId, UUID customerId) {
        ServiceRequest ticket = new ServiceRequest();
        ticket.setId(UUID.randomUUID());
        ticket.setTenantId(tenantId);
        ticket.setBranchId(branchId);
        ticket.setCustomerId(customerId);
        ticket.setTicketNumber("BR-S-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setCategory("INSTALLATION");
        ticket.setPriority("MEDIUM");
        ticket.setDescription("Branch scoped service request");
        ticket.setWarrantyStatus("UNKNOWN");
        ticket.setStatus("OPEN");
        ticket.setPausedSeconds(0);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        return ticket;
    }

    private InstallationJob branchInstallation(UUID tenantId, UUID branchId, UUID orderId, UUID customerId, UUID engineerId) {
        InstallationJob installation = new InstallationJob();
        installation.setId(UUID.randomUUID());
        installation.setTenantId(tenantId);
        installation.setBranchId(branchId);
        installation.setOrderId(orderId);
        installation.setCustomerId(customerId);
        installation.setSiteAddress("Branch installation test");
        installation.setScheduledAt(Instant.now().plusSeconds(7200));
        installation.setAssignedEngineerId(engineerId);
        installation.setStatus("SCHEDULED");
        installation.setCreatedAt(Instant.now());
        return installation;
    }

    private void assertBranchListContainsOnly(String response, UUID expectedId, UUID excludedId) throws Exception {
        JsonNode rows = json.readTree(response);
        assertTrue(rows.isArray());
        assertTrue(java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .anyMatch(row -> expectedId.toString().equals(row.path("id").asText())));
        assertFalse(response.contains(excludedId.toString()));
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
        
        var vendorToken = accessToken("admin@saumya.local", "Admin@123");
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
