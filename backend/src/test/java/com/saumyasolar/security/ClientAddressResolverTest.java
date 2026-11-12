package com.saumyasolar.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientAddressResolverTest {
    @Test
    void ignoresForwardedHeadersFromUntrustedRemoteAddress() {
        var resolver = new ClientAddressResolver("10.0.0.10");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.25");
        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void resolvesClientAddressOnlyWhenRemoteProxyIsExplicitlyTrusted() {
        var resolver = new ClientAddressResolver("10.0.0.10");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.25");
        assertEquals("198.51.100.25", resolver.resolve(request));
    }


    @Test
    void resolvesForwardedClientThroughTrustedIpv4Cidr() {
        var resolver = new ClientAddressResolver("10.0.0.0/24");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.25");
        assertEquals("198.51.100.25", resolver.resolve(request));
    }

    @Test
    void doesNotTrustProxyOutsideConfiguredCidr() {
        var resolver = new ClientAddressResolver("10.0.0.0/24");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.1.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.25");
        assertEquals("10.0.1.10", resolver.resolve(request));
    }

    @Test
    void rejectsMalformedForwardedAddressAndFallsBackToTrustedProxyAddress() {
        var resolver = new ClientAddressResolver("10.0.0.10");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("attacker.example");
        assertEquals("10.0.0.10", resolver.resolve(request));
    }
}
