package com.saumyasolar.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Measures private evidence bytes across every configured tenant evidence store.
 * The tenant row lock acquired by EnterpriseSaasService is held for the caller's
 * transaction, serializing quota checks for concurrent uploads to the same tenant.
 */
@Service
public class TenantStorageMeteringService {
    private final EnterpriseSaasService entitlements;
    private final List<Path> roots;

    public TenantStorageMeteringService(
            EnterpriseSaasService entitlements,
            @Value("${app.service.attachment-location:./data/private-service-attachments}") String serviceRoot,
            @Value("${app.survey.attachment-location:./data/private-survey-attachments}") String surveyRoot,
            @Value("${app.warranty.attachment-location:./data/private-warranty-attachments}") String warrantyRoot) {
        this.entitlements = entitlements;
        this.roots = List.of(Path.of(serviceRoot), Path.of(surveyRoot), Path.of(warrantyRoot));
    }

    @Transactional
    public void assertCanStore(UUID tenantId, long incomingBytes) throws IOException {
        if (incomingBytes < 0) throw new IllegalArgumentException("Incoming storage size cannot be negative");
        long limit = entitlements.storageLimitBytes(tenantId);
        long used = usedBytes(tenantId);
        if (used > limit || incomingBytes > limit - used) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Tenant storage quota exceeded; upgrade the subscription or remove unused evidence");
        }
    }

    public long usedBytes(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant ID is required");
        long total = 0L;
        for (Path configuredRoot : roots) {
            Path root = configuredRoot.toAbsolutePath().normalize();
            Path tenantDirectory = root.resolve(tenantId.toString()).normalize();
            if (!tenantDirectory.startsWith(root) || !Files.isDirectory(tenantDirectory)) continue;
            try (Stream<Path> files = Files.walk(tenantDirectory)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    long size = Files.size(file);
                    total = size > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + size;
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to measure tenant evidence storage", ex);
            }
        }
        return total;
    }
}
