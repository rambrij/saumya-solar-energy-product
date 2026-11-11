package com.saumyasolar.service;

import com.saumyasolar.api.TenantAdminModels.*;
import com.saumyasolar.api.TenantCreate;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
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
    private final RefreshTokenRepository refreshTokens;
    private final AuditEventRepository audits;
    private final AuthenticationAuditService authenticationAudit;
    private final AuthLifecycleService lifecycle;

    public TenantAdminService(TenantRepository tenants,AppUserRepository users,RefreshTokenRepository refreshTokens,
                              AuditEventRepository audits,AuthenticationAuditService authenticationAudit,
                              AuthLifecycleService lifecycle) {
        this.tenants=tenants; this.users=users; this.refreshTokens=refreshTokens;
        this.audits=audits; this.authenticationAudit=authenticationAudit; this.lifecycle=lifecycle;
    }

    private void authorizeRead(Principal p) {
        if(!"PLATFORM".equals(p.accountType())||p.roles()==null
                ||p.roles().stream().noneMatch(java.util.Set.of("PLATFORM_SUPER_ADMIN","PLATFORM_SUPPORT")::contains)
                ||p.permissions()==null||!p.permissions().contains("TENANT_VIEW"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform tenant read permission is required");
        if(p.roles().contains("PLATFORM_SUPPORT")) {
            var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            var request=attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet?servlet.getRequest():null;
            authenticationAudit.record("PLATFORM_SUPPORT_TENANT_DIRECTORY_VIEWED",null,p.userId(),"SUCCESS","READ_ONLY_TENANT_DIRECTORY",null,request);
        }
    }

    private void authorize(Principal p) {
        if(!"PLATFORM".equals(p.accountType())||p.roles()==null||!p.roles().contains("PLATFORM_SUPER_ADMIN")
                ||p.permissions()==null||!p.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform Super Admin permission is required");
    }

    @Transactional
    public Tenant createTenant(TenantCreate input,Principal actor) {
        authorize(actor);
        Tenant created=lifecycle.createTenant(input.name(),input.slug(),input.adminName(),input.adminEmail(),input.adminPassword());
        audit(created,actor,"TENANT_CREATED",null,"PENDING","Tenant provisioned; verification and activation are required");
        return created;
    }

    private boolean matchesSearch(Tenant tenant,String search) {
        if(search==null||search.isBlank())return true;
        String term=search.toLowerCase(Locale.ROOT);
        if((tenant.getName()!=null&&tenant.getName().toLowerCase(Locale.ROOT).contains(term))
                ||(tenant.getSlug()!=null&&tenant.getSlug().toLowerCase(Locale.ROOT).contains(term)))return true;
        return users.findByTenantId(tenant.getId()).stream()
                .filter(user->user.getRoles().stream().anyMatch(role->"VENDOR_ADMIN".equals(role.getName())))
                .anyMatch(user->(user.getName()!=null&&user.getName().toLowerCase(Locale.ROOT).contains(term))
                        ||(user.getEmail()!=null&&user.getEmail().toLowerCase(Locale.ROOT).contains(term)));
    }

    private TenantRow row(Tenant t) {
        AppUser admin=users.findByTenantId(t.getId()).stream()
                .filter(u->u.getRoles().stream().anyMatch(r->"VENDOR_ADMIN".equals(r.getName())))
                .findFirst().orElse(null);
        return new TenantRow(t.getId(),t.getName(),t.getSlug(),admin==null?null:admin.getName(),
                admin==null?null:admin.getEmail(),t.getStatus(),t.getCreatedAt(),t.getUpdatedAt(),
                t.getVerifiedAt(),t.getActivatedAt(),t.getSuspendedAt(),t.getClosedAt(),t.getLifecycleReason());
    }

    public TenantPage page(Principal p,String search,String status,int page,int size) {
        authorizeRead(p);
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100");
        String normalized=status==null||status.isBlank()||"ALL".equalsIgnoreCase(status)?null:status.toUpperCase(Locale.ROOT);
        if(normalized!=null&&!List.of("ACTIVE","INACTIVE","SUSPENDED","PENDING","VERIFIED","CLOSED").contains(normalized))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported tenant status filter");
        var all=tenants.findAll(Sort.by(Sort.Direction.DESC,"createdAt"));
        var filtered=all.stream().filter(t->normalized==null||normalized.equals(t.getStatus()))
                .filter(t->matchesSearch(t,search)).toList();
        int from=Math.min(page*size,filtered.size()),to=Math.min(from+size,filtered.size());
        List<TenantRow> rows=filtered.subList(from,to).stream().map(this::row).toList();
        List<TenantRow> recent=all.stream().limit(5).map(this::row).toList();
        return new TenantPage(filtered.size(),page,size,(filtered.size()+size-1)/size,
                all.stream().filter(t->"ACTIVE".equals(t.getStatus())).count(),
                all.stream().filter(t->"INACTIVE".equals(t.getStatus())).count(),
                all.stream().filter(t->"SUSPENDED".equals(t.getStatus())).count(),
                all.stream().filter(t->"PENDING".equals(t.getStatus())).count(),
                all.stream().filter(t->"VERIFIED".equals(t.getStatus())).count(),
                all.stream().filter(t->"CLOSED".equals(t.getStatus())).count(),recent,rows);
    }

    public List<TenantRow> exportRows(Principal p,String search,String status) {
        TenantPage first=page(p,search,status,0,100);
        java.util.ArrayList<TenantRow> result=new java.util.ArrayList<>(first.items());
        for(int n=1;n<first.totalPages();n++)result.addAll(page(p,search,status,n,100).items());
        return List.copyOf(result);
    }

    public TenantRow get(UUID id,Principal p) {
        authorizeRead(p);
        return row(tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found")));
    }

    @Transactional
    public TenantRow transition(UUID id,TenantLifecycleInput input,Principal actor) {
        authorize(actor);
        Tenant t=tenants.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
        String from=t.getStatus(),to=input.status().trim().toUpperCase(Locale.ROOT),reason=input.reason().trim();
        boolean allowed=switch(from) {
            case "PENDING" -> "VERIFIED".equals(to);
            case "VERIFIED" -> "ACTIVE".equals(to);
            case "ACTIVE" -> "SUSPENDED".equals(to)||"CLOSED".equals(to);
            case "SUSPENDED" -> "ACTIVE".equals(to)||"CLOSED".equals(to);
            default -> false;
        };
        if(!allowed)throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tenant cannot transition from "+from+" to "+to+"; CLOSED is terminal");
        Instant now=Instant.now();
        switch(to) {
            case "VERIFIED" -> {t.setVerifiedAt(now);t.setVerifiedBy(actor.userId());}
            case "ACTIVE" -> {t.setActivatedAt(now);t.setActivatedBy(actor.userId());}
            case "SUSPENDED" -> {t.setSuspendedAt(now);t.setSuspendedBy(actor.userId());}
            case "CLOSED" -> {t.setClosedAt(now);t.setClosedBy(actor.userId());}
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported tenant lifecycle state");
        }
        t.setStatus(to);t.setLifecycleReason(reason);t.setUpdatedAt(now);
        Tenant saved=tenants.save(t);
        if("SUSPENDED".equals(to)||"CLOSED".equals(to))revokeTenantSessions(t.getId());
        String action=switch(to) {
            case "VERIFIED" -> "TENANT_VERIFIED";
            case "ACTIVE" -> "SUSPENDED".equals(from)?"TENANT_REACTIVATED":"TENANT_ACTIVATED";
            case "SUSPENDED" -> "TENANT_SUSPENDED";
            case "CLOSED" -> "TENANT_CLOSED";
            default -> "TENANT_LIFECYCLE_CHANGED";
        };
        audit(saved,actor,action,from,to,reason);
        return row(saved);
    }

    private void revokeTenantSessions(UUID tenantId) {
        for(AppUser user:users.findByTenantId(tenantId)) {
            refreshTokens.findByUserIdAndRevokedFalse(user.getId()).forEach(token->{token.setRevoked(true);refreshTokens.save(token);});
            user.setTokenVersion(user.getTokenVersion()+1);
            users.save(user);
        }
    }

    private void audit(Tenant tenant,Principal actor,String action,String before,String after,String reason) {
        AuditEvent e=new AuditEvent();e.setId(UUID.randomUUID());e.setTenantId(tenant.getId());
        e.setActorId(actor.userId());e.setActorType("USER");e.setAction(action);e.setEntityType("TENANT");
        e.setEntityId(tenant.getId().toString());e.setOutcome("SUCCESS");e.setBeforeJson("{\"status\":\""+(before==null?"":before)+"\"}");
        e.setAfterJson("{\"status\":\""+after+"\"}");e.setReason(reason);e.setCreatedAt(Instant.now());audits.save(e);
    }

    @Transactional
    public TenantRow update(UUID id,TenantUpdate input,Principal p) {
        authorize(p);
        Tenant t=tenants.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
        String name=input.name().trim();if(name.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tenant name is required");
        t.setName(name);t.setUpdatedAt(Instant.now());return row(tenants.save(t));
    }
}