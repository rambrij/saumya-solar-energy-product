package com.saumyasolar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;

/**
 * Enforces double-submit CSRF validation for browser requests authenticated by
 * the HttpOnly access cookie. Bearer-token clients remain stateless and are not
 * exposed to ambient-cookie CSRF.
 */
@Component
public class CookieCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name()
    );
    private final AuthCookieService cookies;

    public CookieCsrfFilter(AuthCookieService cookies) {
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT))
                && cookies.accessToken(request) != null) {
            try {
                cookies.requireCsrf(request);
            } catch (ResponseStatusException invalidCsrf) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"detail\":\"CSRF validation failed\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
