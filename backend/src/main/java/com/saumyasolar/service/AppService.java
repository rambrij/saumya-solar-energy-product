package com.saumyasolar.service;

import com.saumyasolar.api.Models.Input;
import com.saumyasolar.api.Models.QuoteView;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
@Service
public class AppService {
    final TenantRepository tenants;
    final AppUserRepository users;
    final ProductCategoryRepository cats;
    final ProductRepository products;
    final SolarPackageRepository packs;
    final PackageItemRepository packItems;
    final CustomerProfileRepository customers;
    final EnquiryRepository enquiries;
    final SalesLeadRepository leads;
    final LeadHistoryRepository histories;
    final SiteSurveyRepository surveys;
    final QuotationRepository quotes;
    final QuotationItemRepository quoteItems;
    final CustomerOrderRepository orders;
    final OrderItemRepository orderItems;
    final IdempotencyRecordRepository idem;
    final RoleRepository roles;
    
    public AppService(
            TenantRepository a,
            AppUserRepository b,
            ProductCategoryRepository c,
            ProductRepository d,
            SolarPackageRepository e,
            PackageItemRepository f,
            CustomerProfileRepository g,
            EnquiryRepository h,
            SalesLeadRepository i,
            LeadHistoryRepository j,
            SiteSurveyRepository k,
            QuotationRepository l,
            QuotationItemRepository m,
            CustomerOrderRepository n,
            OrderItemRepository o,
            IdempotencyRecordRepository p,
            RoleRepository roles
    ) {
        tenants = a;
        users = b;
        cats = c;
        products = d;
        packs = e;
        packItems = f;
        customers = g;
        enquiries = h;
        leads = i;
        histories = j;
        surveys = k;
        quotes = l;
        quoteItems = m;
        orders = n;
        orderItems = o;
        idem = p;
        this.roles = roles;
    }
    
