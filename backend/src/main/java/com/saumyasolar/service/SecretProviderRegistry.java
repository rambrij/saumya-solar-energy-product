package com.saumyasolar.service;

import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class SecretProviderRegistry {
    private final List<SecretProvider> providers;
    
    public SecretProviderRegistry(List<SecretProvider> providers) {
        this.providers = List.copyOf(providers);
    }
    
    public SecretProvider require(String code) {
        return providers.stream().filter(p -> p.providerCode().equalsIgnoreCase(code)).findFirst().orElseThrow(() -> new IllegalStateException("Secret provider is not configured: " + code));
    }
    
    public String resolve(String reference) {
        if (reference == null || !reference.contains("://"))
            throw new IllegalArgumentException("Secret reference must include a provider scheme");
        return require(reference.substring(0, reference.indexOf("://"))).resolve(reference);
    }
}
