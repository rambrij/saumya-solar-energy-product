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
            IdempotencyRecordRepository p
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
    
    public Object list(String type, Principal p) {
        if ("tenants".equals(type)) {
            if (!"PLATFORM".equals(p.accountType())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return tenants.findAll();
        }
        if ("users".equals(type)) {
            vendor(p);
            return users.findByTenantId(p.tenantId());
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
                if (x.categoryId() != null && cats.findByIdAndTenantId(x.categoryId(), p.tenantId()).isEmpty())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category does not belong to this vendor");
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
                    pi.setPackageId(e.getId());
                    pi.setProductId(l.productId());
                    pi.setQuantity(l.quantity());
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
        if (x.stage() != null) l.setStage(x.stage());
        if (x.priority() != null) l.setPriority(x.priority());
        if (x.assignedUserId() != null) l.setAssignedUserId(x.assignedUserId());
        l.setNextFollowUp(x.nextFollowUp());
        l.setLostReason(x.lostReason());
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
        if ("COMPLETED".equals(e.getStatus())) e.setCompletedAt(Instant.now());
        return surveys.save(e);
    }
    
    @Transactional
    public QuoteView quote(Input x, Principal p) {
        vendor(p);
        var q = new Quotation();
        q.setId(UUID.randomUUID());
        q.setTenantId(p.tenantId());
        q.setQuoteGroupId(UUID.randomUUID());
        q.setQuotationNumber("QUO-" + System.currentTimeMillis());
        q.setRevision(1);
        q.setCustomerId(x.customerId());
        q.setLeadId(x.leadId());
        q.setSurveyId(x.surveyId());
        q.setDiscountPercent(x.discountPercent() == null ? BigDecimal.ZERO : x.discountPercent());
        q.setValidUntil(x.validUntil() == null ? LocalDate.now().plusDays(30) : x.validUntil());
        q.setCreatedBy(p.userId());
        q.setCreatedAt(Instant.now());
        quotes.save(q);
        BigDecimal sub = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        var items = new ArrayList<QuotationItem>();
        for (var a : x.items()) {
            var base = a.unitPrice().multiply(BigDecimal.valueOf(a.quantity()));
            var i = new QuotationItem();
            i.setId(UUID.randomUUID());
            i.setTenantId(p.tenantId());
            i.setQuotationId(q.getId());
            i.setProductId(a.productId());
            i.setPackageId(a.packageId());
            i.setDescription(a.description());
            i.setQuantity(a.quantity());
            i.setUnitPrice(a.unitPrice());
            i.setTaxPercent(a.taxPercent());
            i.setLineTotal(base);
            items.add(quoteItems.save(i));
            sub = sub.add(base);
            tax = tax.add(base.multiply(a.taxPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
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
    
    public Quotation action(UUID id, String action, Principal p) {
        var q = quotes.findByIdAndTenantId(id, p.tenantId()).orElseThrow();
        if ("CUSTOMER".equals(p.accountType())) {
            var c = customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(x -> p.userId().equals(x.getUserId())).findFirst().orElseThrow();
            if (!q.getCustomerId().equals(c.getId()) || !"SENT".equals(q.getStatus()) || q.getValidUntil().isBefore(LocalDate.now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            q.setStatus("accept".equals(action) ? "ACCEPTED" : "REJECTED");
            return quotes.save(q);
        }
        vendor(p);
        q.setStatus(switch (action) {
            case "approve" -> "DRAFT";
            case "reject-approval" -> "APPROVAL_REJECTED";
            case "send" -> "SENT";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        });
        return quotes.save(q);
    }
    
    @Transactional
    public CustomerOrder order(UUID qid, String key, Principal p) {
        vendor(p);
        var old = idem.findByTenantIdAndOperationNameAndIdempotencyKey(p.tenantId(), "QUOTE_TO_ORDER", key);
        if (old.isPresent()) return orders.findById(old.get().getResourceId()).orElseThrow();
        var q = quotes.findByIdAndTenantId(qid, p.tenantId()).orElseThrow();
        if (!"ACCEPTED".equals(q.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT);
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
