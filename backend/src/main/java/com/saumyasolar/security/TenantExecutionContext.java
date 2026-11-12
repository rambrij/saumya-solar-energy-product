package com.saumyasolar.security;

import java.util.UUID;

/**
 * Explicit tenant scope for scheduled and asynchronous execution.
 * Always use try-with-resources so the scope is restored even after failures.
 */
public final class TenantExecutionContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantExecutionContext() {
    }

    public static Scope open(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant execution context is required");
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) throw new IllegalStateException("No tenant execution context is active");
        return tenantId;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
