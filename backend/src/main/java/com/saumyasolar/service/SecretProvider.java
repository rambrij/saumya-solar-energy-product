package com.saumyasolar.service;

/** Resolves credentials by opaque reference; implementations must never log secret values. */
public interface SecretProvider {
    String providerCode();
    
    String resolve(String reference);
}
