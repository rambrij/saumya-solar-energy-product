package com.saumyasolar.service;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AccountingAdapterRegistry {
    private final Map<String, AccountingAdapter> adapters;
    
    public AccountingAdapterRegistry(List<AccountingAdapter> candidates) {
        Map<String, AccountingAdapter> registered = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (AccountingAdapter adapter : candidates) {
            String code = adapter.providerCode();
            if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,59}"))
                throw new IllegalStateException("Accounting adapter has an invalid provider code");
            if (registered.putIfAbsent(code, adapter) != null)
                throw new IllegalStateException("Duplicate accounting adapter: " + code);
        }
        this.adapters = Collections.unmodifiableMap(registered);
    }
    
    public Optional<AccountingAdapter> find(String providerCode) {
        return Optional.ofNullable(providerCode == null ? null : adapters.get(providerCode));
    }
    
    public Set<String> providers() {
        return adapters.keySet();
    }
}
