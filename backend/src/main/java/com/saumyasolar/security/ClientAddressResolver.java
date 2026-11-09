package com.saumyasolar.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the originating client only through explicitly trusted reverse proxies.
 * TRUSTED_PROXY_IPS accepts exact IPv4/IPv6 addresses and CIDR networks.
 */
@Component
public class ClientAddressResolver {
    private final List<TrustedNetwork> trustedNetworks;

    public ClientAddressResolver(@Value("${app.security.trusted-proxies:}") String configuredProxies) {
        List<TrustedNetwork> parsed = new ArrayList<>();
        if (configuredProxies != null) {
            for (String entry : configuredProxies.split(",")) {
                TrustedNetwork network = parseNetwork(entry.trim());
                if (network != null) parsed.add(network);
            }
        }
        trustedNetworks = List.copyOf(parsed);
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalizeAddress(request.getRemoteAddr());
        if (remote == null) return "unknown";
        if (!isTrusted(remote)) return remote;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        List<String> chain = Arrays.stream(forwarded.split(",")).map(String::trim).toList();
        String candidate = remote;
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (!isTrusted(candidate)) break;
            String next = normalizeAddress(chain.get(i));
            if (next == null) return remote;
            candidate = next;
        }
        return candidate;
    }

    private boolean isTrusted(String address) {
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress();
            return trustedNetworks.stream().anyMatch(network -> network.contains(bytes));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static TrustedNetwork parseNetwork(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split("/", -1);
        if (parts.length > 2) return null;
        String normalized = normalizeAddress(parts[0]);
        if (normalized == null) return null;
        try {
            byte[] address = InetAddress.getByName(normalized).getAddress();
            int prefix = parts.length == 1 ? address.length * 8 : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > address.length * 8) return null;
            return new TrustedNetwork(address, prefix);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeAddress(String value) {
        if (value == null) return null;
        boolean ipv4 = value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
        boolean ipv6 = value.contains(":") && value.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception ignored) { return null; }
    }

    private record TrustedNetwork(byte[] address, int prefixLength) {
        private boolean contains(byte[] candidate) {
            if (candidate.length != address.length) return false;
            int wholeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < wholeBytes; i++)
                if (address[i] != candidate[i]) return false;
            if (remainingBits == 0) return true;
            int mask = 0xFF << (8 - remainingBits);
            return (address[wholeBytes] & mask) == (candidate[wholeBytes] & mask);
        }
    }
}
