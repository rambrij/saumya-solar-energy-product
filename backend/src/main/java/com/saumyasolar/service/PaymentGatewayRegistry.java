package com.saumyasolar.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGatewayAdapter> adapters;
    
    public PaymentGatewayRegistry(List<PaymentGatewayAdapter> candidates) {
        Map<String, PaymentGatewayAdapter> registered = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (PaymentGatewayAdapter adapter : candidates) {
            String code = adapter.providerCode();
            if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,59}"))
                throw new IllegalStateException("Payment gateway adapter has an invalid provider code");
            if (registered.putIfAbsent(code, adapter) != null)
                throw new IllegalStateException("Duplicate payment gateway adapter: " + code);
        }
        this.adapters = Collections.unmodifiableMap(registered);
    }
    
    public Optional<PaymentGatewayAdapter> find(String providerCode) {
        return Optional.ofNullable(providerCode == null ? null : adapters.get(providerCode));
    }
    
    public PaymentGatewayAdapter require(String providerCode) {
        return find(providerCode).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "No payment gateway adapter is installed for provider " + providerCode));
    }
    
    public Set<String> providers() {
        return adapters.keySet();
    }
}
