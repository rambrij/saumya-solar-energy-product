package com.saumyasolar.api;

import io.swagger.v3.oas.annotations.Operation;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.saumyasolar.api.Models.*;
import com.saumyasolar.api.TenantCreate;
import com.saumyasolar.api.OperationsModels.*;
import com.saumyasolar.api.CatalogModels.*;
import com.saumyasolar.domain.CustomerOrder;
import com.saumyasolar.domain.Quotation;
import com.saumyasolar.domain.SalesLead;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.QuotationItemRepository;
import com.saumyasolar.security.JwtService;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.AppService;
import com.saumyasolar.service.AuthLifecycleService;
import com.saumyasolar.service.QuotationRevisionService;
import com.saumyasolar.service.OperationsService;
import com.saumyasolar.service.CatalogAdminService;
import com.saumyasolar.service.PackageImageStorageService;
import com.saumyasolar.service.TenantAdminService;
import com.saumyasolar.api.TenantAdminModels.TenantUpdate;
import com.saumyasolar.api.TenantAdminModels.TenantPage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.time.LocalDate;

import org.springframework.web.multipart.MultipartFile;
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
    final CustomerProfileRepository customers;
    final QuotationRevisionService revisions;
    final AuthLifecycleService lifecycle;
    final OperationsService operations;
    final CatalogAdminService catalog;
    final PackageImageStorageService packageImages;
    final TenantAdminService tenantAdmin;
    final com.saumyasolar.service.SurveyLifecycleService surveyLifecycle;
    final com.saumyasolar.service.EnterpriseSaasService enterpriseSaas;
    final boolean platformMfaEnabled;
    final com.saumyasolar.security.AuthCookieService authCookies;
    
    Controller(
            AppService s,
            AppUserRepository u,
            JwtService j,
            PasswordEncoder e,
            QuotationItemRepository i,
            CustomerProfileRepository customerProfiles,
            QuotationRevisionService r,
            AuthLifecycleService l,
            OperationsService operations,
            CatalogAdminService catalog,
            PackageImageStorageService packageImages,
            TenantAdminService tenantAdmin,
            com.saumyasolar.service.SurveyLifecycleService surveyLifecycle,
            com.saumyasolar.service.EnterpriseSaasService enterpriseSaas,
            @org.springframework.beans.factory.annotation.Value("${app.auth.platform-mfa-enabled:true}")
            boolean platformMfaEnabled,
            com.saumyasolar.security.AuthCookieService authCookies
    ) {
        this.s = s;
        users = u;
        jwt = j;
        enc = e;
        items = i;
        customers = customerProfiles;
        revisions = r;
        lifecycle = l;
        this.operations = operations;
        this.catalog = catalog;
        this.packageImages = packageImages;
        this.tenantAdmin = tenantAdmin;
        this.surveyLifecycle = surveyLifecycle;
        this.enterpriseSaas = enterpriseSaas;
        this.platformMfaEnabled = platformMfaEnabled;
        this.authCookies = authCookies;
    }
    
    Principal p(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    @PostMapping("/auth/login")
    Object login(@Valid @RequestBody Login x, jakarta.servlet.http.HttpServletResponse response) {
        var u = lifecycle.authenticate(x.email(), x.password());
        if (platformMfaEnabled && "PLATFORM".equals(u.getAccountType())) {
            return new MfaChallengeRequired(true, lifecycle.createMfaChallenge(u));
        }
        var rs = new HashSet<String>();
        var ps = new HashSet<String>();
        u.getRoles().forEach(r -> {
            rs.add(r.getName());
            r.getPermissions().forEach(z -> ps.add(z.getCode()));
        });
        String accessToken = jwt.issue(u);
        String csrfToken = authCookies.issue(response, accessToken, lifecycle.newRefresh(u));
        return new Session(null, csrfToken, new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), rs, ps));
    }
    
    @PostMapping("/auth/mfa/verify")
    Session verifyMfa(@Valid @RequestBody MfaVerify input, jakarta.servlet.http.HttpServletResponse response) {
        var u = lifecycle.verifyMfaChallenge(input.challengeId(), input.code());
        var roleNames = new HashSet<String>();
        var permissionCodes = new HashSet<String>();
        u.getRoles().forEach(role -> {
            roleNames.add(role.getName());
            role.getPermissions().forEach(permission -> permissionCodes.add(permission.getCode()));
        });
        String accessToken = jwt.issue(u);
        String csrfToken = authCookies.issue(response, accessToken, lifecycle.newRefresh(u));
        return new Session(
                null, csrfToken,
                new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), roleNames, permissionCodes)
        );
    }
    
    @PostMapping("/auth/refresh")
    Session refresh(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        authCookies.requireCsrf(request);
        String refreshToken = authCookies.refreshToken(request);
        if (refreshToken == null || refreshToken.isBlank())
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh session is unavailable");
        var rotation = lifecycle.rotateRefresh(refreshToken);
        String accessToken = jwt.issue(u);
        String csrfToken = authCookies.issue(response, accessToken, rotation.refreshToken());
        var u = rotation.user();
        var rs = new HashSet<String>();
        var ps = new HashSet<String>();
        u.getRoles().forEach(r -> {
            rs.add(r.getName());
            r.getPermissions().forEach(z -> ps.add(z.getCode()));
        });
        return new Session(null, csrfToken, new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), rs, ps));
    }
    
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        authCookies.requireCsrf(request);
        lifecycle.logout(authCookies.refreshToken(request));
        authCookies.clear(response);
    }
    
    @PatchMapping("/users/{userId}/role")
    com.saumyasolar.domain.AppUser assignEmployeeRole(@PathVariable UUID userId, @Valid @RequestBody EmployeeRoleAssignment x, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("ROLE_ASSIGN"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return lifecycle.assignEmployeeRole(actor.tenantId(), actor.userId(), userId, x.roleName());
    }

    @PostMapping("/users")
    UserView createVendorEmployee(@Valid @RequestBody EmployeeCreate x, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null
                || !actor.permissions().contains("USER_CREATE")
                || !actor.permissions().contains("ROLE_ASSIGN"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        enterpriseSaas.enforceUserLimit(actor.tenantId());
        var u = lifecycle.createEmployee(actor.tenantId(), actor.userId(), x.name(), x.email(), x.password(), x.roleName());
        var roleNames = new HashSet<String>();
        var permissionCodes = new HashSet<String>();
        u.getRoles().forEach(r -> {
            roleNames.add(r.getName());
            r.getPermissions().forEach(permission -> permissionCodes.add(permission.getCode()));
        });
        return new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), roleNames, permissionCodes);
    }
    
    @PostMapping("/auth/register")
    Session register(@Valid @RequestBody Register x, jakarta.servlet.http.HttpServletResponse response) {
        var u = lifecycle.register(x.storeSlug(), x.name(), x.email(), x.password());
        String csrfToken = authCookies.issue(response, lifecycle.newRefresh(u));
        return new Session(jwt.issue(u), csrfToken, new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), Set.of("CUSTOMER"), Set.of()));
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
            "/{type:tenants|users|categories|products|packages|customers|enquiries|leads|surveys|quotations|orders|customer-orders|catalog-categories|catalog-products|catalog-packages|customer-enquiries}"
    )
    Object list(@PathVariable String type, Authentication a) {
        return s.list(type, p(a));
    }
    
    @GetMapping({ "/tenant/settings", "/settings" })
    Tenant tenantSettings(Authentication a) {
        return s.tenantSettings(p(a));
    }
    
    @PatchMapping({ "/tenant/settings/discount-threshold", "/settings/discount-threshold" })
    Tenant discountThreshold(@RequestBody Input x, Authentication a) {
        return s.updateDiscountApprovalThreshold(x.discountPercent(), p(a));
    }
    
    @GetMapping("/roles")
    List<com.saumyasolar.domain.Role> employeeRoles(Authentication a) {
        return s.employeeRoles(p(a));
    }

    @GetMapping("/permissions")
    List<com.saumyasolar.domain.Permission> vendorRolePermissions(Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("ROLE_VIEW"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return lifecycle.vendorRolePermissions();
    }

    @PostMapping("/roles")
    com.saumyasolar.domain.Role createCustomRole(@Valid @RequestBody RolePermissionRequest x, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("ROLE_CREATE"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return lifecycle.createCustomRole(actor.tenantId(), actor.userId(), x.name(), x.permissions());
    }

    @PatchMapping("/roles/{roleId}")
    com.saumyasolar.domain.Role updateCustomRole(@PathVariable UUID roleId, @Valid @RequestBody RolePermissionRequest x, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("ROLE_UPDATE"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return lifecycle.updateCustomRole(actor.tenantId(), actor.userId(), roleId, x.permissions());
    }

    @DeleteMapping("/roles/{roleId}")
    void deleteCustomRole(@PathVariable UUID roleId, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("ROLE_UPDATE"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        lifecycle.deleteCustomRole(actor.tenantId(), actor.userId(), roleId);
    }
    
    @Operation(
            summary = "Create customer enquiry",
            description = "Creates a tenant-scoped enquiry for the authenticated customer and optionally snapshots one of their saved addresses. Existing clients may omit addressId."
    )
    @PostMapping("/customer/enquiries")
    com.saumyasolar.domain.Enquiry customerEnquiry(@Valid @RequestBody CustomerEnquiryRequest x, Authentication a) {
        return s.createCustomerEnquiry(x.propertyType(), x.monthlyBill(), x.requiredCapacityKw(), x.message(), x.addressId(), p(a));
    }
    
    @GetMapping("/platform/tenants")
    TenantPage platformTenants(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication a
    ) {
        return tenantAdmin.page(p(a), search, status, page, size);
    }
    
    @GetMapping("/platform/tenants/{id}")
    com.saumyasolar.api.TenantAdminModels.TenantRow platformTenant(@PathVariable UUID id, Authentication a) {
        return tenantAdmin.get(id, p(a));
    }
    
    @PatchMapping("/platform/tenants/{id}")
    com.saumyasolar.api.TenantAdminModels.TenantRow updatePlatformTenant(
            @PathVariable UUID id,
            @Valid @RequestBody TenantUpdate input, Authentication a
    ) {
        return tenantAdmin.update(id, input, p(a));
    }
    
    @GetMapping(value = "/platform/tenants/export", produces = "text/csv")
    ResponseEntity<byte[]> exportTenantsCsv(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "ALL") String status, Authentication a
    ) {
        var rows = tenantAdmin.exportRows(p(a), search, status);
        StringBuilder csv = new StringBuilder("Tenant Name,Tenant Slug,Administrator Name,Administrator Email,Status,Created Date,Updated Date\\r\\n");
        for (var x : rows)
            csv.append(csv(x.name())).append(',').append(csv(x.slug())).append(',')
                    .append(csv(x.administratorName())).append(',').append(csv(x.administratorEmail())).append(',')
                    .append(csv(x.status())).append(',').append(csv(x.createdAt())).append(',')
                    .append(csv(x.updatedAt())).append("\\r\\n");
        return export(
                csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "text/csv; charset=UTF-8", "tenants-" + status.toLowerCase(Locale.ROOT) + "-" + LocalDate.now() + ".csv"
        );
    }
    
    @GetMapping(
            value = "/platform/tenants/export.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    ResponseEntity<byte[]> exportTenantsExcel(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "ALL") String status, Authentication a
    ) throws Exception {
        var rows = tenantAdmin.exportRows(p(a), search, status);
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Tenants");
            String[] headers = { "Tenant Name", "Tenant Slug", "Administrator Name", "Administrator Email", "Status",
                                 "Created Date", "Updated Date"
            };
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int n = 1;
            for (var x : rows) {
                var row = sheet.createRow(n++);
                String[] values = { x.name(), x.slug(), x.administratorName(), x.administratorEmail(), x.status(),
                                    String.valueOf(x.createdAt()), String.valueOf(x.updatedAt())
                };
                for (int i = 0; i < values.length; i++)
                    row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return export(
                    out.toByteArray(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "tenants-" + status.toLowerCase(Locale.ROOT) + "-" + LocalDate.now() + ".xlsx"
            );
        }
    }
    
    @GetMapping(value = "/platform/tenants/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> exportTenantsPdf(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "ALL") String status, Authentication a
    ) throws Exception {
        var rows = tenantAdmin.exportRows(p(a), search, status);
        try (var out = new ByteArrayOutputStream()) {
            var doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Saumya Solar - Tenant Report"));
            doc.add(new Paragraph("Status: " + status + " | Search: " + search + " | Generated: " + LocalDate.now()));
            var table = new PdfPTable(new float[] { 2, 2, 2, 3, 1, 2, 2 });
            table.setWidthPercentage(100);
            for (String h : new String[] { "Tenant", "Slug", "Administrator", "Admin Email", "Status", "Created",
                                           "Updated"
            })
                table.addCell(h);
            for (var x : rows) {
                table.addCell(safe(x.name()));
                table.addCell(safe(x.slug()));
                table.addCell(safe(x.administratorName()));
                table.addCell(safe(x.administratorEmail()));
                table.addCell(safe(x.status()));
                table.addCell(safe(x.createdAt()));
                table.addCell(safe(x.updatedAt()));
            }
            doc.add(table);
            doc.close();
            return export(
                    out.toByteArray(), MediaType.APPLICATION_PDF_VALUE,
                    "tenants-" + status.toLowerCase(Locale.ROOT) + "-" + LocalDate.now() + ".pdf"
            );
        }
    }
    
    private static String csv(Object value) {
        String v = value == null ? "" : String.valueOf(value);
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    
    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
    
    private static ResponseEntity<byte[]> export(byte[] bytes, String type, String filename) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, type).body(bytes);
    }
    
    @PostMapping("/tenants")
    Tenant createTenant(@Valid @RequestBody TenantCreate x, Authentication a) {
        var principal = p(a);
        if (!"PLATFORM".equals(principal.accountType()) || principal.roles() == null
                || !principal.roles().contains("PLATFORM_SUPER_ADMIN") || principal.permissions() == null
                || !principal.permissions().contains("PLATFORM_MANAGE"))
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform Super Admin permission is required"
            );
        return lifecycle.createTenant(x.name(), x.slug(), x.adminName(), x.adminEmail(), x.adminPassword());
    }
    
    @PostMapping("/{type:categories|products|packages|customers|enquiries}")
    Object create(@PathVariable String type, @RequestBody Input x, Authentication a) {
        return s.create(type, x, p(a));
    }
    
    @PatchMapping("/tenants/{id}/status")
    Tenant tenantStatus(@PathVariable UUID id, @RequestBody Input x, Authentication a) {
        return s.updateTenantStatus(id, x.status(), p(a));
    }
    
    @PatchMapping("/users/{id}/enabled")
    UserView employeeEnabled(@PathVariable UUID id, @RequestBody Input x, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.permissions() == null || !actor.permissions().contains("USER_DISABLE"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        if (x.active() == null)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "active is required");
        var u = lifecycle.setEmployeeEnabled(actor.tenantId(), actor.userId(), id, x.active());
        var roleNames = new HashSet<String>();
        var permissionCodes = new HashSet<String>();
        u.getRoles().forEach(r -> {
            roleNames.add(r.getName());
            r.getPermissions().forEach(permission -> permissionCodes.add(permission.getCode()));
        });
        return new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), roleNames, permissionCodes);
    }
    
    @PostMapping("/enquiries/{id}/convert")
    SalesLead convert(@PathVariable UUID id, @RequestBody Input x, Authentication a) {
        return s.convertLead(id, x, p(a));
    }
    
    @PatchMapping("/leads/{id}")
    SalesLead lead(@PathVariable UUID id, @RequestBody Input x, Authentication a) {
        return s.updateLead(id, x, p(a));
    }
    
    @GetMapping("/leads/{id}/history")
    List<com.saumyasolar.domain.LeadHistory> leadHistory(@PathVariable UUID id, Authentication a) {
        return s.leadHistory(id, p(a));
    }
    
    @PostMapping("/surveys")
    @org.springframework.transaction.annotation.Transactional
    SiteSurvey survey(@RequestBody Input x, Authentication a) {
        Principal actor = p(a);
        SiteSurvey created = s.survey(x, actor);
        surveyLifecycle.recordCreated(created, actor);
        return created;
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
    
    @GetMapping("/quotations/{id}/revisions")
    List<Quotation> quotationRevisions(@PathVariable UUID id, Authentication a) {
        return s.quotationRevisions(id, p(a));
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
            d.add(new Paragraph("SAUMYA SOLAR — QUOTATION"));
            d.add(new Paragraph("Quotation: " + q.getQuotationNumber() + "    Revision: " + q.getRevision()));
            d.add(new Paragraph("Status: " + q.getStatus() + "    Valid until: " + q.getValidUntil()));
            var customer = customers.findByIdAndTenantId(q.getCustomerId(), q.getTenantId()).orElse(null);
            if (customer != null) {
                d.add(new Paragraph("Customer: " + customer.getName()));
                d.add(new Paragraph("Email: " + customer.getEmail() + "    Phone: " + customer.getPhone()));
                d.add(new Paragraph("Address: " + customer.getAddress()));
            }
            d.add(new Paragraph(" "));
            var table = new PdfPTable(new float[] { 4, 1, 2, 1, 2 });
            table.setWidthPercentage(100);
            table.addCell("Description");
            table.addCell("Qty");
            table.addCell("Unit price");
            table.addCell("Tax %");
            table.addCell("Line total");
            var quoteLines = items.findByTenantIdOrderByIdDesc(q.getTenantId()).stream()
                    .filter(line -> id.equals(line.getQuotationId())).toList();
            for (var line : quoteLines) {
                table.addCell(line.getDescription() == null ? "" : line.getDescription());
                table.addCell(String.valueOf(line.getQuantity()));
                table.addCell(String.valueOf(line.getUnitPrice()));
                table.addCell(String.valueOf(line.getTaxPercent()));
                table.addCell(String.valueOf(line.getLineTotal()));
            }
            d.add(table);
            d.add(new Paragraph("Subtotal INR: " + q.getSubtotal()));
            d.add(new Paragraph("Discount (" + q.getDiscountPercent() + "%) INR: " + q.getDiscountAmount()));
            d.add(new Paragraph("Tax INR: " + q.getTaxAmount()));
            d.add(new Paragraph("Grand total INR: " + q.getGrandTotal()));
            d.close();
            return out.toByteArray();
        }
    }
    
    @PatchMapping("/packages/{id}")
    com.saumyasolar.domain.SolarPackage updateSolarPackage(
            @PathVariable UUID id,
            @Valid @RequestBody PackageInput x,
            Authentication a
    ) {
        return s.updateSolarPackage(id, x, p(a));
    }
    
    @DeleteMapping("/packages/{id}")
    com.saumyasolar.domain.SolarPackage archiveSolarPackage(@PathVariable UUID id, Authentication a) {
        return s.archiveSolarPackage(id, p(a));
    }
    
    @PostMapping(value = "/packages/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    com.saumyasolar.domain.SolarPackage uploadPackageImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            Authentication a
    ) {
        s.assertPackageManageAccess(id, p(a));
        String imageUrl = packageImages.store(file);
        return s.updatePackageImage(id, imageUrl, p(a));
    }
    
    @PostMapping("/public/{storeSlug}/enquiries")
    com.saumyasolar.domain.Enquiry publicEnquiry(@PathVariable String storeSlug, @Valid @RequestBody Input x) {
        return s.createPublicEnquiry(storeSlug, x);
    }
    
    @GetMapping("/public/{storeSlug}/packages")
    List<com.saumyasolar.domain.SolarPackage> publicPackages(@PathVariable String storeSlug) {
        return s.publicPackages(storeSlug);
    }
    
    @GetMapping("/public/{storeSlug}/products")
    List<PublicProductView> publicProducts(@PathVariable String storeSlug) {
        return s.publicProducts(storeSlug);
    }
    
    // Vendor catalogue administration. Delete operations are soft archives.
    @PostMapping("/catalog/products")
    com.saumyasolar.domain.Product createCatalogProduct(@Valid @RequestBody ProductInput x, Authentication a) {
        return catalog.createProduct(x, p(a));
    }
    
    @PatchMapping("/products/{id}")
    com.saumyasolar.domain.Product updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductInput x,
            Authentication a
    ) {
        return catalog.updateProduct(id, x, p(a));
    }
    
    @DeleteMapping("/products/{id}")
    com.saumyasolar.domain.Product archiveProduct(@PathVariable UUID id, Authentication a) {
        return catalog.archiveProduct(id, p(a));
    }
    
    @PatchMapping("/categories/{id}")
    com.saumyasolar.domain.ProductCategory updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryInput x,
            Authentication a
    ) {
        return catalog.updateCategory(id, x, p(a));
    }
    
    @DeleteMapping("/categories/{id}")
    com.saumyasolar.domain.ProductCategory archiveCategory(@PathVariable UUID id, Authentication a) {
        return catalog.archiveCategory(id, p(a));
    }
    
    @PatchMapping("/customers/{id}")
    com.saumyasolar.domain.CustomerProfile updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerInput x,
            Authentication a
    ) {
        return catalog.updateCustomer(id, x, p(a));
    }
    
    @DeleteMapping("/customers/{id}")
    com.saumyasolar.domain.CustomerProfile archiveCustomer(@PathVariable UUID id, Authentication a) {
        return catalog.archiveCustomer(id, p(a));
    }
    
    @PatchMapping("/users/{id}")
    UserView updateEmployee(@PathVariable UUID id, @Valid @RequestBody EmployeeUpdateInput x, Authentication a) {
        var u = catalog.updateEmployee(id, x, p(a));
        var rs = new HashSet<String>();
        var ps = new HashSet<String>();
        u.getRoles().forEach(r -> {
            rs.add(r.getName());
            r.getPermissions().forEach(v -> ps.add(v.getCode()));
        });
        return new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), rs, ps);
    }
    
    @DeleteMapping("/users/{id}")
    UserView archiveEmployee(@PathVariable UUID id, Authentication a) {
        var actor = p(a);
        if (!"VENDOR".equals(actor.accountType()) || actor.permissions() == null || !actor.permissions().contains("USER_DISABLE"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        if (actor.userId().equals(id))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable your own account");
        var u = lifecycle.setEmployeeEnabled(actor.tenantId(), actor.userId(), id, false);
        var rs = new HashSet<String>();
        var ps = new HashSet<String>();
        u.getRoles().forEach(r -> {
            rs.add(r.getName());
            r.getPermissions().forEach(v -> ps.add(v.getCode()));
        });
        return new UserView(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getAccountType(), rs, ps);
    }
    
    @PostMapping("/customer/product-orders")
    CustomerOrder createCustomerProductOrder(@Valid @RequestBody CustomerProductOrderInput x, Authentication a) {
        return operations.createCustomerProductOrder(x, p(a));
    }
    
    @GetMapping("/customer/product-orders")
    List<CustomerOrder> customerProductOrders(Authentication a) {
        return operations.customerOrders(p(a));
    }
    
    @PostMapping("/orders/{id}/vendor-decision")
    CustomerOrder vendorOrderDecision(
            @PathVariable UUID id,
            @Valid @RequestBody OrderDecisionInput x,
            Authentication a
    ) {
        return operations.decideCustomerOrder(id, x, p(a));
    }
    
    // Release 3: warehouse and immutable inventory ledger.
    @GetMapping("/warehouses")
    List<com.saumyasolar.domain.Warehouse> warehouses(Authentication a) {
        return operations.warehouses(p(a));
    }
    
    @PostMapping("/warehouses")
    com.saumyasolar.domain.Warehouse createWarehouse(@Valid @RequestBody WarehouseInput x, Authentication a) {
        return operations.createWarehouse(x, p(a));
    }
    
    @GetMapping("/inventory/balances")
    List<com.saumyasolar.domain.InventoryBalance> inventoryBalances(Authentication a) {
        return operations.balances(p(a));
    }
    
    @GetMapping("/inventory/transactions")
    List<com.saumyasolar.domain.InventoryTransaction> inventoryLedger(Authentication a) {
        return operations.ledger(p(a));
    }
    
    @PostMapping("/inventory/transactions/{id}/reverse")
    com.saumyasolar.domain.InventoryTransaction reverseInventoryTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryReversalInput x,
            Authentication a
    ) {
        return operations.reverseInventoryTransaction(id, x, p(a));
    }
    
    @PostMapping("/inventory/receipts")
    com.saumyasolar.domain.InventoryBalance inventoryReceipt(@Valid @RequestBody ReceiptInput x, Authentication a) {
        return operations.receive(x, p(a));
    }
    
    @PostMapping("/inventory/returns")
    com.saumyasolar.domain.InventoryBalance inventoryReturn(@Valid @RequestBody ReceiptInput x, Authentication a) {
        return operations.returnStock(x, p(a));
    }
    
    @PostMapping("/inventory/issues")
    com.saumyasolar.domain.InventoryBalance inventoryIssue(@Valid @RequestBody IssueInput x, Authentication a) {
        return operations.issue(x, p(a));
    }
    
    @GetMapping("/inventory/low-stock")
    List<Map<String, Object>> lowStock(Authentication a) {
        return operations.lowStock(p(a));
    }
    
    @PostMapping("/inventory/damages")
    com.saumyasolar.domain.InventoryBalance inventoryDamage(@Valid @RequestBody DamageInput x, Authentication a) {
        return operations.damage(x, p(a));
    }
    
    @PostMapping("/inventory/adjustments")
    com.saumyasolar.domain.InventoryBalance inventoryAdjustment(
            @Valid @RequestBody AdjustmentInput x,
            Authentication a
    ) {
        return operations.adjust(x, p(a));
    }
    
    @PostMapping("/inventory/transfers")
    Map<String, Object> inventoryTransfer(@Valid @RequestBody TransferInput x, Authentication a) {
        return operations.transfer(x, p(a));
    }
    
    @PostMapping("/orders/{id}/reserve")
    List<com.saumyasolar.domain.InventoryReservation> reserveOrder(
            @PathVariable UUID id,
            @Valid @RequestBody ReservationInput x,
            Authentication a
    ) {
        return operations.reserve(id, x, p(a));
    }
    
    @PostMapping("/orders/{id}/release-reservation")
    List<com.saumyasolar.domain.InventoryReservation> releaseOrder(
            @PathVariable UUID id,
            @RequestBody Map<String, String> x,
            Authentication a
    ) {
        return operations.release(id, x.getOrDefault("reason", "Reservation released"), p(a));
    }
    
    @PostMapping("/orders/{id}/transition")
    CustomerOrder transitionOrder(@PathVariable UUID id, @Valid @RequestBody OrderTransitionInput x, Authentication a) {
        return operations.transition(id, x, p(a));
    }
    
    @GetMapping("/orders/{id}/history")
    List<com.saumyasolar.domain.OrderStatusHistory> orderHistory(@PathVariable UUID id, Authentication a) {
        return operations.orderHistory(id, p(a));
    }
    
    @GetMapping("/installation-teams")
    List<com.saumyasolar.domain.InstallationTeam> installationTeams(Authentication a) {
        return operations.teams(p(a));
    }
    
    @PostMapping("/installation-teams")
    com.saumyasolar.domain.InstallationTeam createInstallationTeam(@Valid @RequestBody TeamInput x, Authentication a) {
        return operations.createTeam(x, p(a));
    }
    
    @PatchMapping("/installation-teams/{id}")
    com.saumyasolar.domain.InstallationTeam updateInstallationTeam(
            @PathVariable UUID id,
            @Valid @RequestBody TeamInput x,
            Authentication a
    ) {
        return operations.updateTeam(id, x, p(a));
    }
    
    @GetMapping("/installation-checklist-templates")
    List<com.saumyasolar.domain.InstallationChecklistTemplate> checklistTemplates(Authentication a) {
        return operations.checklistTemplates(p(a));
    }
    
    @PostMapping("/installation-checklist-templates")
    com.saumyasolar.domain.InstallationChecklistTemplate createChecklistTemplate(
            @Valid @RequestBody ChecklistTemplateInput x,
            Authentication a
    ) {
        return operations.createChecklistTemplate(x, p(a));
    }
    
    @DeleteMapping("/installation-checklist-templates/{id}")
    com.saumyasolar.domain.InstallationChecklistTemplate archiveChecklistTemplate(
            @PathVariable UUID id,
            Authentication a
    ) {
        return operations.archiveChecklistTemplate(id, p(a));
    }
    
    @GetMapping("/installations")
    List<com.saumyasolar.domain.InstallationJob> installations(Authentication a) {
        return operations.installations(p(a));
    }
    
    @PostMapping("/installations")
    com.saumyasolar.domain.InstallationJob createInstallation(
            @Valid @RequestBody InstallationInput x,
            Authentication a
    ) {
        return operations.createInstallation(x, p(a));
    }
    
    @GetMapping("/installations/{id}/checklist")
    List<com.saumyasolar.domain.InstallationChecklistItem> installationChecklist(
            @PathVariable UUID id,
            Authentication a
    ) {
        return operations.checklist(id, p(a));
    }
    
    @PatchMapping("/installations/{id}/checklist/{itemId}")
    com.saumyasolar.domain.InstallationChecklistItem updateChecklist(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody ChecklistUpdateInput x,
            Authentication a
    ) {
        return operations.updateChecklist(id, itemId, x, p(a));
    }
    
    @PostMapping("/installations/{id}/reschedule")
    com.saumyasolar.domain.InstallationJob rescheduleInstallation(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleInput x,
            Authentication a
    ) {
        return operations.reschedule(id, x, p(a));
    }
    
    @PostMapping("/installations/{id}/cancel")
    com.saumyasolar.domain.InstallationJob cancelInstallation(
            @PathVariable UUID id,
            @RequestBody Map<String, String> x,
            Authentication a
    ) {
        return operations.cancelInstallation(id, x.get("reason"), p(a));
    }
    
    @GetMapping("/installations/{id}/schedule-history")
    List<com.saumyasolar.domain.InstallationScheduleHistory> installationScheduleHistory(
            @PathVariable UUID id,
            Authentication a
    ) {
        return operations.scheduleHistory(id, p(a));
    }
    
    @PostMapping("/installations/{id}/complete")
    com.saumyasolar.domain.InstallationJob completeInstallation(
            @PathVariable UUID id,
            @RequestBody Map<String, String> x,
            Authentication a
    ) {
        return operations.completeInstallation(id, x.getOrDefault("reason", "Installation checklist completed"), p(a));
    }
    
    @PostMapping("/installations/{id}/commission")
    com.saumyasolar.domain.InstallationJob commissionInstallation(
            @PathVariable UUID id,
            @Valid @RequestBody CommissionInput x,
            Authentication a
    ) {
        return operations.commission(id, x, p(a));
    }
    
    @GetMapping("/warranties")
    List<com.saumyasolar.domain.Warranty> warranties(Authentication a) {
        return operations.warranties(p(a));
    }
    
    @GetMapping("/customer/installations")
    List<com.saumyasolar.domain.InstallationJob> myInstallations(Authentication a) {
        return operations.customerInstallations(p(a));
    }
    
    @GetMapping("/customer/warranties")
    List<com.saumyasolar.domain.Warranty> myWarranties(Authentication a) {
        return operations.customerWarranties(p(a));
    }
}
