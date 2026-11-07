package com.saumyasolar.service;

import com.saumyasolar.api.TenantAdminModels.*;
import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantAdminService {
    private final TenantRepository tenants;
    private final AppUserRepository users;
    public TenantAdminService(TenantRepository tenants, AppUserRepository users) {
        this.tenants = tenants; this.users = users;
    }
    private void authorize(Principal p) {
        if (!"PLATFORM".equals(p.accountType()) || p.roles()==null ||
            !p.roles().contains("PLATFORM_SUPER_ADMIN") || p.permissions()==null ||
            !p.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Super Admin permission is required");
    }
    private TenantRow row(Tenant t) {
        AppUser admin = users.findByTenantId(t.getId()).stream()
            .filter(u -> u.getRoles().stream().anyMatch(r -> "VENDOR_ADMIN".equals(r.getName())))
            .findFirst().orElse(null);
        return new TenantRow(t.getId(), t.getName(), t.getSlug(),
            admin==null?null:admin.getName(), admin==null?null:admin.getEmail(),
            t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
    public TenantPage page(Principal p, String search, String status, int page, int size) {
        authorize(p);
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
        String normalized = status==null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT);
        if (normalized!=null && !List.of("ACTIVE","INACTIVE","SUSPENDED").contains(normalized))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tenant status filter");
        var all = tenants.findAll(Sort.by(Sort.Direction.DESC,"createdAt"));
        var filtered = all.stream()
            .filter(t -> normalized==null || normalized.equals(t.getStatus()))
            .filter(t -> search==null || search.isBlank() ||
                t.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) ||
                t.getSlug().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) ||
                users.findByTenantId(t.getId()).stream().anyMatch(u ->
                    u.getRoles().stream().anyMatch(r -> "VENDOR_ADMIN".equals(r.getName())) &&
                    ((u.getName()!=null && u.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) ||
                     (u.getEmail()!=null && u.getEmail().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))))))
            .toList();
        int from = Math.min(page*size, filtered.size()), to=Math.min(from+size,filtered.size());
        List<TenantRow> rows=filtered.subList(from,to).stream().map(this::row).toList();
        List<TenantRow> recent=all.stream().limit(5).map(this::row).toList();
        return new TenantPage(filtered.size(),page,size,(filtered.size()+size-1)/size,
            all.stream().filter(t->"ACTIVE".equals(t.getStatus())).count(),
            all.stream().filter(t->"INACTIVE".equals(t.getStatus())).count(),
            all.stream().filter(t->"SUSPENDED".equals(t.getStatus())).count(),recent,rows);
    }
    public List<TenantRow> exportRows(Principal p, String search, String status) {
        TenantPage first=page(p,search,status,0,100);
        java.util.ArrayList<TenantRow> result=new java.util.ArrayList<>(first.items());
        for(int n=1;n<first.totalPages();n++) result.addAll(page(p,search,status,n,100).items());
        return List.copyOf(result);
    }
    public TenantRow get(UUID id, Principal p) {
        authorize(p);
        return row(tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found")));
    }
    @Transactional
    public TenantRow update(UUID id, TenantUpdate input, Principal p) {
        authorize(p);
        Tenant t=tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
        String name=input.name().trim();
        if(name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tenant name is required");
        t.setName(name); t.setUpdatedAt(Instant.now());
        return row(tenants.save(t));
    }
}
