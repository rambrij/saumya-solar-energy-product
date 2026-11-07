package com.saumyasolar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.SolarPackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Release4AccessAndPackageTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired SolarPackageRepository packages;

    private String token(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    @Test
    void platformAdminCanCreateTenantAndNonPlatformCannot() throws Exception {
        String platform = token("platform@saumya.local", "Platform@123");
        String slug = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + platform)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Janhvi Solar","slug":"Janhvi Soalr","adminName":"Janhvi Bais",
                                 "adminEmail":"admin-%s@example.test","adminPassword":"JanhviAdmin@2026"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("janhvi-soalr"));
        String vendor = token("admin@saumya.local", "Admin@123");
        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + vendor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Forbidden","slug":"forbidden","adminName":"x","adminEmail":"x@example.test","adminPassword":"Password@12345"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsAliasRequiresVendorDiscountPolicyPermission() throws Exception {
        String vendor = token("admin@saumya.local", "Admin@123");
        mvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + vendor))
                .andExpect(status().isOk());
        String customerEmail = "settings-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeSlug":"saumya-solar","name":"Customer","email":"%s","password":"CustomerPassword@2026"}
                                """.formatted(customerEmail)))
                .andExpect(status().isOk());
        String customer = token(customerEmail, "CustomerPassword@2026");
        mvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationAcceptsExactStoreDisplayNameAndUnknownStoreIsNotForbidden() throws Exception {
        String email = "register-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeSlug":"Saumya Solar","name":"New Customer","email":"%s","password":"CustomerPassword@2026"}
                                """.formatted(email)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeSlug":"No Such Solar Shop","name":"New Customer","email":"unknown-%s@example.test","password":"CustomerPassword@2026"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No active store matches")));
    }

    @Test
    void customerCanReadPublicPackagesAndVendorCanUploadPackageImage() throws Exception {
        String vendor = token("admin@saumya.local", "Admin@123");
        String categories = mvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + vendor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String categoryId = json.readTree(categories).get(0).get("id").asText();
        String product = mvc.perform(post("/api/v1/catalog/products").header("Authorization", "Bearer " + vendor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"IMG-%s","name":"Image test product","price":1000,
                                 "purchasePrice":700,"discountPercent":0,"taxPercent":5,"reorderLevel":0}
                                """.formatted(categoryId, UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = json.readTree(product).get("id").asText();
        String pkg = mvc.perform(post("/api/v1/packages").header("Authorization", "Bearer " + vendor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"IMGPKG-%s","name":"Image package","capacityKw":3,"packageType":"RESIDENTIAL",
                                 "basePrice":10000,"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(UUID.randomUUID(), productId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String packageId = json.readTree(pkg).get("id").asText();
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0ioAAAAASUVORK5CYII=");
        MockMultipartFile file = new MockMultipartFile("file", "solar.png", "image/png", png);
        String uploaded = mvc.perform(multipart("/api/v1/packages/" + packageId + "/image")
                        .file(file).header("Authorization", "Bearer " + vendor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(uploaded).get("imageUrl").asText().startsWith("/uploads/"));
        mvc.perform(get("/api/v1/public/saumya-solar/packages")).andExpect(status().isOk());
        mvc.perform(get(json.readTree(uploaded).get("imageUrl").asText())).andExpect(status().isOk());
    }
}
