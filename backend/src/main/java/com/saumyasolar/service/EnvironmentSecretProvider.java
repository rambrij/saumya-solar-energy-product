package com.saumyasolar.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
/** Local/development provider. Production deployments should supply a Vault/KMS-backed SecretProvider bean. */
@Component
public class EnvironmentSecretProvider implements SecretProvider {
    private final Environment environment;
    
    public EnvironmentSecretProvider(Environment environment) {
        this.environment = environment;
    }
    
    public String providerCode() {
        return "env";
    }
    
    public String resolve(String reference) {
        if (reference == null || !reference.startsWith("env://"))
            throw new IllegalArgumentException("Unsupported secret reference");
        String key = reference.substring(6);
        if (!key.matches("[A-Z_][A-Z0-9_]{0,127}"))
            throw new IllegalArgumentException("Invalid environment secret reference");
        String value = environment.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalStateException("Configured integration secret is unavailable");
        return value;
    }
}
