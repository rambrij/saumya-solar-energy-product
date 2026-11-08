package com.saumyasolar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.IntegrationConnection;
import com.saumyasolar.domain.IntegrationSyncJob;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Safe baseline adapter for tenant-configured HTTPS APIs. Provider-specific accounting
 * and payment adapters can implement IntegrationAdapter without changing the durable queue.
 */
@Component
public class GenericHttpIntegrationAdapter implements IntegrationAdapter {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final SecretProviderRegistry secrets;

    public GenericHttpIntegrationAdapter(ObjectMapper mapper, SecretProviderRegistry secrets) {
        this.mapper = mapper;
        this.secrets = secrets;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String providerCode() {
        return "GENERIC_HTTP";
    }

    @Override
    public void synchronize(IntegrationConnection connection, IntegrationSyncJob job) throws Exception {
        JsonNode config = mapper.readTree(connection.getConfigJson());
        String endpoint = required(config, "endpoint");
        URI uri = URI.create(endpoint);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) || uri.getHost() == null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Generic integration endpoints must use HTTPS on the standard port");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost().endsWith(".")) {
            throw new IllegalArgumentException("Integration endpoint must not contain user information, fragments or ambiguous hostnames");
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal")
                || host.equals("metadata.google.internal") || host.matches("[0-9.]+")) {
            throw new IllegalArgumentException("Integration endpoint host is not permitted");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Integration endpoint resolves to a non-public network address");
            }
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", job.getIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(job.getPayloadJson()));

        String secretReference = connection.getSecretReference();
        if (secretReference != null && !secretReference.isBlank()) {
            String secret = secrets.resolve(secretReference);
            String header = config.hasNonNull("authHeader") ? config.get("authHeader").asText() : "Authorization";
            String schemeName = config.hasNonNull("authScheme") ? config.get("authScheme").asText() : "Bearer";
            if (!header.matches("[A-Za-z0-9-]{1,64}") || header.equalsIgnoreCase("Host")
                    || header.equalsIgnoreCase("Content-Length")) {
                throw new IllegalArgumentException("Invalid integration authentication header");
            }
            request.header(header, schemeName.isBlank() ? secret : schemeName + " " + secret);
        }

        HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Integration endpoint returned HTTP " + response.statusCode());
        }
        response.headers().firstValue("Location").ifPresent(location ->
                job.setExternalReference(location.length() > 160 ? location.substring(0, 160) : location));
    }

    private String required(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Integration configuration requires " + field);
        }
        return value.asText().trim();
    }
}
