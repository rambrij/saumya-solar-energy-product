package com.saumyasolar.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Private local-filesystem evidence storage.
 *
 * Storage keys are tenant/category scoped and never exposed as public URLs. Callers must authorize the owning
 * tenant/resource before reading an object. Legacy flat keys remain readable so existing evidence is not orphaned.
 */
@Service
public class PrivateObjectStorageService {
    
    private final TenantStorageMeteringService metering;
    
    @Autowired
    public PrivateObjectStorageService(TenantStorageMeteringService metering) {
        this.metering = metering;
    }
    
    /** Test/standalone constructor; production Spring wiring always supplies the metering service. */
    public PrivateObjectStorageService() {
        this.metering = null;
    }
    
    @Transactional
    public String put(
            Path localRoot, String category, UUID tenantId, String generatedKey,
            byte[] content, String contentType
    ) throws IOException {
        if (tenantId == null || category == null || !category.matches("[a-z0-9-]{1,50}")
                || generatedKey == null || generatedKey.isBlank()
                || generatedKey.contains("/") || generatedKey.contains("\\")
                || generatedKey.equals(".") || generatedKey.equals("..")) {
            throw new IOException("Invalid private evidence storage key");
        }
        Path root = localRoot.toAbsolutePath().normalize();
        Path tenantDirectory = root.resolve(tenantId.toString()).resolve(category).normalize();
        Path target = tenantDirectory.resolve(generatedKey).normalize();
        if (!tenantDirectory.startsWith(root) || !target.startsWith(tenantDirectory)) {
            throw new IOException("Invalid private evidence path");
        }
        if (metering != null) metering.assertCanStore(tenantId, content.length);
        Files.createDirectories(tenantDirectory);
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return tenantId + "/" + category + "/" + generatedKey;
    }
    
    public byte[] get(Path localRoot, String storageKey) throws IOException {
        return Files.readAllBytes(resolve(localRoot, storageKey));
    }
    
    public void delete(Path localRoot, String storageKey) throws IOException {
        Files.deleteIfExists(resolve(localRoot, storageKey));
    }
    
    private Path resolve(Path localRoot, String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank()) throw new IOException("Private evidence not found");
        Path root = localRoot.toAbsolutePath().normalize();
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root) || file.equals(root) || !Files.isRegularFile(file)) {
            throw new IOException("Private evidence not found");
        }
        return file;
    }
}
