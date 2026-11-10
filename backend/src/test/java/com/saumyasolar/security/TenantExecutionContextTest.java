package com.saumyasolar.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantExecutionContextTest {
    @Test
    void restoresPreviousTenantAndClearsThreadLocalAfterScope() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId);
        try (var outer = TenantExecutionContext.open(first)) {
            assertEquals(first, TenantExecutionContext.requireTenantId());
            try (var inner = TenantExecutionContext.open(second)) {
                assertEquals(second, TenantExecutionContext.requireTenantId());
            }
            assertEquals(first, TenantExecutionContext.requireTenantId());
        }
        assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId);
    }

    @Test
    void rejectsNullTenantScope() {
        assertThrows(IllegalArgumentException.class, () -> TenantExecutionContext.open(null));
    }
    @Test
    void clearsTenantContextAfterExceptionOnReusedWorkerThread() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        UUID tenant = UUID.randomUUID();
        try {
            var failed = executor.submit(() -> {
                try (var scope = TenantExecutionContext.open(tenant)) {
                    assertEquals(tenant, TenantExecutionContext.requireTenantId());
                    throw new IllegalStateException("simulated job failure");
                }
            });
            assertThrows(java.util.concurrent.ExecutionException.class, failed::get);
            UUID nextTenant = UUID.randomUUID();
            var next = executor.submit(() -> {
                assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId);
                try (var scope = TenantExecutionContext.open(nextTenant)) {
                    return TenantExecutionContext.requireTenantId();
                }
            });
            assertEquals(nextTenant, next.get());
            var after = executor.submit(() -> assertThrows(
                    IllegalStateException.class, TenantExecutionContext::requireTenantId));
            assertDoesNotThrow(after::get);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void restoresOuterTenantWhenInnerScopeThrows() {
        UUID outerTenant = UUID.randomUUID();
        UUID innerTenant = UUID.randomUUID();
        try (var outer = TenantExecutionContext.open(outerTenant)) {
            assertThrows(IllegalArgumentException.class, () -> {
                try (var inner = TenantExecutionContext.open(innerTenant)) {
                    assertEquals(innerTenant, TenantExecutionContext.requireTenantId());
                    throw new IllegalArgumentException("simulated nested job failure");
                }
            });
            assertEquals(outerTenant, TenantExecutionContext.requireTenantId());
        }
        assertThrows(IllegalStateException.class, TenantExecutionContext::requireTenantId);
    }

}
