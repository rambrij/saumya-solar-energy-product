package com.saumyasolar.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Locale;

@Component
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_AUTH_BODY_BYTES = 16 * 1024;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final int loginLimit;
    private final int recoveryLimit;
    private final int mfaLimit;
    private final int publicEnquiryLimit;
    private final int exportLimit;
    private final int webhookLimit;
    private final int windowSeconds;
    private final ClientAddressResolver addressResolver;
    private final byte[] accountHmacKey;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthenticationRateLimitFilter(
            JdbcTemplate jdbc, ObjectMapper mapper, ClientAddressResolver addressResolver,
            @Value("${app.auth.rate-limit.login-per-window:10}") int loginLimit,
            @Value("${app.auth.rate-limit.recovery-per-window:5}") int recoveryLimit,
            @Value("${app.auth.rate-limit.mfa-per-window:10}") int mfaLimit,
            @Value("${app.auth.rate-limit.public-enquiry-per-window:30}") int publicEnquiryLimit,
            @Value("${app.auth.rate-limit.export-per-window:20}") int exportLimit,
            @Value("${app.auth.rate-limit.webhook-per-window:60}") int webhookLimit,
            @Value("${app.auth.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${app.jwt-secret}") String jwtSecret) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.addressResolver = addressResolver;
        this.loginLimit = Math.max(1, loginLimit);
        this.recoveryLimit = Math.max(1, recoveryLimit);
        this.mfaLimit = Math.max(1, mfaLimit);
        this.publicEnquiryLimit = Math.max(1, publicEnquiryLimit);
        this.exportLimit = Math.max(1, exportLimit);
        this.webhookLimit = Math.max(1, webhookLimit);
        this.windowSeconds = Math.max(1, windowSeconds);
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must be configured for account rate-limit hashing");
        }
        this.accountHmacKey = sha256(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    AuthenticationRateLimitFilter(JdbcTemplate jdbc, ObjectMapper mapper, int loginLimit,
                                   int recoveryLimit, int mfaLimit, int windowSeconds) {
        this(jdbc, mapper, new ClientAddressResolver(""), loginLimit, recoveryLimit,
                mfaLimit, recoveryLimit, recoveryLimit, recoveryLimit, windowSeconds, "test-only-rate-limit-hmac-secret");
    }

    AuthenticationRateLimitFilter(JdbcTemplate jdbc, ObjectMapper mapper, ClientAddressResolver resolver,
                                   int loginLimit, int recoveryLimit, int mfaLimit, int publicEnquiryLimit,
                                   int exportLimit, int webhookLimit, int windowSeconds) {
        this(jdbc, mapper, resolver, loginLimit, recoveryLimit, mfaLimit, publicEnquiryLimit,
                exportLimit, webhookLimit, windowSeconds, "test-only-rate-limit-hmac-secret");
    }

    private String requestPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isBlank() && path.startsWith(context)) path = path.substring(context.length());
        return path;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return limitFor(request.getMethod(), path) == 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = requestPath(request);
        int limit = limitFor(request.getMethod(), path);

        HttpServletRequest effectiveRequest = request;
        String accountIdentifier = null;
        if (isAccountScopedPath(path)) {
            byte[] body = request.getInputStream().readNBytes(MAX_AUTH_BODY_BYTES + 1);
            if (body.length > MAX_AUTH_BODY_BYTES) {
                writeBadRequest(response, Instant.now());
                return;
            }
            effectiveRequest = new CachedBodyRequest(request, body);
            accountIdentifier = accountIdentifier(body);
        }

        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);
        String address = addressResolver.resolve(request);
        String ipBucket = sha256Hex("ip|" + path + "|" + address);
        if (incrementAndGet(ipBucket, now, windowStart) > limit) {
            writeRateLimit(response, now);
            return;
        }

        if (accountIdentifier != null) {
            String normalized = accountIdentifier.trim().toLowerCase(Locale.ROOT);
            String accountBucket = hmacHex("account|" + path + "|" + normalized);
            if (incrementAndGet(accountBucket, now, windowStart) > limit) {
                writeRateLimit(response, now);
                return;
            }
        }

        chain.doFilter(effectiveRequest, response);
    }

    private int limitFor(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh")) return loginLimit;
            if (path.equals("/api/v1/auth/mfa/verify")) return mfaLimit;
            if (path.equals("/api/v1/auth/forgot-password") || path.equals("/api/v1/auth/reset-password")) return recoveryLimit;
            if (path.matches("/api/v1/public/[^/]+/enquiries")) return publicEnquiryLimit;
            if (path.startsWith("/api/v1/payment-webhooks/") || path.startsWith("/api/v1/payment-gateway-webhooks/")) return webhookLimit;
            if (path.equals("/api/v1/exports") || path.startsWith("/api/v1/exports/")) return exportLimit;
        }
        if ("GET".equalsIgnoreCase(method)
                && (path.contains("/export") || path.startsWith("/api/v1/exports/"))) return exportLimit;
        return 0;
    }

    private boolean isAccountScopedPath(String path) {
        return path.endsWith("/login") || path.endsWith("/forgot-password") || path.endsWith("/reset-password");
    }

    private String accountIdentifier(byte[] body) {
        if (body.length == 0) return null;
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) return null;
            for (String field : new String[]{"email", "username", "login", "token"}) {
                JsonNode value = root.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (IOException ignored) {
            // Invalid JSON is still protected by the IP/endpoint bucket and handled by MVC.
        }
        return null;
    }

    private int incrementAndGet(String key, Instant now, Instant windowStart) {
        int updated = jdbc.update("""
                UPDATE auth_rate_limit_bucket
                SET request_count = CASE WHEN window_started_at <= ? THEN 1 ELSE request_count + 1 END,
                    window_started_at = CASE WHEN window_started_at <= ? THEN ? ELSE window_started_at END,
                    updated_at = ?
                WHERE bucket_key = ?
                """, java.sql.Timestamp.from(windowStart), java.sql.Timestamp.from(windowStart),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), key);
        if (updated == 0) {
            try {
                jdbc.update("""
                        INSERT INTO auth_rate_limit_bucket(bucket_key, window_started_at, request_count, updated_at)
                        VALUES (?, ?, 1, ?)
                        """, key, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            } catch (DuplicateKeyException concurrentInsert) {
                jdbc.update("""
                        UPDATE auth_rate_limit_bucket
                        SET request_count = CASE WHEN window_started_at <= ? THEN 1 ELSE request_count + 1 END,
                            window_started_at = CASE WHEN window_started_at <= ? THEN ? ELSE window_started_at END,
                            updated_at = ?
                        WHERE bucket_key = ?
                        """, java.sql.Timestamp.from(windowStart), java.sql.Timestamp.from(windowStart),
                        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), key);
            }
        }
        Integer count = jdbc.queryForObject(
                "SELECT request_count FROM auth_rate_limit_bucket WHERE bucket_key = ?",
                Integer.class, key);
        return count == null ? 0 : count;
    }

    private void writeRateLimit(HttpServletResponse response, Instant now) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Integer.toString(windowSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), Map.of(
                "type", "about:blank", "title", "Too Many Requests", "status", 429,
                "detail", "Too many requests. Please try again later.",
                "timestamp", now.toString()));
    }

    private void writeBadRequest(HttpServletResponse response, Instant now) throws IOException {
        response.setStatus(400);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), Map.of(
                "type", "about:blank", "title", "Bad Request", "status", 400,
                "detail", "Authentication request is invalid.", "timestamp", now.toString()));
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(accountHmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", ex);
        }
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Non-blocking reads are not supported");
                }
                @Override public int read() { return input.read(); }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }

        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
