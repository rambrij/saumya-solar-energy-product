package com.saumyasolar.config;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.RoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Configuration
public class PlatformAdminBootstrap {
    @Bean
    CommandLineRunner bootstrapPlatformAdmin(
            AppUserRepository users,
            RoleRepository roles,
            PasswordEncoder encoder,
            @Value("${app.bootstrap.platform-admin.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.platform-admin.email:}") String email,
            @Value("${app.bootstrap.platform-admin.password:}") String password
    ) {
        return args -> {
            if (!enabled) return;
            if (email == null || email.isBlank() || password == null || password.length() < 16)
                throw new IllegalStateException("Platform bootstrap requires an email and password of at least 16 characters");
            if (users.findByEmailIgnoreCase(email).isPresent())
                throw new IllegalStateException("Platform bootstrap email already exists; disable PLATFORM_BOOTSTRAP_ENABLED");
            var role = roles.findByNameAndTenantIdIsNull("PLATFORM_SUPER_ADMIN")
                    .orElseThrow(() -> new IllegalStateException("Global platform role is missing; run Flyway migrations first"));
            var user = new AppUser();
            user.setId(UUID.randomUUID());
            user.setTenantId(null);
            user.setName("Platform Super Admin");
            user.setEmail(email.trim().toLowerCase());
            user.setPasswordHash(encoder.encode(password));
            user.setAccountType("PLATFORM");
            user.setEnabled(true);
            user.getRoles().add(role);
            users.save(user);
        };
    }
}
