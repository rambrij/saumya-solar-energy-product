package com.saumyasolar.security;

import java.util.Set;
import java.util.UUID;
public record Principal(
        UUID userId, UUID tenantId, String email, String accountType, Set<String> roles, Set<String> permissions
) {
}
