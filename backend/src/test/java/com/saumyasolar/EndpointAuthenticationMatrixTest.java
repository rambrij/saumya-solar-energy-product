package com.saumyasolar;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Role;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.RoleRepository;
import com.saumyasolar.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
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
class EndpointAuthenticationMatrixTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwordEncoder;

    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/", "/api/v1/public/", "/api/v1/payment-webhooks/",
            "/api/v1/payment-gateway-webhooks/", "/uploads/", "/swagger-ui/", "/v3/api-docs/"
    );
    private static final Set<String> PUBLIC_EXACT = Set.of("/actuator/health", "/v3/api-docs");

    @Test
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
        assertTrue(checked.size() >= 200,
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
                "/api/v1/me", "/api/v1/subscription/usage", "/api/v1/subscription/storage");
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
        assertTrue(checked.size() >= 70,
                "The authenticated read matrix unexpectedly shrank: only checked " + checked.size());
        assertTrue(failures.isEmpty(),
                "Least-privilege authenticated reads were exposed:\\n" + String.join("\\n", failures));
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
