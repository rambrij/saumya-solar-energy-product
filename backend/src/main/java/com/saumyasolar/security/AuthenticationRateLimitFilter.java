package com.saumyasolar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Component
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final int loginLimit;
    private final int recoveryLimit;
    private final int mfaLimit;
    private final int windowSeconds;
    private final ClientAddressResolver addressResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthenticationRateLimitFilter(
            JdbcTemplate jdbc, ObjectMapper mapper, ClientAddressResolver addressResolver,
            @Value("${app.auth.rate-limit.login-per-window:10}") int loginLimit,
            @Value("${app.auth.rate-limit.recovery-per-window:5}") int recoveryLimit,
            @Value("${app.auth.rate-limit.mfa-per-window:10}") int mfaLimit,
            @Value("${app.auth.rate-limit.window-seconds:60}") int windowSeconds) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.addressResolver = addressResolver;
        this.loginLimit = Math.max(1, loginLimit);
        this.recoveryLimit = Math.max(1, recoveryLimit);
        this.mfaLimit = Math.max(1, mfaLimit);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    AuthenticationRateLimitFilter(JdbcTemplate jdbc, ObjectMapper mapper, int loginLimit, int recoveryLimit, int mfaLimit, int windowSeconds) {
        this(jdbc, mapper, new ClientAddressResolver(""), loginLimit, recoveryLimit, mfaLimit, windowSeconds);
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
        return !("POST".equalsIgnoreCase(request.getMethod()) &&
                (path.equals("/api/v1/auth/login") ||
                 path.equals("/api/v1/auth/refresh") ||
                 path.equals("/api/v1/auth/forgot-password") ||
                 path.equals("/api/v1/auth/reset-password") ||
                 path.equals("/api/v1/auth/mfa/verify")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = requestPath(request);
        int limit = path.endsWith("/login") || path.endsWith("/refresh") ? loginLimit
                : path.endsWith("/mfa/verify") ? mfaLimit : recoveryLimit;
        String address = addressResolver.resolve(request);
        String key = digest(path + "|" + address);
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);
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
            } catch (org.springframework.dao.DuplicateKeyException concurrentInsert) {
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
        if (count != null && count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", Integer.toString(windowSeconds));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            mapper.writeValue(response.getWriter(), Map.of(
                    "type", "about:blank", "title", "Too Many Requests", "status", 429,
                    "detail", "Too many authentication attempts. Please try again later.",
                    "timestamp", now.toString()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
