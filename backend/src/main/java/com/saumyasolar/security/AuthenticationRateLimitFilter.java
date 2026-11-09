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
        jdbc.update("""
                INSERT INTO auth_rate_limit_bucket(bucket_key, window_started_at, request_count, updated_at)
                VALUES (?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE
                  request_count = IF(window_started_at <= ?, 1, request_count + 1),
                  window_started_at = IF(window_started_at <= ?, VALUES(window_started_at), window_started_at),
                  updated_at = VALUES(updated_at)
                """, key, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(windowStart), java.sql.Timestamp.from(windowStart));
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
