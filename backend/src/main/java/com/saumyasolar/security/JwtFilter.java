package com.saumyasolar.security;

import com.saumyasolar.domain.AppUser;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService j;
    private final AppUserRepository users;
    private final TenantRepository tenants;

    JwtFilter(JwtService j, AppUserRepository users, TenantRepository tenants) {
        this.j = j;
        this.users = users;
        this.tenants = tenants;
    }
    
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
            throws ServletException, IOException {
        var h = r.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) try {
            var tokenPrincipal = j.parse(h.substring(7));
            var user = users.findById(tokenPrincipal.userId()).filter(AppUser::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("User is disabled or no longer exists"));
            if (!java.util.Objects.equals(user.getTenantId(), tokenPrincipal.tenantId())
                    || !java.util.Objects.equals(user.getAccountType(), tokenPrincipal.accountType()))
                throw new IllegalArgumentException("User security context changed");
            if (user.getTenantId() != null && tenants.findById(user.getTenantId())
                    .filter(t -> "ACTIVE".equals(t.getStatus())).isEmpty())
                throw new IllegalArgumentException("Tenant is suspended");
            var roleNames = new java.util.HashSet<String>();
            var permissionCodes = new java.util.HashSet<String>();
            user.getRoles().forEach(role -> {
                roleNames.add(role.getName());
                role.getPermissions().forEach(permission -> permissionCodes.add(permission.getCode()));
            });
            var p = new Principal(user.getId(), user.getTenantId(), user.getEmail(), user.getAccountType(), roleNames, permissionCodes);
            var a = new ArrayList<SimpleGrantedAuthority>();
            p.roles().forEach(x -> a.add(new SimpleGrantedAuthority("ROLE_" + x)));
            p.permissions().forEach(x -> a.add(new SimpleGrantedAuthority(x)));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, a));
        } catch (Exception ignored) {
        }
        c.doFilter(r, s);
    }
}
