package com.saumyasolar;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Role;
import com.saumyasolar.domain.Quotation;
import com.saumyasolar.domain.Enquiry;
import com.saumyasolar.domain.SalesLead;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.RoleRepository;
import com.saumyasolar.repo.PermissionRepository;
import com.saumyasolar.repo.QuotationRepository;
import com.saumyasolar.repo.EnquiryRepository;
import com.saumyasolar.repo.SalesLeadRepository;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.web.method.HandlerMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Inventory-driven authentication gate for every concrete Spring MVC handler mapping.
 * Tenant authorization still has resource-specific integration tests; this test prevents
 * an endpoint from accidentally becoming anonymously callable as the API grows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EndpointAuthenticationMatrixTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired QuotationRepository quotations;
    @Autowired EnquiryRepository enquiries;
    @Autowired SalesLeadRepository leads;
    @Autowired CustomerProfileRepository customers;
    private UUID revisionFixtureId;
    private UUID acceptedQuotationFixtureId;
    private UUID enquiryFixtureId;
    private UUID leadFixtureId;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwordEncoder;

    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/", "/api/v1/public/", "/api/v1/payment-webhooks/",
            "/api/v1/payment-gateway-webhooks/", "/uploads/", "/swagger-ui/", "/v3/api-docs/"
    );
    private static final Set<String> PUBLIC_EXACT = Set.of("/actuator/health", "/v3/api-docs");

    @Test
    @Transactional
    void everyNonPublicHttpHandlerRequiresAuthentication() throws Exception {
        List<String> checked = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) continue;
            for (String pattern : patterns) {
                if (isPublic(pattern)) continue;
                String path = probePath(pattern);
                for (var method : methods) {
                    if (method == org.springframework.web.bind.annotation.RequestMethod.OPTIONS) continue;
                    HttpMethod httpMethod = HttpMethod.valueOf(method.name());
                    MockHttpServletRequestBuilder request = request(httpMethod, path);
                    int status = mvc.perform(request).andReturn().getResponse().getStatus();
                    checked.add(method + " " + pattern);
                    if (status != 401 && status != 403) {
                        failures.add(method + " " + pattern + " returned " + status
                                + " (expected 401/403 without credentials)");
                    }
                }
            }
        }
        assertTrue(checked.size() >= 207,
                "The controller mapping inventory unexpectedly shrank: only checked " + checked.size());
        assertTrue(checked.contains("POST /api/v1/exports"),
                "Export creation must be included in the authentication matrix");
        assertTrue(checked.contains("GET /api/v1/exports/{exportId}"),
                "Export status must be included in the authentication matrix");
        assertTrue(checked.contains("GET /api/v1/exports/{exportId}/download"),
                "Export download must be included in the authentication matrix");
        assertTrue(failures.isEmpty(), "Unauthenticated endpoint exposure:\\n" + String.join("\\n", failures));
    }



    @Test
    @Transactional
    void applicableAccountWithAllCataloguedGrantsPassesEveryProtectedRoutePermissionGate() throws Exception {
        AppUser seededVendor = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        Role allGrants = new Role();
        allGrants.setId(UUID.randomUUID());
        allGrants.setTenantId(seededVendor.getTenantId());
        allGrants.setName("ENDPOINT_MATRIX_ALL_GRANTS_" + UUID.randomUUID().toString().replace("-", ""));
        allGrants.getPermissions().addAll(permissions.findAll());
        roles.saveAndFlush(allGrants);

        AppUser vendor = new AppUser();
        vendor.setId(UUID.randomUUID());
        vendor.setTenantId(seededVendor.getTenantId());
        vendor.setName("Endpoint matrix all-grants vendor");
        vendor.setEmail("endpoint-all-grants-" + UUID.randomUUID() + "@example.test");
        vendor.setPasswordHash(passwordEncoder.encode("Endpoint-Matrix-Password-2026"));
        vendor.setAccountType("VENDOR");
        vendor.setEnabled(true);
        vendor.getRoles().add(allGrants);
        users.saveAndFlush(vendor);

        AppUser platform = users.findByEmailIgnoreCase("platform@saumya.local").orElseThrow();
        AppUser customer = users.findByEmailIgnoreCase("customer@saumya.local").orElseThrow();

        var customerProfile = customers.findByTenantIdAndUserId(seededVendor.getTenantId(), customer.getId())
                .orElseThrow(() -> new AssertionError("Seeded customer profile is required for positive route fixtures"));

        // Quotation revision and customer decision routes require real tenant-owned parents.
        Quotation revisionFixture = new Quotation();
        revisionFixture.setId(UUID.randomUUID());
        revisionFixture.setTenantId(seededVendor.getTenantId());
        revisionFixture.setQuoteGroupId(UUID.randomUUID());
        revisionFixture.setQuotationNumber("MATRIX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        revisionFixture.setRevision(1);
        revisionFixture.setStatus("SENT");
        revisionFixture.setCustomerId(customerProfile.getId());
        revisionFixture.setValidUntil(LocalDate.now().plusDays(30));
        revisionFixture.setSubtotal(BigDecimal.ONE);
        revisionFixture.setDiscountPercent(BigDecimal.ZERO);
        revisionFixture.setDiscountAmount(BigDecimal.ZERO);
        revisionFixture.setTaxAmount(BigDecimal.ZERO);
        revisionFixture.setGrandTotal(BigDecimal.ONE);
        revisionFixture.setCreatedBy(vendor.getId());
        revisionFixture.setCreatedAt(Instant.now());
        quotations.saveAndFlush(revisionFixture);
        revisionFixtureId = revisionFixture.getId();

        Quotation acceptedQuotationFixture = new Quotation();
        acceptedQuotationFixture.setId(UUID.randomUUID());
        acceptedQuotationFixture.setTenantId(seededVendor.getTenantId());
        acceptedQuotationFixture.setQuoteGroupId(UUID.randomUUID());
        acceptedQuotationFixture.setQuotationNumber("MATRIX-ACCEPTED-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        acceptedQuotationFixture.setRevision(1);
        acceptedQuotationFixture.setStatus("ACCEPTED");
        acceptedQuotationFixture.setCustomerId(customerProfile.getId());
        acceptedQuotationFixture.setSubtotal(BigDecimal.ONE);
        acceptedQuotationFixture.setDiscountPercent(BigDecimal.ZERO);
        acceptedQuotationFixture.setDiscountAmount(BigDecimal.ZERO);
        acceptedQuotationFixture.setTaxAmount(BigDecimal.ZERO);
        acceptedQuotationFixture.setGrandTotal(BigDecimal.ONE);
        acceptedQuotationFixture.setCreatedBy(vendor.getId());
        acceptedQuotationFixture.setCreatedAt(Instant.now());
        quotations.saveAndFlush(acceptedQuotationFixture);
        acceptedQuotationFixtureId = acceptedQuotationFixture.getId();

        Enquiry enquiryFixture = new Enquiry();
        enquiryFixture.setId(UUID.randomUUID());
        enquiryFixture.setTenantId(seededVendor.getTenantId());
        enquiryFixture.setName("Endpoint matrix enquiry");
        enquiryFixture.setEmail("matrix-enquiry-" + UUID.randomUUID() + "@example.test");
        enquiryFixture.setPhone("9000000000");
        enquiryFixture.setPropertyType("RESIDENTIAL");
        enquiryFixture.setStatus("NEW");
        enquiryFixture.setCreatedAt(Instant.now());
        enquiries.saveAndFlush(enquiryFixture);
        enquiryFixtureId = enquiryFixture.getId();

        SalesLead leadFixture = new SalesLead();
        leadFixture.setId(UUID.randomUUID());
        leadFixture.setTenantId(seededVendor.getTenantId());
        leadFixture.setEnquiryId(enquiryFixture.getId());
        leadFixture.setStage("NEW");
        leadFixture.setPriority("MEDIUM");
        leadFixture.setCreatedAt(Instant.now());
        leads.saveAndFlush(leadFixture);
        leadFixtureId = leadFixture.getId();

        Map<String, String> tokens = Map.of(
                "VENDOR", "Bearer " + jwt.issue(vendor),
                "PLATFORM", "Bearer " + jwt.issue(platform),
                "CUSTOMER", "Bearer " + jwt.issue(customer));

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<String> checked = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) continue;
            for (String pattern : info.getPatternValues()) {
                if (isPublic(pattern)) continue;
                String accountType = applicableAccountType(pattern);
                String token = tokens.get(accountType);
                String path = positiveProbePath(pattern, handler, accountType);
                for (var method : methods) {
                    if (method == org.springframework.web.bind.annotation.RequestMethod.OPTIONS
                            || method == org.springframework.web.bind.annotation.RequestMethod.HEAD) continue;
                    MockHttpServletRequestBuilder builder = mutationRequest(method, path, handler, mapper);
                    var probeResult = mvc.perform(builder.header("Authorization", token)).andReturn();
                    int response = probeResult.getResponse().getStatus();
                    String responseBody = probeResult.getResponse().getContentAsString();
                    checked.add(method + " " + pattern + " [" + accountType + "]");
                    // A missing synthetic resource (404), invalid-but-authorized sample (400/422),
                    // or valid business conflict (409/410) is acceptable here: the objective is
                    // to prove the correct account type and a role carrying the complete permission
                    // catalogue passed the authorization gate. Real resource ownership remains
                    // covered by the fixture-backed tests in Release12TenantIntegrationTest.
                    if (response == 401 || response == 403 || response >= 500) {
                        failures.add(method + " " + pattern + " [" + accountType + "] returned "
                                + response + " for the all-grants actor; response=" + responseBody);
                    }
                }
            }
        }
        assertTrue(checked.size() >= 207,
                "The positive authorization-gate matrix unexpectedly shrank: only checked " + checked.size());
        assertTrue(failures.isEmpty(),
                "Applicable all-grants actors were denied or reached a server error:\n" + String.join("\n", failures));
    }

    private String applicableAccountType(String pattern) {
        if (pattern.startsWith("/api/v1/platform/") || pattern.equals("/api/v1/tenants")
                || pattern.startsWith("/api/v1/tenants/")) return "PLATFORM";
        if (pattern.startsWith("/api/v1/customer/")
                || pattern.equals("/api/v1/service-requests/{id}/confirm")
                || pattern.contains("/quotations/{id}/{action:")) return "CUSTOMER";
        return "VENDOR";
    }

    private String positiveProbePath(String pattern, HandlerMethod handler, String accountType) {
        String path = probePath(pattern, handler);
        // The first regex alternative is the platform-only tenants route. For a vendor
        // positive probe, select the tenant-scoped users collection instead.
        if ("VENDOR".equals(accountType) && pattern.startsWith("/api/v1/{type:")) {
            path = path.replaceFirst("^/api/v1/tenants$", "/api/v1/users");
        }
        if (pattern.contains("/quotations/") && pattern.contains("/revisions")) {
            path = path.replace("00000000-0000-4000-8000-000000000001", revisionFixtureId.toString());
        }
        if ("CUSTOMER".equals(accountType) && pattern.contains("/quotations/{id}/{action:")) {
            path = path.replace("00000000-0000-4000-8000-000000000001", revisionFixtureId.toString())
                    .replace("/approve", "/accept");
        }
        if ("VENDOR".equals(accountType) && pattern.contains("/quotations/") && pattern.contains("/convert-to-order")) {
            path = path.replace("00000000-0000-4000-8000-000000000001", acceptedQuotationFixtureId.toString());
        }
        if ("VENDOR".equals(accountType) && pattern.startsWith("/api/v1/enquiries/") && pattern.endsWith("/convert")) {
            path = path.replace("00000000-0000-4000-8000-000000000001", enquiryFixtureId.toString());
        }
        if ("VENDOR".equals(accountType) && pattern.startsWith("/api/v1/leads/{id}")) {
            path = path.replace("00000000-0000-4000-8000-000000000001", leadFixtureId.toString());
        }
        return path;
    }

    @Test
    @Transactional
    void authenticatedLeastPrivilegeVendorCannotReadProtectedEndpointMappings() throws Exception {
        AppUser seededVendor = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        Role emptyRole = new Role();
        emptyRole.setId(UUID.randomUUID());
        emptyRole.setTenantId(seededVendor.getTenantId());
        emptyRole.setName("ENDPOINT_MATRIX_NO_PERMISSION_" + UUID.randomUUID().toString().replace("-", ""));
        roles.saveAndFlush(emptyRole);

        AppUser leastPrivilege = new AppUser();
        leastPrivilege.setId(UUID.randomUUID());
        leastPrivilege.setTenantId(seededVendor.getTenantId());
        leastPrivilege.setName("Endpoint matrix least privilege");
        leastPrivilege.setEmail("endpoint-matrix-" + UUID.randomUUID() + "@example.test");
        leastPrivilege.setPasswordHash(passwordEncoder.encode("Endpoint-Matrix-Password-2026"));
        leastPrivilege.setAccountType("VENDOR");
        leastPrivilege.setEnabled(true);
        leastPrivilege.getRoles().add(emptyRole);
        users.saveAndFlush(leastPrivilege);
        String token = jwt.issue(leastPrivilege);

        Set<String> selfServiceReads = Set.of(
                "/api/v1/me",
                "/api/v1/subscription",
                "/api/v1/subscription-plans",
                "/api/v1/subscription/usage",
                "/api/v1/subscription/storage",
                "/api/v1/payment-gateways/availability",
                "/api/v1/offline-sync/operations",
                "/v3/api-docs.yaml");
        List<String> checked = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (!info.getMethodsCondition().getMethods().contains(
                    org.springframework.web.bind.annotation.RequestMethod.GET)) continue;
            for (String pattern : info.getPatternValues()) {
                if (isPublic(pattern) || selfServiceReads.contains(pattern)) continue;
                String path = probePath(pattern);
                int status = mvc.perform(request(HttpMethod.GET, path)
                                .header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getStatus();
                checked.add("GET " + pattern);
                if (status >= 200 && status < 300) {
                    failures.add("GET " + pattern + " returned " + status
                            + " for a vendor with no permissions");
                }
            }
        }
        assertTrue(checked.size() >= 83,
                "The authenticated read matrix unexpectedly shrank: only checked " + checked.size());
        assertTrue(failures.isEmpty(),
                "Least-privilege authenticated reads were exposed:\\n" + String.join("\\n", failures));
    }


    @Test
    @Transactional
    void authenticatedZeroPermissionVendorIsDeniedEveryProtectedMutationMapping() throws Exception {
        AppUser seededVendor = users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
        Role emptyRole = new Role();
        emptyRole.setId(UUID.randomUUID());
        emptyRole.setTenantId(seededVendor.getTenantId());
        emptyRole.setName("ENDPOINT_MATRIX_MUTATION_DENY_" + UUID.randomUUID().toString().replace("-", ""));
        roles.saveAndFlush(emptyRole);

        AppUser leastPrivilege = new AppUser();
        leastPrivilege.setId(UUID.randomUUID());
        leastPrivilege.setTenantId(seededVendor.getTenantId());
        leastPrivilege.setName("Endpoint matrix mutation denial");
        leastPrivilege.setEmail("endpoint-mutation-" + UUID.randomUUID() + "@example.test");
        leastPrivilege.setPasswordHash(passwordEncoder.encode("Endpoint-Matrix-Password-2026"));
        leastPrivilege.setAccountType("VENDOR");
        leastPrivilege.setEnabled(true);
        leastPrivilege.getRoles().add(emptyRole);
        users.saveAndFlush(leastPrivilege);
        String token = jwt.issue(leastPrivilege);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        List<String> checked = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int forbiddenResponses = 0;
        int nonDisclosingNotFoundResponses = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (isPublic(pattern)) continue;
                for (var method : methods) {
                    // These routes intentionally delegate authorization to an existing resource's
                    // owner/assignment policy; dedicated tests provide real resource fixtures.
                    if (pattern.startsWith("/api/v1/quotations/{id}/{action:")
                            || pattern.equals("/api/v1/offline-sync/operations")) continue;
                    if (method == org.springframework.web.bind.annotation.RequestMethod.GET
                            || method == org.springframework.web.bind.annotation.RequestMethod.OPTIONS
                            || method == org.springframework.web.bind.annotation.RequestMethod.HEAD) continue;
                    String path = probePath(pattern, handler);
                    MockHttpServletRequestBuilder builder = mutationRequest(method, path, handler, mapper);
                    builder.header("Authorization", "Bearer " + token);
                    int status = mvc.perform(builder).andReturn().getResponse().getStatus();
                    try {
                        UUID leakedTenant = com.saumyasolar.security.TenantExecutionContext.requireTenantId();
                        failures.add(method + " " + pattern + " leaked tenant context " + leakedTenant
                                + " after request completion");
                    } catch (IllegalStateException expectedNoContext) {
                        // Request-scoped tenant context must be cleared before the next request/test.
                    }
                    checked.add(method + " " + pattern);
                    if (status == 403) forbiddenResponses++;
                    else if (status == 404) nonDisclosingNotFoundResponses++;
                    if (status != 403 && status != 404) {
                        failures.add(method + " " + pattern + " returned " + status
                                + " (expected authorization denial 403 or non-disclosing 404)");
                    }
                }
            }
        }
        assertTrue(checked.size() >= 115,
                "The authenticated mutation matrix unexpectedly shrank: only checked " + checked.size());
        assertTrue(forbiddenResponses >= 50,
                "The zero-permission mutation sweep did not reach enough explicit permission gates: 403="
                        + forbiddenResponses + ", non-disclosing 404=" + nonDisclosingNotFoundResponses);
        assertTrue(failures.isEmpty(),
                "Authenticated vendor mutation endpoints did not deny a user with no permissions:\n"
                        + String.join("\n", failures));
    }

    private MockHttpServletRequestBuilder mutationRequest(
            org.springframework.web.bind.annotation.RequestMethod method,
            String path,
            HandlerMethod handler,
            ObjectMapper mapper
    ) throws Exception {
        boolean multipart = false;
        for (var parameter : handler.getMethodParameters()) {
            if (org.springframework.web.multipart.MultipartFile.class.isAssignableFrom(parameter.getParameterType())
                    || (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestPart.class)
                    && org.springframework.web.multipart.MultipartFile.class.isAssignableFrom(parameter.getParameterType()))) {
                multipart = true;
            }
        }
        MockHttpServletRequestBuilder builder;
        if (multipart) {
            var upload = new MockMultipartFile("file", "matrix.txt", "text/plain",
                    "authorization matrix probe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path).file(upload);
        } else {
            builder = request(HttpMethod.valueOf(method.name()), path);
        }

        for (var parameter : handler.getMethodParameters()) {
            var body = parameter.getParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class);
            if (body != null) {
                Object sample = sampleValue(parameter.getParameterType(), parameter.getGenericParameterType(),
                        parameter.getParameterName() == null ? "body" : parameter.getParameterName(),
                        parameter.getParameterAnnotations());
                builder.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(sample));
                continue;
            }
            var part = parameter.getParameterAnnotation(org.springframework.web.bind.annotation.RequestPart.class);
            if (part != null && !org.springframework.web.multipart.MultipartFile.class.isAssignableFrom(parameter.getParameterType())) {
                String name = !part.name().isBlank() ? part.name() : part.value();
                Object sample = sampleValue(parameter.getParameterType(), parameter.getGenericParameterType(),
                        name, parameter.getParameterAnnotations());
                if (builder instanceof org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartBuilder) {
                    multipartBuilder.part(new MockPart(name, mapper.writeValueAsBytes(sample)));
                }
                continue;
            }
            var requestHeader = parameter.getParameterAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
            if (requestHeader != null) {
                if (requestHeader.required() && requestHeader.defaultValue().equals(
                        org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE)) {
                    String name = !requestHeader.name().isBlank() ? requestHeader.name()
                            : !requestHeader.value().isBlank() ? requestHeader.value()
                            : (parameter.getParameterName() == null ? "X-Matrix-Probe" : parameter.getParameterName());
                    builder.header(name, name.equalsIgnoreCase("Idempotency-Key")
                            ? "authorization-matrix-" + UUID.randomUUID() : sampleString(name, parameter.getParameterAnnotations()));
                }
                continue;
            }
            if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.PathVariable.class)) continue;
            var requestParam = parameter.getParameterAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
            if (requestParam != null) {
                if (!requestParam.required() || !requestParam.defaultValue().equals(
                        org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE)) continue;
                String name = !requestParam.name().isBlank() ? requestParam.name()
                        : !requestParam.value().isBlank() ? requestParam.value()
                        : (parameter.getParameterName() == null ? "probe" : parameter.getParameterName());
                builder.param(name, sampleParameter(name, parameter.getParameterType(), parameter.getParameterAnnotations()));
            } else if (org.springframework.beans.BeanUtils.isSimpleProperty(parameter.getParameterType())
                    && parameter.getParameterName() != null) {
                builder.param(parameter.getParameterName(),
                        sampleParameter(parameter.getParameterName(), parameter.getParameterType(), parameter.getParameterAnnotations()));
            }
        }
        return builder;
    }

    private String probePath(String pattern, HandlerMethod handler) {
        Pattern variables = Pattern.compile("\\{([^}:]+)(?::([^}]+))?}");
        Matcher matcher = variables.matcher(pattern);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String expression = matcher.group(2);
            String value;
            if (expression != null) {
                if (expression.contains("|")) value = expression.split("\\|")[0];
                else if (expression.contains("\\d") || expression.contains("[0-9]")) value = "1";
                else value = "matrix";
            } else {
                value = pathVariableValue(name, handler);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        String path = result.toString().replace("**", "probe").replace("*", "probe");
        return path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
    }

    private String pathVariableValue(String name, HandlerMethod handler) {
        for (var parameter : handler.getMethodParameters()) {
            var annotation = parameter.getParameterAnnotation(org.springframework.web.bind.annotation.PathVariable.class);
            if (annotation == null) continue;
            String parameterName = !annotation.name().isBlank() ? annotation.name()
                    : !annotation.value().isBlank() ? annotation.value()
                    : parameter.getParameterName();
            if (name.equals(parameterName) && parameter.getParameterType() == String.class) {
                return sampleString(name, parameter.getParameterAnnotations());
            }
        }
        return "00000000-0000-4000-8000-000000000001";
    }

    private Object sampleValue(Class<?> type, Type genericType, String name, Annotation[] annotations) {
        if (type == String.class) return sampleString(name, annotations);
        if (type == UUID.class) return UUID.fromString("00000000-0000-4000-8000-000000000001");
        if (type == BigDecimal.class) return BigDecimal.ONE;
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class || type == Double.class || type == double.class
                || type == Float.class || type == float.class) return 1;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == Instant.class) return Instant.parse("2030-01-01T00:00:00Z");
        if (type == LocalDate.class) return LocalDate.of(2030, 1, 1);
        if (type == LocalDateTime.class) return LocalDateTime.of(2030, 1, 1, 10, 0);
        if (type == LocalTime.class) return LocalTime.of(10, 0);
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type == byte[].class) return new byte[] {1};
        if (type.isArray()) return new Object[] {sampleValue(type.getComponentType(), null, name, new Annotation[0])};
        if (Map.class.isAssignableFrom(type)) {
            if (genericType instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments().length == 2
                    && parameterized.getActualTypeArguments()[1] == Integer.class) {
                return Map.of("reopenDays", 7);
            }
            return Map.of("reason", "Authorization matrix probe");
        }
        if ("items".equals(name)) {
            return List.of(Map.of("productId", UUID.fromString("00000000-0000-4000-8000-000000000001"),
                    "quantity", 1, "optionalItem", false));
        }
        if ("memberIds".equals(name)) {
            // Request validation requires a non-empty UUID list; permission checks run before member lookup.
            return List.of(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        }
        if (Collection.class.isAssignableFrom(type)) {
            boolean requiredElements = hasAnnotation(annotations, "NotEmpty") || hasAnnotation(annotations, "NotNull");
            for (Annotation annotation : annotations) {
                if (annotation instanceof jakarta.validation.constraints.Size size && size.min() > 0) requiredElements = true;
            }
            if (!requiredElements) return List.of();
            Type elementType = genericType instanceof ParameterizedType parameterized
                    ? parameterized.getActualTypeArguments()[0] : Object.class;
            Class<?> elementClass = elementType instanceof Class<?> cls ? cls : Object.class;
            return List.of(sampleValue(elementClass, elementType, name, new Annotation[0]));
        }
        if (type.isRecord()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (var component : type.getRecordComponents()) {
                values.put(component.getName(), sampleValue(component.getType(), component.getGenericType(),
                        component.getName(), component.getAnnotations()));
            }
            return values;
        }
        if (type == Object.class) return Map.of();
        Map<String, Object> bean = new LinkedHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                    bean.putIfAbsent(field.getName(), sampleValue(field.getType(), field.getGenericType(),
                            field.getName(), field.getAnnotations()));
                } catch (RuntimeException ignored) {
                    // A DTO field that cannot be reflected remains absent; validation will expose the gap.
                }
            }
        }
        return bean.isEmpty() ? null : bean;
    }

    private boolean hasAnnotation(Annotation[] annotations, String simpleName) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private String sampleParameter(String name, Class<?> type, Annotation[] annotations) {
        if (type == Instant.class) return "2030-01-01T00:00:00Z";
        if (type == LocalDate.class) return "2030-01-01";
        if (type == LocalDateTime.class) return "2030-01-01T10:00:00";
        if (type == LocalTime.class) return "10:00:00";
        if (type == UUID.class) return "00000000-0000-4000-8000-000000000001";
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) return "1";
        if (type == Boolean.class || type == boolean.class) return "true";
        return sampleString(name, annotations);
    }

    private String sampleString(String name, Annotation[] annotations) {
        String key = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.validation.constraints.Pattern constraint) {
                String expression = constraint.regexp();
                if (expression.contains("|")) {
                    String candidate = expression.replace("^", "").replace("$", "").split("\\|")[0];
                    if (candidate.matches("[A-Za-z0-9_-]+")) return candidate;
                }
                if (expression.contains("\\d") || expression.contains("[0-9]")) return "9000000000";
            }
        }
        if (key.contains("color")) return "#112233";
        if (key.contains("visibility")) return "CUSTOMER";
        if (key.contains("email")) return "matrix@example.test";
        if (key.contains("password")) return "Matrix-Password-2026";
        if (key.contains("phone")) return "9000000000";
        if (key.contains("pincode") || key.contains("postal")) return "492001";
        if (key.contains("timezone")) return "Asia/Kolkata";
        if (key.contains("slug")) return "matrix-tenant";
        if (key.contains("domain")) return "matrix.example.test";
        if (key.contains("url") || key.contains("uri")) return "https://example.test/resource";
        if (key.contains("direction")) return "INCREASE";
        if (key.contains("decision")) return "ACCEPT";
        if (key.contains("priority")) return "LOW";
        if (key.contains("status")) return "ACTIVE";
        if (key.contains("method")) return "CASH";
        if (key.contains("format")) return "CSV";
        if (key.contains("reporttype")) return "OPERATIONS";
        if (key.contains("currency")) return "INR";
        if (key.contains("reason")) return "Authorization matrix probe";
        if (key.contains("description") || key.contains("notes") || key.contains("comment")) return "Authorization matrix probe";
        if (key.contains("name") || key.contains("title") || key.contains("label")) return "Matrix Test";
        if (key.contains("code") || key.contains("sku")) return "MATRIX-001";
        if (key.contains("reference") || key.contains("key")) return "MATRIX-001";
        if (key.contains("date")) return "2030-01-01";
        return "MATRIX";
    }

    private boolean isPublic(String pattern) {
        if (PUBLIC_EXACT.contains(pattern)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(pattern::startsWith);
    }

    private String probePath(String pattern) {
        String path = VARIABLE.matcher(pattern).replaceAll("00000000-0000-4000-8000-000000000001");
        path = path.replace("**", "probe").replace("*", "probe");
        if (path.contains("?")) path = path.substring(0, path.indexOf('?'));
        return path;
    }
}