    void vendor(Principal p) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void requirePermission(Principal p, String code) {
        if (p.permissions() == null || !p.permissions().contains(code))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + code);
    }

    private void requireEmployee(UUID id, UUID tenantId) {
        if (id != null && users.findById(id).filter(u -> tenantId.equals(u.getTenantId())
                && "VENDOR".equals(u.getAccountType()) && u.isEnabled()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee must belong to this vendor");
    }

    private CustomerProfile requireCustomer(UUID id, UUID tenantId) {
        return customers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private SalesLead requireLead(UUID id, UUID tenantId) {
        return leads.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }
    
    public Object list(String type, Principal p) {
        if ("tenants".equals(type)) {
            if (!"PLATFORM".equals(p.accountType()) || p.permissions() == null || !p.permissions().contains("PLATFORM_MANAGE"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return tenants.findAll();
        }
        if ("users".equals(type)) {
            vendor(p);
            requirePermission(p, "USER_VIEW");
            return users.findByTenantId(p.tenantId()).stream()
                    .filter(u -> "VENDOR".equals(u.getAccountType())).toList();
        }
        if (java.util.Set.of("catalog-categories", "catalog-products", "catalog-packages").contains(type)) {
            if (!"CUSTOMER".equals(p.accountType()) || p.tenantId() == null)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return switch (type) {
                case "catalog-categories" -> cats.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(ProductCategory::isActive).toList();
                case "catalog-products" -> products.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(Product::isActive).toList();
                default -> packs.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(SolarPackage::isActive).toList();
            };
        }
        if ("customer-enquiries".equals(type) && "CUSTOMER".equals(p.accountType())) {
            var profile = customers.findByTenantIdAndUserId(p.tenantId(), p.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
            return enquiries.findByTenantIdAndCustomerIdOrderByIdDesc(p.tenantId(), profile.getId());
        }
        if ("customer-orders".equals(type) && "CUSTOMER".equals(p.accountType())) {
            var c = customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(x -> p.userId().equals(x.getUserId())).findFirst().orElseThrow();
            return orders.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(x -> x.getCustomerId().equals(c.getId())).toList();
        }
        if ("quotations".equals(type) && "CUSTOMER".equals(p.accountType())) {
            var c = customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(x -> p.userId().equals(x.getUserId())).findFirst().orElseThrow();
            return quotes.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(x -> x.getCustomerId().equals(c.getId())).toList();
        }
        vendor(p);
        switch (type) {
            case "categories", "products", "packages" -> requirePermission(p, "CATALOG_MANAGE");
            case "customers" -> requirePermission(p, "CUSTOMER_MANAGE");
            case "enquiries", "leads" -> requirePermission(p, "LEAD_MANAGE");
            case "surveys" -> requirePermission(p, "SURVEY_MANAGE");
            case "quotations" -> requirePermission(p, "QUOTATION_MANAGE");
            case "orders" -> requirePermission(p, "ORDER_CREATE");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return switch (type) {
            case "categories" -> cats.findByTenantIdOrderByIdDesc(p.tenantId());
            case "products" -> products.findByTenantIdOrderByIdDesc(p.tenantId());
            case "packages" -> packs.findByTenantIdOrderByIdDesc(p.tenantId());
            case "customers" -> customers.findByTenantIdOrderByIdDesc(p.tenantId());
            case "enquiries" -> enquiries.findByTenantIdOrderByIdDesc(p.tenantId());
            case "leads" -> leads.findByTenantIdOrderByIdDesc(p.tenantId());
            case "surveys" -> surveys.findByTenantIdOrderByIdDesc(p.tenantId());
            case "quotations" -> quotes.findByTenantIdOrderByIdDesc(p.tenantId());
            case "orders" -> orders.findByTenantIdOrderByIdDesc(p.tenantId());
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
    }
    
    public Tenant updateTenantStatus(UUID tenantId, String status, Principal p) {
        if (!"PLATFORM".equals(p.accountType()) || p.permissions() == null || !p.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant status must be ACTIVE or SUSPENDED");
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenant.setStatus(status);
        return tenants.save(tenant);
    }

    @Transactional
    public Object create(String type, Input x, Principal p) {
        if ("tenants".equals(type)) {
            if (!"PLATFORM".equals(p.accountType())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            var t = new Tenant();
            t.setId(UUID.randomUUID());
            t.setName(x.name());
            t.setSlug(x.slug());
            t.setStatus("ACTIVE");
            return tenants.save(t);
        }
        vendor(p);
        switch (type) {
            case "categories", "products", "packages" -> requirePermission(p, "CATALOG_MANAGE");
            case "customers" -> requirePermission(p, "CUSTOMER_MANAGE");
            case "enquiries" -> requirePermission(p, "LEAD_MANAGE");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return switch (type) {
            case "categories" -> {
                var e = new ProductCategory();
                e.setId(UUID.randomUUID());
                e.setTenantId(p.tenantId());
                e.setName(x.name());
                e.setDescription(x.description());
                e.setActive(x.active() == null || x.active());
                yield cats.save(e);
            }
            case "products" -> {
                var e = new Product();
                e.setId(UUID.randomUUID());
                e.setTenantId(p.tenantId());
                if (x.categoryId() == null || cats.findByIdAndTenantId(x.categoryId(), p.tenantId())
                        .filter(ProductCategory::isActive).isEmpty())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active product category belonging to this vendor");
                if (x.price() == null || x.price().signum() < 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product price must be zero or greater");
                if (x.taxPercent() != null && (x.taxPercent().signum() < 0 || x.taxPercent().compareTo(new BigDecimal("100")) > 0))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax percentage must be between 0 and 100");
                e.setCategoryId(x.categoryId());
                e.setSku(x.sku());
                e.setName(x.name());
                e.setDescription(x.description());
                e.setPrice(x.price());
                e.setTaxPercent(x.taxPercent() == null ? BigDecimal.ZERO : x.taxPercent());
                e.setActive(true);
                yield products.save(e);
            }
            case "packages" -> {
                if (x.items() == null || x.items().isEmpty())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one package component");
                for (var line : x.items()) {
                    if (line.productId() == null || line.quantity() < 1)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every package component needs a product and positive quantity");
                    if (products.findByIdAndTenantId(line.productId(), p.tenantId()).filter(Product::isActive).isEmpty())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package components must use active products from this vendor");
                }
                var e = new SolarPackage();
                e.setId(UUID.randomUUID());
                e.setTenantId(p.tenantId());
                e.setCode(x.code());
                e.setName(x.name());
                e.setCapacityKw(x.capacityKw());
                e.setPackageType(x.packageType());
                e.setBasePrice(x.basePrice());
                e.setWarrantyYears(x.warrantyYears());
                e.setActive(true);
                packs.save(e);
                if (x.items() != null) for (var l : x.items()) {
                    var pi = new PackageItem();
                    pi.setId(UUID.randomUUID());
                    pi.setTenantId(p.tenantId());
                    if (l.productId() == null || products.findByIdAndTenantId(l.productId(), p.tenantId()).filter(Product::isActive).isEmpty())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package items must reference active products in this vendor");
                    if (l.quantity() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package item quantity must be positive");
                    pi.setPackageId(e.getId());
                    pi.setProductId(l.productId());
                    pi.setQuantity(l.quantity());
                    pi.setOptionalItem(Boolean.TRUE.equals(l.optionalItem()));
                    packItems.save(pi);
                }
                yield e;
            }
            case "customers" -> {
                var e = new CustomerProfile();
                e.setId(UUID.randomUUID());
                e.setTenantId(p.tenantId());
                e.setName(x.name());
                e.setEmail(x.email());
                e.setPhone(x.phone());
                e.setAddress(x.address());
                yield customers.save(e);
            }
            case "enquiries" -> {
                var e = new Enquiry();
                e.setId(UUID.randomUUID());
                e.setTenantId(p.tenantId());
                if (x.customerId() != null) requireCustomer(x.customerId(), p.tenantId());
                e.setCustomerId(x.customerId());
                e.setName(x.name());
                e.setEmail(x.email());
                e.setPhone(x.phone());
                e.setPropertyType(x.propertyType());
                e.setMonthlyBill(x.monthlyBill());
                e.setRequiredCapacityKw(x.requiredCapacityKw());
                e.setMessage(x.message());
                e.setStatus("NEW");
                e.setCreatedAt(Instant.now());
                yield enquiries.save(e);
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
    }
    
    public List<Role> employeeRoles(Principal p) {
        vendor(p);
        requirePermission(p, "USER_MANAGE");
        return roles.findByTenantIdOrderByNameAsc(p.tenantId()).stream()
                .filter(role -> !java.util.Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "CUSTOMER").contains(role.getName()))
                .toList();
    }

    @Transactional
    public Enquiry createCustomerEnquiry(Input x, Principal p) {
        if (!"CUSTOMER".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var profile = customers.findByTenantIdAndUserId(p.tenantId(), p.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        var enquiry = new Enquiry();
        enquiry.setId(UUID.randomUUID());
        enquiry.setTenantId(p.tenantId());
        enquiry.setCustomerId(profile.getId());
        enquiry.setName(profile.getName());
        enquiry.setEmail(profile.getEmail());
        enquiry.setPhone(profile.getPhone());
        enquiry.setPropertyType(x.propertyType());
        enquiry.setMonthlyBill(x.monthlyBill());
        enquiry.setRequiredCapacityKw(x.requiredCapacityKw());
        enquiry.setMessage(x.message());
        enquiry.setStatus("NEW");
        enquiry.setCreatedAt(Instant.now());
        return enquiries.save(enquiry);
    }

    @Transactional
    public SalesLead convertLead(UUID enquiryId, Input x, Principal p) {
        vendor(p);
        requirePermission(p, "LEAD_MANAGE");
        var q = enquiries.findByIdAndTenantId(enquiryId, p.tenantId()).orElseThrow();
        if (!"NEW".equals(q.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enquiry already converted");
        requireEmployee(x.assignedUserId(), p.tenantId());
        q.setStatus("CONVERTED");
        enquiries.save(q);
        var l = new SalesLead();
        l.setId(UUID.randomUUID());
        l.setTenantId(p.tenantId());
        l.setEnquiryId(q.getId());
        l.setCustomerId(q.getCustomerId());
        l.setStage("NEW");
        l.setPriority(x.priority() == null ? "MEDIUM" : x.priority());
        l.setAssignedUserId(x.assignedUserId());
        l.setNextFollowUp(x.nextFollowUp());
        l.setCreatedAt(Instant.now());
        leads.save(l);
        history(l, null, "NEW", p.userId(), "Converted from enquiry");
        return l;
    }
    
    @Transactional
    public SalesLead updateLead(UUID id, Input x, Principal p) {
        vendor(p);
        requirePermission(p, "LEAD_MANAGE");
        var l = leads.findByIdAndTenantId(id, p.tenantId()).orElseThrow();
        requireEmployee(x.assignedUserId(), p.tenantId());
        var old = l.getStage();
        if (x.stage() != null && !java.util.Set.of("NEW", "CONTACTED", "SITE_VISIT_SCHEDULED", "SURVEY_COMPLETED", "QUOTATION_SENT", "NEGOTIATION", "WON", "LOST").contains(x.stage()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lead stage");
        if (x.stage() != null && "LOST".equals(x.stage()) && (x.lostReason() == null || x.lostReason().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lost reason is required");
        if (x.priority() != null && !java.util.Set.of("LOW", "MEDIUM", "HIGH").contains(x.priority()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lead priority");
        if (x.stage() != null) l.setStage(x.stage());
        if (x.priority() != null) l.setPriority(x.priority());
        if (x.assignedUserId() != null) l.setAssignedUserId(x.assignedUserId());
        if (x.nextFollowUp() != null) l.setNextFollowUp(x.nextFollowUp());
        if (x.lostReason() != null) l.setLostReason(x.lostReason());
        leads.save(l);
        if (!Objects.equals(old, l.getStage())) history(l, old, l.getStage(), p.userId(), x.reason());
        return l;
    }
    
    void history(SalesLead l, String old, String now, UUID actor, String reason) {
        var h = new LeadHistory();
        h.setId(UUID.randomUUID());
        h.setTenantId(l.getTenantId());
        h.setLeadId(l.getId());
        h.setOldStage(old);
        h.setNewStage(now);
        h.setActorId(actor);
        h.setReason(reason);
        h.setCreatedAt(Instant.now());
        histories.save(h);
    }
    
    @Transactional
    public SiteSurvey survey(Input x, Principal p) {
        vendor(p);
        requirePermission(p, "SURVEY_MANAGE");
        if (x.leadId() == null || leads.findByIdAndTenantId(x.leadId(), p.tenantId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead must belong to this vendor");
        requireEmployee(x.assignedUserId(), p.tenantId());
        var e = new SiteSurvey();
        e.setId(UUID.randomUUID());
        e.setTenantId(p.tenantId());
        e.setLeadId(x.leadId());
        e.setScheduledAt(x.scheduledAt());
        e.setAssignedUserId(x.assignedUserId());
        e.setPropertyType(x.propertyType());
        e.setRoofType(x.roofType());
        e.setUsableAreaSqft(x.usableAreaSqft());
        e.setShading(x.shading());
        e.setSanctionedLoadKw(x.sanctionedLoadKw());
        e.setNotes(x.notes());
        e.setStatus(x.status() == null ? "SCHEDULED" : x.status());
        if (!java.util.Set.of("SCHEDULED", "COMPLETED", "CANCELLED").contains(e.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid survey status");
        if ("SCHEDULED".equals(e.getStatus()) && e.getScheduledAt() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled date/time is required");
        if ("COMPLETED".equals(e.getStatus())) e.setCompletedAt(Instant.now());
        return surveys.save(e);
    }
    
    @Transactional
    public QuoteView quote(Input x, Principal p) {
        vendor(p);
        requirePermission(p, "QUOTATION_MANAGE");
        requireCustomer(x.customerId(), p.tenantId());
        if (x.leadId() != null && !requireLead(x.leadId(), p.tenantId()).getCustomerId().equals(x.customerId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead does not belong to the selected customer");
        if (x.surveyId() != null) {
            var survey = surveys.findByIdAndTenantId(x.surveyId(), p.tenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Survey not found in this vendor"));
            if (x.leadId() == null || !x.leadId().equals(survey.getLeadId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Survey must belong to selected lead");
        }
        if (x.items() == null || x.items().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one quotation item is required");
        var requestedDiscount = x.discountPercent() == null ? BigDecimal.ZERO : x.discountPercent();
        if (requestedDiscount.compareTo(BigDecimal.ZERO) < 0 || requestedDiscount.compareTo(new BigDecimal("100")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount must be between 0 and 100");
        var q = new Quotation();
        q.setId(UUID.randomUUID());
        q.setTenantId(p.tenantId());
        q.setQuoteGroupId(UUID.randomUUID());
        q.setQuotationNumber("QUO-" + System.currentTimeMillis());
        q.setRevision(1);
        q.setCustomerId(x.customerId());
        q.setLeadId(x.leadId());
        q.setSurveyId(x.surveyId());
        q.setDiscountPercent(requestedDiscount);
        q.setValidUntil(x.validUntil() == null ? LocalDate.now().plusDays(30) : x.validUntil());
        q.setCreatedBy(p.userId());
        q.setCreatedAt(Instant.now());
        quotes.save(q);
        BigDecimal sub = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        var items = new ArrayList<QuotationItem>();
        for (var a : x.items()) {
            if (a.quantity() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
            BigDecimal unitPrice;
            BigDecimal taxPercent;
            String description;
            if (a.productId() != null) {
                var product = products.findByIdAndTenantId(a.productId(), p.tenantId()).filter(Product::isActive)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is not active in this vendor"));
                unitPrice = product.getPrice();
                taxPercent = product.getTaxPercent();
                description = product.getName();
            } else if (a.packageId() != null) {
                var solarPackage = packs.findByIdAndTenantId(a.packageId(), p.tenantId()).filter(SolarPackage::isActive)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package is not active in this vendor"));
                unitPrice = solarPackage.getBasePrice();
                taxPercent = BigDecimal.ZERO;
                description = solarPackage.getName();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each quotation item must reference a product or package");
            }
            if (unitPrice == null || taxPercent == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog price and tax must be configured");
            var base = unitPrice.multiply(BigDecimal.valueOf(a.quantity()));
            var i = new QuotationItem();
            i.setId(UUID.randomUUID());
            i.setTenantId(p.tenantId());
            i.setQuotationId(q.getId());
            i.setProductId(a.productId());
            i.setPackageId(a.packageId());
            i.setDescription(description);
            i.setQuantity(a.quantity());
            i.setUnitPrice(unitPrice);
            i.setTaxPercent(taxPercent);
            i.setLineTotal(base);
            items.add(quoteItems.save(i));
            sub = sub.add(base);
            var taxableBase = base.multiply(BigDecimal.valueOf(100).subtract(requestedDiscount))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            tax = tax.add(taxableBase.multiply(taxPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        var disc = sub.multiply(q.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        q.setSubtotal(sub);
        q.setDiscountAmount(disc);
        q.setTaxAmount(tax);
        q.setGrandTotal(sub.subtract(disc).add(tax));
        q.setApprovalRequired(q.getDiscountPercent().compareTo(BigDecimal.TEN) > 0);
        q.setStatus(q.isApprovalRequired() ? "PENDING_APPROVAL" : "DRAFT");
        quotes.save(q);
        return new QuoteView(q, items);
    }
    
    @Transactional
    public Quotation action(UUID id, String action, Principal p) {
        if (p.tenantId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var q = quotes.findByIdAndTenantId(id, p.tenantId()).orElseThrow();
        if ("CUSTOMER".equals(p.accountType())) {
            var c = customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(x -> p.userId().equals(x.getUserId())).findFirst().orElseThrow();
            if (!q.getCustomerId().equals(c.getId()) || !"SENT".equals(q.getStatus()) || q.getValidUntil().isBefore(LocalDate.now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            if (!"accept".equals(action) && !"reject".equals(action))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            q.setStatus("accept".equals(action) ? "ACCEPTED" : "REJECTED");
            q.setDecisionBy(p.userId());
            q.setDecisionAt(Instant.now());
            return quotes.save(q);
        }
        vendor(p);
        if ("approve".equals(action) || "reject-approval".equals(action)) {
            requirePermission(p, "DISCOUNT_APPROVE");
            if (!"PENDING_APPROVAL".equals(q.getStatus()) || !q.isApprovalRequired())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation is not awaiting approval");
        } else {
            requirePermission(p, "QUOTATION_MANAGE");
        }
        if ("send".equals(action) && !"DRAFT".equals(q.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft quotation can be sent");
        q.setStatus(switch (action) {
            case "approve" -> "DRAFT";
            case "reject-approval" -> "APPROVAL_REJECTED";
            case "send" -> "SENT";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        });
        return quotes.save(q);
    }
    
    public java.util.List<Quotation> quotationRevisions(UUID id, Principal p) {
        if (p.tenantId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var quotation = quotes.findByIdAndTenantId(id, p.tenantId()).orElseThrow();
        if ("CUSTOMER".equals(p.accountType())) {
            var customer = customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream()
                    .filter(x -> p.userId().equals(x.getUserId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!customer.getId().equals(quotation.getCustomerId()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } else {
            vendor(p);
            requirePermission(p, "QUOTATION_MANAGE");
        }
        return quotes.findByQuoteGroupIdAndTenantIdOrderByRevisionAsc(quotation.getQuoteGroupId(), p.tenantId());
    }

    @Transactional
    public CustomerOrder order(UUID qid, String key, Principal p) {
        vendor(p);
        requirePermission(p, "ORDER_CREATE");
        if (key == null || key.isBlank() || key.length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid Idempotency-Key header is required");
        var old = idem.findByTenantIdAndOperationNameAndIdempotencyKey(p.tenantId(), "QUOTE_TO_ORDER", key);
        if (old.isPresent()) {
            var existing = orders.findByIdAndTenantId(old.get().getResourceId(), p.tenantId()).orElseThrow();
            if (!qid.equals(existing.getQuotationId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already belongs to another quotation");
            return existing;
        }
        var q = quotes.findByIdAndTenantIdForUpdate(qid, p.tenantId()).orElseThrow();
        if (!"ACCEPTED".equals(q.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var existingOrder = orders.findByTenantIdOrderByIdDesc(p.tenantId()).stream()
                .filter(o -> qid.equals(o.getQuotationId())).findFirst();
        if (existingOrder.isPresent()) return existingOrder.get();
        var o = new CustomerOrder();
        o.setId(UUID.randomUUID());
        o.setTenantId(p.tenantId());
        o.setQuotationId(qid);
        o.setCustomerId(q.getCustomerId());
        o.setOrderNumber("ORD-" + System.currentTimeMillis());
        o.setStatus("CONFIRMED");
        o.setTotal(q.getGrandTotal());
        o.setCreatedAt(Instant.now());
        orders.save(o);
        for (var qi : quoteItems.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(i -> i.getQuotationId().equals(qid)).toList()) {
            var i = new OrderItem();
            i.setId(UUID.randomUUID());
            i.setTenantId(p.tenantId());
            i.setOrderId(o.getId());
            i.setDescription(qi.getDescription());
            i.setQuantity(qi.getQuantity());
            i.setUnitPrice(qi.getUnitPrice());
            i.setLineTotal(qi.getLineTotal());
            orderItems.save(i);
        }
        var rec = new IdempotencyRecord();
        rec.setId(UUID.randomUUID());
        rec.setTenantId(p.tenantId());
        rec.setOperationName("QUOTE_TO_ORDER");
        rec.setIdempotencyKey(key);
        rec.setResourceId(o.getId());
        idem.save(rec);
        return o;
    }
}
