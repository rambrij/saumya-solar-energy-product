package com.saumyasolar.security;

import java.util.Set;
import java.util.UUID;
public record Principal(
        UUID userId, UUID tenantId, String email, String accountType, Set<String> roles, Set<String> permissions,
        int tokenVersion
) {
    public Principal(
            UUID userId,
            UUID tenantId,
            String email,
            String accountType,
            Set<String> roles,
            Set<String> permissions
    ) {
        this(userId, tenantId, email, accountType, roles, permissions, 0);
    }
}
