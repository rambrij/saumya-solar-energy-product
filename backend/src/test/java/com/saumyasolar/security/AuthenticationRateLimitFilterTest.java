package com.saumyasolar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationRateLimitFilterTest {
    @Test
    void authenticationEndpointReturns429WhenWindowLimitIsExceeded() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(jdbc, new ObjectMapper(), 1, 1, 1, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too many authentication attempts"));
        assertNull(chain.getRequest());
    }

    @Test
    void nonAuthenticationEndpointIsNotRateLimited() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(jdbc, new ObjectMapper(), 1, 1, 1, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(jdbc);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
