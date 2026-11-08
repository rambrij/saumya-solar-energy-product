package com.saumyasolar.service;

import com.saumyasolar.api.TenantAdminModels.*;
import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.repo.VendorVerificationDocumentRepository;
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
    private final NotificationService notifications;
    private final VendorVerificationDocumentRepository verificationDocuments;
    private final AuthLifecycleService lifecycle;
    public TenantAdminService(TenantRepository tenants, AppUserRepository users, NotificationService notifications,
            VendorVerificationDocumentRepository verificationDocuments, AuthLifecycleService lifecycle) {
        this.tenants = tenants; this.users = users; this.notifications = notifications;
        this.verificationDocuments = verificationDocuments;
        this.lifecycle = lifecycle;
    }
    private void authorize(Principal p) {
        if (!"PLATFORM".equals(p.accountType()) || p.roles()==null ||
            !p.roles().contains("PLATFORM_SUPER_ADMIN") || p.permissions()==null ||
            !p.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Super Admin permission is required");
    }
    private boolean matchesSearch(Tenant tenant, String search) {
        if (search == null || search.isBlank()) return true;
        String term = search.toLowerCase(Locale.ROOT);
        if ((tenant.getName()!=null && tenant.getName().toLowerCase(Locale.ROOT).contains(term))
                || (tenant.getSlug()!=null && tenant.getSlug().toLowerCase(Locale.ROOT).contains(term))) return true;
        return users.findByTenantId(tenant.getId()).stream()
            .filter(user -> user.getRoles().stream().anyMatch(role -> "VENDOR_ADMIN".equals(role.getName())))
            .anyMatch(user -> (user.getName()!=null && user.getName().toLowerCase(Locale.ROOT).contains(term))
                || (user.getEmail()!=null && user.getEmail().toLowerCase(Locale.ROOT).contains(term)));
    }
    private TenantRow row(Tenant t) {
        AppUser admin = users.findByTenantId(t.getId()).stream()
            .filter(u -> u.getRoles().stream().anyMatch(r -> "VENDOR_ADMIN".equals(r.getName())))
            .findFirst().orElse(null);
        return new TenantRow(t.getId(), t.getName(), t.getSlug(),
            admin==null?null:admin.getName(), admin==null?null:admin.getEmail(),
            t.getStatus(), t.getVerificationStatus(), t.getVerifiedAt(), t.getRejectionReason(), t.getCreatedAt(), t.getUpdatedAt(),
            t.getLegalName(), t.getContactPhone(), t.getRegisteredAddress(), t.getContactEmail(), t.getGstin(), t.getWebsite());
    }
    public TenantPage page(Principal p, String search, String status, int page, int size) {
        authorize(p);
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
        String normalized = status==null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT);
        if (normalized!=null && !List.of("ACTIVE","INACTIVE","SUSPENDED").contains(normalized))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tenant status filter");
        var all = tenants.findAll(Sort.by(Sort.Direction.DESC,"createdAt"));
        var filtered = all.stream()
            .filter(t -> normalized == null || normalized.equals(t.getStatus()))
            .filter(t -> matchesSearch(t, search))
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
    public TenantRow verify(UUID id, String decision, String reason, Principal p) {
        authorize(p);
        Tenant t=tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
        String action=decision==null?"":decision.trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(action)) {
            if (t.getLegalName() == null || t.getLegalName().isBlank()
                    || t.getContactPhone() == null || t.getContactPhone().isBlank()
                    || t.getRegisteredAddress() == null || t.getRegisteredAddress().isBlank())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete the vendor legal profile before approval");
            if (!verificationDocuments.existsByTenantIdAndReviewStatus(id, "APPROVED"))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one vendor verification document must be reviewed and approved");
            t.setVerificationStatus("VERIFIED");
            t.setVerifiedAt(Instant.now());
            t.setVerifiedBy(p.userId());
            t.setRejectionReason(null);
            t.setStatus("ACTIVE");
        } else if ("REJECT".equals(action)) {
            if (reason==null || reason.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A rejection reason is required");
            t.setVerificationStatus("REJECTED");
            t.setVerifiedAt(null);
            t.setVerifiedBy(p.userId());
            t.setRejectionReason(reason.trim().substring(0,Math.min(1000,reason.trim().length())));
            t.setStatus("INACTIVE");
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision must be APPROVE or REJECT");
        }
        t.setUpdatedAt(Instant.now());
        Tenant saved=tenants.save(t);
        AppUser admin=users.findByTenantId(saved.getId()).stream()
                .filter(user -> user.getRoles().stream().anyMatch(role -> "VENDOR_ADMIN".equals(role.getName())))
                .findFirst().orElse(null);
        if (admin!=null) {
            String event="APPROVE".equals(action)?"VENDOR_VERIFIED":"VENDOR_VERIFICATION_REJECTED";
            String subject="APPROVE".equals(action)?"Your vendor account is verified":"Vendor verification needs attention";
            String message="Hello "+admin.getName()+",\n\n"
                    + ("APPROVE".equals(action)
                    ? "Your Soumya Solar vendor account has been verified and is now active."
                    : "Your vendor verification was not approved. Reason: "+saved.getRejectionReason())
                    + "\n\nPlease contact the platform administrator if you need assistance.";
            notifications.sendBusinessNotification(admin.getId(),saved.getId(),event,admin.getEmail(),subject,message);
        }
        return row(saved);
    }

    @Transactional
    public void resendInvitation(UUID id, Principal p) {
        authorize(p);
        tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found"));
        lifecycle.resendVendorInvitation(id,p.userId());
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
