package com.saumyasolar.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AuthCookieServiceTest {
    @Test
    void refreshCookieIsHttpOnlySecureAndScopedToAuthEndpoints() {
        var service = new AuthCookieService(true, "None", "", 7);
        var response = new MockHttpServletResponse();
        String csrf = service.issue(response, "opaque-refresh-token");
        var cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(v -> v.contains("saumya_refresh=")
                && v.contains("HttpOnly") && v.contains("Secure")
                && v.contains("SameSite=None") && v.contains("Path=/api/v1/auth")));
        assertTrue(cookies.stream().anyMatch(v -> v.contains("XSRF-TOKEN=")
                && !v.contains("HttpOnly") && v.contains("Secure")));
        assertNotNull(csrf);
    }

    @Test
    void requiresDoubleSubmitCsrfCookieAndHeader() {
        var service = new AuthCookieService(true, "Strict", "", 7);
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.CSRF_COOKIE, "csrf-value"));
        request.addHeader(AuthCookieService.CSRF_HEADER, "csrf-value");
        assertDoesNotThrow(() -> service.requireCsrf(request));
        request.addHeader(AuthCookieService.CSRF_HEADER, "different");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.requireCsrf(request))
                .getStatusCode().value());
    }

    @Test
    void sameSiteNoneRequiresSecureCookie() {
        assertThrows(IllegalArgumentException.class, () -> new AuthCookieService(false, "None", "", 7));
    }
}
