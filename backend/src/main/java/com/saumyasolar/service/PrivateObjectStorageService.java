package com.saumyasolar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Private evidence storage. S3 objects are never exposed through public URLs;
 * callers must authorize the owning tenant/resource before reading an object.
 * Existing unprefixed database keys remain readable from the configured local root.
 */
@Service
public class PrivateObjectStorageService {
    private static final String S3_PREFIX = "s3:";
    private final String provider;
    private final String bucket;
    private final S3Client s3;

    public PrivateObjectStorageService(
            @Value("${app.private-storage.provider:local}") String provider,
            @Value("${app.private-storage.s3.bucket:}") String bucket,
            @Value("${app.private-storage.s3.region:us-east-1}") String region,
            @Value("${app.private-storage.s3.endpoint:}") String endpoint,
            @Value("${app.private-storage.s3.path-style-access:false}") boolean pathStyleAccess) {
        this.provider = provider == null ? "local" : provider.trim().toLowerCase();
        this.bucket = bucket == null ? "" : bucket.trim();
        if (!this.provider.equals("local") && !this.provider.equals("s3")) {
            throw new IllegalStateException("app.private-storage.provider must be local or s3");
        }
        if (this.provider.equals("s3") && this.bucket.isBlank()) {
            throw new IllegalStateException("app.private-storage.s3.bucket is required when S3 storage is enabled");
        }
        if (this.provider.equals("s3")) {
            var builder = S3Client.builder()
                    .region(Region.of(region))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build());
            if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(java.net.URI.create(endpoint));
            this.s3 = builder.build(); // Uses the AWS SDK default credential provider chain (role/IRSA/env/profile).
        } else {
            this.s3 = null;
        }
    }

    public String put(Path localRoot, String category, UUID tenantId, String generatedKey,
                      byte[] content, String contentType) throws IOException {
        if (provider.equals("s3")) {
            String objectKey = "tenants/" + tenantId + "/" + category + "/" + generatedKey;
            try {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                        .contentType(contentType).serverSideEncryption(ServerSideEncryption.AES256).build(),
                        RequestBody.fromBytes(content));
                return S3_PREFIX + objectKey;
            } catch (RuntimeException ex) {
                throw new IOException("Unable to store private evidence in object storage", ex);
            }
        }
        Path root = localRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(generatedKey).normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid private evidence path");
        Files.write(target, content, StandardOpenOption.CREATE_NEW);
        return generatedKey;
    }

    public byte[] get(Path localRoot, String storageKey) throws IOException {
        if (storageKey != null && storageKey.startsWith(S3_PREFIX)) {
            try {
                ResponseBytes<software.amazon.awssdk.services.s3.model.GetObjectResponse> response =
                        s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket)
                                .key(storageKey.substring(S3_PREFIX.length())).build());
                return response.asByteArray();
            } catch (RuntimeException ex) {
                throw new IOException("Unable to read private evidence from object storage", ex);
            }
        }
        Path root = localRoot.toAbsolutePath().normalize();
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Private evidence not found");
        return Files.readAllBytes(file);
    }

    public void delete(Path localRoot, String storageKey) throws IOException {
        if (storageKey != null && storageKey.startsWith(S3_PREFIX)) {
            try {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket)
                        .key(storageKey.substring(S3_PREFIX.length())).build());
            } catch (RuntimeException ex) {
                throw new IOException("Unable to delete private evidence from object storage", ex);
            }
            return;
        }
        Path root = localRoot.toAbsolutePath().normalize();
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root)) throw new IOException("Invalid private evidence path");
        Files.deleteIfExists(file);
    }
}
