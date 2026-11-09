package com.saumyasolar.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Component
public class AuthCookieService {
    public static final String ACCESS_COOKIE = "saumya_access";
    public static final String REFRESH_COOKIE = "saumya_refresh";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final long ttlDays;
    private final long accessTtlSeconds;

    public AuthCookieService(
            @Value("${app.auth.refresh-cookie-secure:true}") boolean secure,
            @Value("${app.auth.refresh-cookie-same-site:Strict}") String sameSite,
            @Value("${app.auth.refresh-cookie-domain:}") String domain,
            @Value("${app.auth.refresh-token-ttl-days:7}") long ttlDays,
            @Value("${app.auth.access-token-ttl-seconds:900}") long accessTtlSeconds) {
        if (!"Strict".equalsIgnoreCase(sameSite) && !"Lax".equalsIgnoreCase(sameSite)
                && !"None".equalsIgnoreCase(sameSite))
            throw new IllegalArgumentException("Refresh cookie SameSite must be Strict, Lax or None");
        if ("None".equalsIgnoreCase(sameSite) && !secure)
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain == null ? "" : domain.trim();
        this.ttlDays = Math.max(1, ttlDays);
        this.accessTtlSeconds = Math.max(60, accessTtlSeconds);
    }

    public String issue(HttpServletResponse response, String accessToken, String refreshToken) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, accessToken, true, "/api/v1", Duration.ofSeconds(accessTtlSeconds)));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, refreshToken, true, "/api/v1/auth", Duration.ofDays(ttlDays)));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, csrf, false, "/", Duration.ofDays(ttlDays)));
        return csrf;
    }

    public String accessToken(HttpServletRequest request) {
        return cookieValue(request, ACCESS_COOKIE);
    }

    public String refreshToken(HttpServletRequest request) {
        return cookieValue(request, REFRESH_COOKIE);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies())
            if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public String csrfToken(HttpServletRequest request) {
        return cookieValue(request, CSRF_COOKIE);
    }

    public void requireCsrf(HttpServletRequest request) {
        String cookie = null;
        if (request.getCookies() != null) {
            for (Cookie item : request.getCookies())
                if (CSRF_COOKIE.equals(item.getName())) cookie = item.getValue();
        }
        String header = request.getHeader(CSRF_HEADER);
        if (cookie == null || header == null || !MessageDigest.isEqual(
                cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CSRF validation failed");
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", true, "/api/v1", Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", true, "/api/v1/auth", Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", false, "/", Duration.ZERO));
    }

    private String cookie(String name, String value, boolean httpOnly, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(httpOnly).secure(secure).sameSite(sameSite).path(path).maxAge(maxAge);
        if (!domain.isBlank()) builder.domain(domain);
        return builder.build().toString();
    }
}
