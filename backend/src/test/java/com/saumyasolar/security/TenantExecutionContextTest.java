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
}
