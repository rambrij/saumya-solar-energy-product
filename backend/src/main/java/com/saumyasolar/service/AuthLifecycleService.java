package com.saumyasolar.service;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.PasswordResetToken;
import com.saumyasolar.domain.RefreshToken;
import com.saumyasolar.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@Service
public class AuthLifecycleService {
    final RefreshTokenRepository refresh;
    final PasswordResetTokenRepository resets;
    final AppUserRepository users;
    final TenantRepository tenants;
    final RoleRepository roles;
    final PasswordEncoder encoder;
    
    AuthLifecycleService(
            RefreshTokenRepository r,
            PasswordResetTokenRepository p,
            AppUserRepository u,
            TenantRepository t,
            RoleRepository rr,
            PasswordEncoder e
    ) {
        refresh = r;
        resets = p;
        users = u;
        tenants = t;
        roles = rr;
        encoder = e;
    }
    
    public String newRefresh(AppUser u) {
        var x = new RefreshToken();
        x.setId(UUID.randomUUID());
        x.setUserId(u.getId());
        x.setToken(UUID.randomUUID() + "." + UUID.randomUUID());
        x.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        refresh.save(x);
        return x.getToken();
    }
    
    public AppUser consumeRefresh(String token) {
        var x = refresh.findByToken(token).filter(v -> !v.isRevoked() && v.getExpiresAt().isAfter(Instant.now())).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        x.setRevoked(true);
        refresh.save(x);
        return users.findById(x.getUserId()).orElseThrow();
    }
    
    public void logout(String token) {
        refresh.findByToken(token).ifPresent(x -> {
            x.setRevoked(true);
            refresh.save(x);
        });
    }
    
    public Map<String, String> forgot(String email) {
        var out = new HashMap<String, String>();
        out.put("message", "If the account exists, reset instructions were created");
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            var x = new PasswordResetToken();
            x.setId(UUID.randomUUID());
            x.setUserId(u.getId());
            x.setToken(UUID.randomUUID().toString());
            x.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
            resets.save(x);
            out.put("devResetToken", x.getToken());
        });
        return out;
    }
    
    public void reset(String token, String password) {
        var x = resets.findByToken(token).filter(v -> !v.isUsed() && v.getExpiresAt().isAfter(Instant.now())).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        var u = users.findById(x.getUserId()).orElseThrow();
        u.setPasswordHash(encoder.encode(password));
        users.save(u);
        x.setUsed(true);
        resets.save(x);
    }
    
    @Transactional
    public AppUser register(String slug, String name, String email, String password) {
        var t = tenants.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email exists");
        var u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(t.getId());
        u.setName(name);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setAccountType("CUSTOMER");
        u.setEnabled(true);
        u.getRoles().add(roles.findByNameAndTenantIdIsNull("CUSTOMER").orElseThrow());
        return users.save(u);
    }
}
