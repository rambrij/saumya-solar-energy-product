package com.saumyasolar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.IntegrationConnection;
import com.saumyasolar.domain.IntegrationSyncJob;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Safe baseline adapter for tenant-configured HTTPS APIs. Provider-specific accounting
 * and payment adapters can implement IntegrationAdapter without changing the durable queue.
 */
@Component
public class GenericHttpIntegrationAdapter implements IntegrationAdapter {
    private static final Pattern ENV_NAME = Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");
    private final ObjectMapper mapper;
    private final HttpClient client;

    public GenericHttpIntegrationAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
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
        boolean localHttp = "http".equals(scheme) && uri.getHost() != null
                && (uri.getHost().equalsIgnoreCase("localhost") || uri.getHost().equals("127.0.0.1"));
        if (!"https".equals(scheme) && !localHttp) {
            throw new IllegalArgumentException("Generic integration endpoints must use HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Integration endpoint must not contain user information or fragments");
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", job.getIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(job.getPayloadJson()));

        String secretReference = connection.getSecretReference();
        if (secretReference != null && !secretReference.isBlank()) {
            if (!secretReference.startsWith("env://")) {
                throw new IllegalStateException("This adapter supports env:// secret references; configure a secret resolver for vault://");
            }
            String variable = secretReference.substring("env://".length());
            if (!ENV_NAME.matcher(variable).matches()) {
                throw new IllegalArgumentException("Invalid environment secret reference");
            }
            String secret = System.getenv(variable);
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("Configured integration environment secret is unavailable");
            }
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
    }

    private String required(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Integration configuration requires " + field);
        }
        return value.asText().trim();
    }
}
