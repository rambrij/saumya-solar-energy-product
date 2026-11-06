package com.saumyasolar.security;

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
    
    JwtFilter(JwtService j) {
        this.j = j;
    }
    
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
            throws ServletException, IOException {
        var h = r.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) try {
            var p = j.parse(h.substring(7));
            var a = new ArrayList<SimpleGrantedAuthority>();
            p.roles().forEach(x -> a.add(new SimpleGrantedAuthority("ROLE_" + x)));
            p.permissions().forEach(x -> a.add(new SimpleGrantedAuthority(x)));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, a));
        } catch (Exception ignored) {
        }
        c.doFilter(r, s);
    }
}
