package com.saumyasolar;

import com.saumyasolar.security.Principal;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
class Release2SecurityTest {
    @Test
    void principalsKeepTenantRoleAndPermissionBoundaries() {
        var t = UUID.randomUUID();
        var p = new Principal(UUID.randomUUID(), t, "admin@test", "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("CATALOG_MANAGE"));
        assertEquals(t, p.tenantId());
        assertTrue(p.permissions().contains("CATALOG_MANAGE"));
        assertFalse(p.roles().contains("PLATFORM_SUPER_ADMIN"));
    }
    
    @Test
    void platformPrincipalHasNoTenant() {
        var p = new Principal(UUID.randomUUID(), null, "platform@test", "PLATFORM", Set.of("PLATFORM_SUPER_ADMIN"), Set.of("PLATFORM_MANAGE"));
        assertNull(p.tenantId());
    }
}
