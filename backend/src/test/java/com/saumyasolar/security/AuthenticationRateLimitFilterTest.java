package com.saumyasolar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.nio.charset.StandardCharsets;

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
        assertTrue(response.getContentAsString().contains("Too many requests"));
        assertNull(chain.getRequest());
    }

    @Test
    void loginAppliesIndependentIpAndAccountBucketsAndReplaysRequestBody() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(jdbc, new ObjectMapper(), 10, 5, 10, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        request.setContentType("application/json");
        request.setContent("{\"email\":\" Ram@Example.Test \",\"password\":\"not-persisted\"}"
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals("{\"email\":\" Ram@Example.Test \",\"password\":\"not-persisted\"}",
                new String(chain.getRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        verify(jdbc, times(2)).update(contains("auth_rate_limit_bucket"), any(Object[].class));
        verify(jdbc, times(2)).queryForObject(contains("auth_rate_limit_bucket"), eq(Integer.class), any(Object[].class));
    }

    @Test
    void recoveryAndLoginAccountBucketsDoNotPersistRawIdentifiers() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(jdbc, new ObjectMapper(), 10, 5, 10, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        request.setRemoteAddr("192.0.2.11");
        request.setContentType("application/json");
        request.setContent("{\"email\":\"person@example.test\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(contains("auth_rate_limit_bucket"), args.capture());
        for (Object[] values : args.getAllValues()) {
            for (Object value : values) {
                assertFalse(value instanceof String text && text.contains("person@example.test"));
            }
        }
    }

    @Test
    void oversizedAuthenticationBodyIsRejectedWithoutReadingUnboundedInput() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(jdbc, new ObjectMapper(), 10, 5, 10, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContent(new byte[16 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(400, response.getStatus());
        verifyNoInteractions(jdbc);
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
    @Test
    void publicEnquiryEndpointIsRateLimitedByItsOwnConfiguredBucket() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                jdbc, new ObjectMapper(), new ClientAddressResolver(""),
                10, 5, 10, 1, 20, 60, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/public/vendor-one/enquiries");
        request.setRemoteAddr("192.0.2.12");
        request.setContentType("application/json");
        request.setContent("{\"email\":\"customer@example.test\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        verify(jdbc, times(1)).update(contains("auth_rate_limit_bucket"), any(Object[].class));
    }

    @Test
    void exportAndWebhookEndpointsAreRateLimitedBySeparateBuckets() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                jdbc, new ObjectMapper(), new ClientAddressResolver(""),
                10, 5, 10, 30, 1, 1, 60);

        MockHttpServletRequest export = new MockHttpServletRequest("GET", "/api/v1/platform/tenants/export.xlsx");
        export.setRemoteAddr("192.0.2.13");
        MockHttpServletResponse exportResponse = new MockHttpServletResponse();
        filter.doFilter(export, exportResponse, new MockFilterChain());
        assertEquals(429, exportResponse.getStatus());

        MockHttpServletRequest webhook = new MockHttpServletRequest("POST", "/api/v1/payment-webhooks/provider-a");
        webhook.setRemoteAddr("192.0.2.14");
        MockHttpServletResponse webhookResponse = new MockHttpServletResponse();
        filter.doFilter(webhook, webhookResponse, new MockFilterChain());
        assertEquals(429, webhookResponse.getStatus());
    }

    @Test
    void passwordResetUsesOpaqueTokenDigestAndNeverPersistsRawResetToken() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(jdbc, new ObjectMapper(), 10, 5, 10, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/reset-password");
        request.setRemoteAddr("192.0.2.15");
        request.setContentType("application/json");
        request.setContent("{\"token\":\"opaque-reset-token-value\",\"password\":\"new-secret-password\"}"
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(contains("auth_rate_limit_bucket"), args.capture());
        for (Object[] values : args.getAllValues()) {
            for (Object value : values) {
                assertFalse(value instanceof String text && text.contains("opaque-reset-token-value"));
                assertFalse(value instanceof String text && text.contains("new-secret-password"));
            }
        }
    }

}
