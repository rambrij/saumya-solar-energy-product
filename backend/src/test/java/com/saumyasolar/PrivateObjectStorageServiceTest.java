package com.saumyasolar;

import com.saumyasolar.service.PrivateObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrivateObjectStorageServiceTest {
    @TempDir Path temp;

    @Test
    void localStorageRoundTripsAndDeletesPrivateObjects() throws Exception {
        PrivateObjectStorageService storage = new PrivateObjectStorageService("local", "", "us-east-1", "", false);
        UUID tenant = UUID.randomUUID();
        byte[] payload = "%PDF-1.4 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String key = storage.put(temp, "surveys", tenant, "proof.pdf", payload, "application/pdf");

        assertEquals("proof.pdf", key);
        assertArrayEquals(payload, storage.get(temp, key));
        assertTrue(Files.exists(temp.resolve(key)));

        storage.delete(temp, key);
        assertFalse(Files.exists(temp.resolve(key)));
        assertThrows(java.io.IOException.class, () -> storage.get(temp, key));
    }

    @Test
    void localStorageRejectsPathTraversal() {
        PrivateObjectStorageService storage = new PrivateObjectStorageService("local", "", "us-east-1", "", false);
        assertThrows(java.io.IOException.class,
                () -> storage.put(temp, "surveys", UUID.randomUUID(), "../outside.pdf",
                        new byte[]{1}, "application/pdf"));
        assertThrows(java.io.IOException.class, () -> storage.get(temp, "../outside.pdf"));
    }

    @Test
    void invalidProviderAndMissingS3BucketFailFast() {
        assertThrows(IllegalStateException.class,
                () -> new PrivateObjectStorageService("filesystem", "", "us-east-1", "", false));
        assertThrows(IllegalStateException.class,
                () -> new PrivateObjectStorageService("s3", "", "us-east-1", "", false));
    }
}
