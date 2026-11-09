package com.saumyasolar;

import com.saumyasolar.service.PrivateObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrivateObjectStorageServiceTest {
    @TempDir
    Path temp;
    
    @Test
    void localStorageRoundTripsAndDeletesTenantScopedObjects() throws Exception {
        PrivateObjectStorageService storage = new PrivateObjectStorageService();
        UUID tenant = UUID.randomUUID();
        byte[] payload = "%PDF-1.4 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String key = storage.put(temp, "surveys", tenant, "proof.pdf", payload, "application/pdf");
        
        assertEquals(tenant + "/surveys/proof.pdf", key);
        assertArrayEquals(payload, storage.get(temp, key));
        assertTrue(Files.exists(temp.resolve(tenant.toString()).resolve("surveys").resolve("proof.pdf")));
        
        storage.delete(temp, key);
        assertFalse(Files.exists(temp.resolve(key)));
        assertThrows(IOException.class, () -> storage.get(temp, key));
    }
    
    @Test
    void differentTenantsAndCategoriesAreStoredInSeparateDirectories() throws Exception {
        PrivateObjectStorageService storage = new PrivateObjectStorageService();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String firstKey = storage.put(temp, "surveys", first, "same.pdf", new byte[] { 1 }, "application/pdf");
        String secondKey = storage.put(
                temp, "warranty-claims", second, "same.pdf", new byte[] { 2
                }, "application/pdf"
        );
        
        assertNotEquals(firstKey, secondKey);
        assertArrayEquals(new byte[] { 1 }, storage.get(temp, firstKey));
        assertArrayEquals(new byte[] { 2 }, storage.get(temp, secondKey));
    }
    
    @Test
    void localStorageRejectsTraversalAndSeparators() {
        PrivateObjectStorageService storage = new PrivateObjectStorageService();
        UUID tenant = UUID.randomUUID();
        assertThrows(
                IOException.class, () -> storage.put(
                        temp, "surveys", tenant, "../outside.pdf", new byte[] { 1
                        }, "application/pdf"
                )
        );
        assertThrows(
                IOException.class, () -> storage.put(
                        temp, "surveys", tenant, "nested\\\\outside.pdf", new byte[] { 1 }, "application/pdf")
        );
        assertThrows(
                IOException.class, () -> storage.put(
                        temp, "../surveys", tenant, "proof.pdf", new byte[] { 1
                        }, "application/pdf"
                )
        );
        assertThrows(IOException.class, () -> storage.get(temp, "../outside.pdf"));
    }
    
    @Test
    void legacyFlatLocalEvidenceKeysRemainReadableAndDeletable() throws Exception {
        PrivateObjectStorageService storage = new PrivateObjectStorageService();
        byte[] payload = new byte[] { 7, 8, 9 };
        Files.write(temp.resolve("legacy-proof.pdf"), payload);
        
        assertArrayEquals(payload, storage.get(temp, "legacy-proof.pdf"));
        storage.delete(temp, "legacy-proof.pdf");
        assertFalse(Files.exists(temp.resolve("legacy-proof.pdf")));
    }
    
    @Test
    void localStorageRejectsInvalidOrMissingKeys() {
        PrivateObjectStorageService storage = new PrivateObjectStorageService();
        assertThrows(IOException.class, () -> storage.get(temp, ""));
        assertThrows(IOException.class, () -> storage.get(temp, "../outside.pdf"));
    }
}
