package com.saumyasolar.service;

import com.saumyasolar.api.VendorProfileModels.VendorProfileInput;
import com.saumyasolar.api.VendorProfileModels.VendorProfileView;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class VendorProfileService {
    private final TenantRepository tenants;
    public VendorProfileService(TenantRepository tenants) { this.tenants = tenants; }

    private Tenant vendorAdmin(Principal actor) {
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.roles() == null || !actor.roles().contains("VENDOR_ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendor administrator access is required");
        return tenants.findById(actor.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    public VendorProfileView get(Principal actor) { return view(vendorAdmin(actor)); }

    @Transactional
    public VendorProfileView update(VendorProfileInput input, Principal actor) {
        Tenant tenant = vendorAdmin(actor);
        String gstin = input.gstin() == null ? null : input.gstin().trim().toUpperCase(java.util.Locale.ROOT);
        if (gstin != null && !gstin.isBlank() && !gstin.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GSTIN format is invalid");
        tenant.setLegalName(input.legalName().trim());
        tenant.setContactPhone(input.contactPhone().trim());
        tenant.setRegisteredAddress(input.registeredAddress().trim());
        tenant.setContactEmail(input.contactEmail() == null ? null : input.contactEmail().trim().toLowerCase(java.util.Locale.ROOT));
        tenant.setGstin(gstin);
        tenant.setWebsite(input.website() == null ? null : input.website().trim());
        tenant.setUpdatedAt(Instant.now());
        return view(tenants.save(tenant));
    }

    private VendorProfileView view(Tenant tenant) {
        return new VendorProfileView(tenant.getId(), tenant.getName(), tenant.getLegalName(), tenant.getContactPhone(),
                tenant.getRegisteredAddress(), tenant.getContactEmail(), tenant.getGstin(), tenant.getWebsite());
    }
}
