package com.saumyasolar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired RequestMappingHandlerMapping mappings;

    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/", "/api/v1/public/", "/api/v1/payment-webhooks/",
            "/api/v1/payment-gateway-webhooks/", "/uploads/", "/swagger-ui/", "/v3/api-docs/"
    );
    private static final Set<String> PUBLIC_EXACT = Set.of("/actuator/health");

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
        assertTrue(failures.isEmpty(), "Unauthenticated endpoint exposure:\\n" + String.join("\\n", failures));
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
