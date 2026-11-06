package com.saumyasolar.api;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.saumyasolar.api.Models.*;
import com.saumyasolar.domain.CustomerOrder;
import com.saumyasolar.domain.Quotation;
import com.saumyasolar.domain.SalesLead;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.QuotationItemRepository;
import com.saumyasolar.security.JwtService;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.AppService;
import com.saumyasolar.service.AuthLifecycleService;
import com.saumyasolar.service.QuotationRevisionService;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
@RestController
@RequestMapping("/api/v1")
public class Controller {
    final AppService s;
    final AppUserRepository users;
    final JwtService jwt;
    final PasswordEncoder enc;
    final QuotationItemRepository items;
    final QuotationRevisionService revisions;
    final AuthLifecycleService lifecycle;
    
    Controller(
            AppService s,
            AppUserRepository u,
            JwtService j,
            PasswordEncoder e,
            QuotationItemRepository i,
            QuotationRevisionService r,
            AuthLifecycleService l
    ) {
        this.s = s;
        users = u;
        jwt = j;
        enc = e;
        items = i;
        revisions = r;
        lifecycle = l;
    }
    
    Principal p(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    @PostMapping("/auth/login")
    Session login(@Valid @RequestBody Login x) {
        var u = users.findByEmailIgnoreCase(x.email()).filter(v -> v.isEnabled() && enc.matches(x.password(), v.getPasswordHash())).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var rs = new HashSet<String>();
        var ps = new HashSet<String>();
        u.getRoles().forEach(r -> {
            rs.add(r.getName());
            r.getPermissions().forEach(z -> ps.add(z.getCode()));
        });
        return new Session(jwt.issue(u), lifecycle.newRefresh(u), new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), rs, ps));
    }
    
    @PostMapping("/auth/refresh")
    Session refresh(@Valid @RequestBody Refresh x) {
        var u = lifecycle.consumeRefresh(x.refreshToken());
        var rs = new HashSet<String>();
        var ps = new HashSet<String>();
        u.getRoles().forEach(r -> {
            rs.add(r.getName());
            r.getPermissions().forEach(z -> ps.add(z.getCode()));
        });
        return new Session(jwt.issue(u), lifecycle.newRefresh(u), new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), rs, ps));
    }
    
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody Refresh x) {
        lifecycle.logout(x.refreshToken());
    }
    
    @PostMapping("/users")
    UserView createVendorEmployee(@Valid @RequestBody EmployeeCreate x, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("USER_MANAGE"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        var u = lifecycle.createEmployee(actor.tenantId(), x.name(), x.email(), x.password(), x.roleName());
        var roleNames = new HashSet<String>();
        var permissionCodes = new HashSet<String>();
        u.getRoles().forEach(r -> {
            roleNames.add(r.getName());
            r.getPermissions().forEach(permission -> permissionCodes.add(permission.getCode()));
        });
        return new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), roleNames, permissionCodes);
    }

    @PostMapping("/auth/register")
    Session register(@Valid @RequestBody Register x) {
        var u = lifecycle.register(x.storeSlug(), x.name(), x.email(), x.password());
        return new Session(jwt.issue(u), lifecycle.newRefresh(u), new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), Set.of("CUSTOMER"), Set.of()));
    }
    
    @PostMapping("/auth/forgot-password")
    Map<String, String> forgot(@Valid @RequestBody PasswordReset x) {
        return lifecycle.forgot(x.email());
    }
    
    @PostMapping("/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody PasswordReset x) {
        lifecycle.reset(x.token(), x.newPassword());
    }
    
    @GetMapping("/me")
    Principal me(Authentication a) {
        return p(a);
    }
    
    @GetMapping(
            "/{type:tenants|users|categories|products|packages|customers|enquiries|leads|surveys|quotations|orders|customer-orders}"
    )
    Object list(@PathVariable String type, Authentication a) {
        return s.list(type, p(a));
    }
    
    @PostMapping("/{type:tenants|categories|products|packages|customers|enquiries}")
    Object create(@PathVariable String type, @RequestBody Input x, Authentication a) {
        var principal = p(a);
        if ("tenants".equals(type)) {
            if (!"PLATFORM".equals(principal.accountType()) || principal.permissions() == null
                    || !principal.permissions().contains("PLATFORM_MANAGE"))
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
            return lifecycle.createTenant(x.name(), x.slug(), x.adminName(), x.adminEmail(), x.adminPassword());
        }
        return s.create(type, x, principal);
    }
    
    @PostMapping("/enquiries/{id}/convert")
    SalesLead convert(@PathVariable UUID id, @RequestBody Input x, Authentication a) {
        return s.convertLead(id, x, p(a));
    }
    
    @PatchMapping("/leads/{id}")
    SalesLead lead(@PathVariable UUID id, @RequestBody Input x, Authentication a) {
        return s.updateLead(id, x, p(a));
    }
    
    @PostMapping("/surveys")
    SiteSurvey survey(@RequestBody Input x, Authentication a) {
        return s.survey(x, p(a));
    }
    
    @PostMapping("/quotations")
    QuoteView quote(@RequestBody Input x, Authentication a) {
        return s.quote(x, p(a));
    }
    
    @PostMapping("/quotations/{id}/{action:approve|reject-approval|send|accept|reject}")
    Quotation action(@PathVariable UUID id, @PathVariable String action, Authentication a) {
        return s.action(id, action, p(a));
    }
    
    @PostMapping("/quotations/{id}/convert-to-order")
    CustomerOrder order(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, Authentication a) {
        return s.order(id, key, p(a));
    }
    
    @PostMapping("/quotations/{id}/revisions")
    QuoteView revise(@PathVariable UUID id, Authentication a) {
        return revisions.revise(id, p(a));
    }
    
    @GetMapping(value = "/quotations/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] pdf(@PathVariable UUID id, Authentication a) throws Exception {
        var q = ( (List<Quotation>) s.list("quotations", p(a)) ).stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
        try (var out = new ByteArrayOutputStream()) {
            var d = new Document();
            PdfWriter.getInstance(d, out);
            d.open();
            d.add(new Paragraph("Saumya Solar Quotation " + q.getQuotationNumber() + " R" + q.getRevision()));
            d.add(new Paragraph("Status: " + q.getStatus() + "  Total INR: " + q.getGrandTotal()));
            d.close();
            return out.toByteArray();
        }
    }
}
