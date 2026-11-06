package com.saumyasolar.security;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Permission;
import com.saumyasolar.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
@Service
public class JwtService {
    private final SecretKey key;
    
    JwtService(@Value("${app.jwt-secret}") String s) {
        key = Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }
    
    public String issue(AppUser u) {
        var rs = u.getRoles().stream().map(Role::getName).toList();
        var ps = u.getRoles().stream().flatMap(r -> r.getPermissions().stream()).map(Permission::getCode).distinct().toList();
        return Jwts.builder().subject(u.getId().toString()).claim("tenant", u.getTenantId() == null ? null : u.getTenantId().toString()).claim("email", u.getEmail()).claim("type", u.getAccountType()).claim("roles", rs).claim("permissions", ps).expiration(Date.from(Instant.now().plusSeconds(7200))).signWith(key).compact();
    }
    
    public Principal parse(String t) {
        var c = Jwts.parser().verifyWith(key).build().parseSignedClaims(t).getPayload();
        var tenant = c.get("tenant", String.class);
        return new Principal(UUID.fromString(c.getSubject()), tenant == null ? null : UUID.fromString(tenant), c.get("email", String.class), c.get("type", String.class), new HashSet<>((List<String>) c.get("roles", List.class)), new HashSet<>((List<String>) c.get("permissions", List.class)));
    }
}
