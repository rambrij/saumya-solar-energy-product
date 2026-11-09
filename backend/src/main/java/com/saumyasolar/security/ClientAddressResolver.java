package com.saumyasolar.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientAddressResolver {
    private final Set<String> trustedProxies;

    public ClientAddressResolver(@Value("${app.security.trusted-proxies:}") String configuredProxies) {
        trustedProxies = Arrays.stream(configuredProxies.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(ClientAddressResolver::normalizeAddress).filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (remote == null) return "unknown";
        if (!trustedProxies.contains(remote)) return remote;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        List<String> chain = Arrays.stream(forwarded.split(","))
                .map(String::trim).toList();
        String candidate = remote;
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (!trustedProxies.contains(candidate)) break;
            String next = normalize(chain.get(i));
            if (next == null) return remote;
            candidate = next;
        }
        return candidate;
    }

    private String normalize(String value) { return normalizeAddress(value); }

    private static String normalizeAddress(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]+")) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception ignored) { return null; }
    }
}
