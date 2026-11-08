package com.saumyasolar.service;

import com.saumyasolar.api.Models.Input;
import com.saumyasolar.api.Models.QuoteView;
import com.saumyasolar.api.PackageInput;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
@Service
public class AppService {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private IntegrationManagementService integrationManagement;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private EnterpriseSaasService enterpriseSaas;

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
    final OrderStatusHistoryRepository orderStatusHistory;
    final NotificationService notifications;
    final ApprovalWorkflowService approvalWorkflows;
    
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
            RoleRepository roles,
            OrderStatusHistoryRepository orderStatusHistory,
            NotificationService notifications,
            ApprovalWorkflowService approvalWorkflows
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
        this.orderStatusHistory = orderStatusHistory;
        this.notifications = notifications;
        this.approvalWorkflows = approvalWorkflows;
    }
    
    void vendor(Principal p) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void requirePermission(Principal p, String code) {
        if (p.permissions() == null || !p.permissions().contains(code))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + code);
    }

    private void requireAnyPermission(Principal p, String... codes) {
        if (p.permissions() == null || java.util.Arrays.stream(codes).noneMatch(p.permissions()::contains))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required catalogue permission");
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
            case "categories", "products", "packages" -> requireAnyPermission(p, "CATALOG_VIEW", "CATALOG_MANAGE");
            case "customers" -> requirePermission(p, "CUSTOMER_MANAGE");
            case "enquiries", "leads" -> requirePermission(p, "LEAD_MANAGE");
            case "surveys" -> requirePermission(p, "SURVEY_MANAGE");
            case "quotations" -> requirePermission(p, "QUOTATION_MANAGE");
            case "orders" -> requirePermission(p, "ORDER_CREATE");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Object rows = switch (type) {
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
        if (enterpriseSaas == null) return rows;
        return switch (type) {
            case "enquiries" -> enterpriseSaas.filterBranchRecords((List<Enquiry>)rows, Enquiry::getBranchId, p);
            case "leads" -> enterpriseSaas.filterBranchRecords((List<SalesLead>)rows, SalesLead::getBranchId, p);
            case "surveys" -> enterpriseSaas.filterBranchRecords((List<SiteSurvey>)rows, SiteSurvey::getBranchId, p);
            case "quotations" -> enterpriseSaas.filterBranchRecords((List<Quotation>)rows, Quotation::getBranchId, p);
            case "orders" -> enterpriseSaas.filterBranchRecords((List<CustomerOrder>)rows, CustomerOrder::getBranchId, p);
            default -> rows;
        };
    }
    
    public Tenant updateTenantStatus(UUID tenantId, String status, Principal p) {
        if (!"PLATFORM".equals(p.accountType()) || p.permissions() == null || !p.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant status must be ACTIVE or SUSPENDED");
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenant.setStatus(status);
        tenant.setUpdatedAt(Instant.now());
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
                if(enterpriseSaas!=null)enterpriseSaas.enforceProductLimit(p.tenantId());
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
                e.setPurchasePrice(BigDecimal.ZERO);
                e.setDiscountPercent(BigDecimal.ZERO);
                e.setReorderLevel(BigDecimal.ZERO);
                e.setUnitOfMeasure("PCS");
                e.setSerialized(false);
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
                e.setImageUrl(x.imageUrl());
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
                if(enterpriseSaas!=null)enterpriseSaas.enforceCustomerLimit(p.tenantId());
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
    
    public List<SolarPackage> publicPackages(String storeSlug) {
        String normalized = storeSlug == null ? "" : storeSlug.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        var tenant = tenants.findBySlug(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        if (!"ACTIVE".equals(tenant.getStatus()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store is not available");
        return packs.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
                .filter(SolarPackage::isActive).toList();
    }

    public List<com.saumyasolar.api.CatalogModels.PublicProductView> publicProducts(String storeSlug) {
        String normalized = storeSlug == null ? "" : storeSlug.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        var tenant = tenants.findBySlug(normalized)
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active store not found"));
        var activeCategories = cats.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
                .filter(ProductCategory::isActive)
                .collect(java.util.stream.Collectors.toMap(ProductCategory::getId, category -> category));
        return products.findByTenantIdOrderByIdDesc(tenant.getId()).stream()
                .filter(Product::isActive)
                .map(product -> {
                    ProductCategory category = activeCategories.get(product.getCategoryId());
                    if (category == null) return null;
                    return new com.saumyasolar.api.CatalogModels.PublicProductView(
                            product.getId(), product.getSku(), product.getName(), product.getDescription(),
                            product.getCategoryId().toString(), category.getName(), product.getManufacturer(),
                            product.getModel(), product.getTechnology(), product.getPrice(),
                            product.getDiscountPercent(), product.getTaxPercent(), product.getWarrantyYears(),
                            product.getImageUrl(), product.getDatasheetUrl(), product.getUnitOfMeasure(),
                            product.isSerialized(), product.getAttributesJson());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public Enquiry createPublicEnquiry(String storeSlug, Input input) {
        String normalized = storeSlug == null ? "" : storeSlug.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Tenant tenant = tenants.findBySlug(normalized)
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active store not found"));
        if (input.name() == null || input.name().isBlank() || input.email() == null
                || !input.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || input.phone() == null || input.phone().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, valid email and phone are required.");
        Enquiry enquiry = new Enquiry();
        enquiry.setId(UUID.randomUUID());
        enquiry.setTenantId(tenant.getId());
        enquiry.setName(input.name().trim());
        enquiry.setEmail(input.email().trim().toLowerCase(java.util.Locale.ROOT));
        enquiry.setPhone(input.phone().trim());
        enquiry.setPropertyType(input.propertyType());
        enquiry.setMonthlyBill(input.monthlyBill());
        enquiry.setRequiredCapacityKw(input.requiredCapacityKw());
        enquiry.setMessage(input.message());
        enquiry.setStatus("NEW");
        enquiry.setCreatedAt(Instant.now());
        return enquiries.save(enquiry);
    }

    private UUID requirePackageManager(Principal p) {
        vendor(p);
        requirePermission(p, "CATALOG_MANAGE");
        return p.tenantId();
    }

    public void assertPackageManageAccess(UUID id, Principal p) {
        UUID tenantId = requirePackageManager(p);
        if (packs.findByIdAndTenantId(id, tenantId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solar package not found");
    }

    @Transactional
    public SolarPackage updatePackageImage(UUID id, String imageUrl, Principal p) {
        UUID tenantId = requirePackageManager(p);
        SolarPackage pack = packs.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solar package not found"));
        pack.setImageUrl(imageUrl);
        return packs.save(pack);
    }

    @Transactional
    public SolarPackage updateSolarPackage(UUID id, PackageInput x, Principal p) {
        UUID tenantId = requirePackageManager(p);
        SolarPackage pack = packs.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solar package not found"));
        packs.findByTenantIdOrderByIdDesc(tenantId).stream()
                .filter(other -> !other.getId().equals(id) && other.getCode().equalsIgnoreCase(x.code()))
                .findAny().ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Package code already exists"); });
        validatePackageItems(x.items(), tenantId);
        pack.setCode(x.code().trim());
        pack.setName(x.name().trim());
        pack.setCapacityKw(x.capacityKw());
        pack.setPackageType(x.packageType());
        pack.setBasePrice(x.basePrice());
        pack.setWarrantyYears(x.warrantyYears());
        if (x.imageUrl() != null && !x.imageUrl().isBlank()) {
            if (!x.imageUrl().startsWith("https://") && !x.imageUrl().startsWith("/uploads/"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package image must use HTTPS or the managed /uploads path");
            pack.setImageUrl(x.imageUrl());
        }
        if (x.active() != null) pack.setActive(x.active());
        packs.save(pack);
        packItems.findByTenantIdOrderByIdDesc(tenantId).stream()
                .filter(item -> id.equals(item.getPackageId())).toList().forEach(packItems::delete);
        for (PackageInput.Item item : x.items()) {
            PackageItem row = new PackageItem();
            row.setId(UUID.randomUUID());
            row.setTenantId(tenantId);
            row.setPackageId(id);
            row.setProductId(item.productId());
            row.setQuantity(item.quantity());
            row.setOptionalItem(Boolean.TRUE.equals(item.optionalItem()));
            packItems.save(row);
        }
        return pack;
    }

    private void validatePackageItems(List<PackageInput.Item> items, UUID tenantId) {
        if (items == null || items.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one package component");
        for (PackageInput.Item item : items) {
            if (item.productId() == null || item.quantity() == null || item.quantity() < 1)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every package component needs an active product and positive quantity");
            if (products.findByIdAndTenantId(item.productId(), tenantId).filter(Product::isActive).isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package components must use active products from this vendor");
        }
    }

    @Transactional
    public SolarPackage archiveSolarPackage(UUID id, Principal p) {
        UUID tenantId = requirePackageManager(p);
        SolarPackage pack = packs.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solar package not found"));
        pack.setActive(false);
        return packs.save(pack);
    }

    public Tenant tenantSettings(Principal p) {
        vendor(p);
        requirePermission(p, "DISCOUNT_POLICY_MANAGE");
        return tenants.findById(p.tenantId()).orElseThrow();
    }

    public Tenant updateDiscountApprovalThreshold(BigDecimal threshold, Principal p) {
        vendor(p);
        requirePermission(p, "DISCOUNT_POLICY_MANAGE");
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) < 0
                || threshold.compareTo(new BigDecimal("100")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount approval threshold must be between 0 and 100");
        var tenant = tenants.findById(p.tenantId()).orElseThrow();
        tenant.setDiscountApprovalThreshold(threshold);
        return tenants.save(tenant);
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
        if (q.getCustomerId() == null) {
            CustomerProfile customer = customers.findByTenantIdAndEmailIgnoreCase(p.tenantId(), q.getEmail())
                    .orElseGet(() -> {
                        if(enterpriseSaas!=null)enterpriseSaas.enforceCustomerLimit(p.tenantId());
                        var created = new CustomerProfile();
                        created.setId(UUID.randomUUID());
                        created.setTenantId(p.tenantId());
                        created.setName(q.getName());
                        created.setEmail(q.getEmail());
                        created.setPhone(q.getPhone());
                        return customers.save(created);
                    });
            q.setCustomerId(customer.getId());
        }
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
        if (l.getAssignedUserId() != null)
            assignmentHistory(l, null, l.getAssignedUserId(), p.userId(), "Initial lead assignment");
        return l;
    }
    
    @Transactional
    public SalesLead updateLead(UUID id, Input x, Principal p) {
        vendor(p);
        requirePermission(p, "LEAD_MANAGE");
        var l = leads.findByIdAndTenantId(id, p.tenantId()).orElseThrow();
        if(enterpriseSaas!=null)enterpriseSaas.assertBranchAccess(l.getBranchId(),p);
        requireEmployee(x.assignedUserId(), p.tenantId());
        var old = l.getStage();
        var oldAssignee = l.getAssignedUserId();
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
        if (!Objects.equals(oldAssignee, l.getAssignedUserId()))
            assignmentHistory(l, oldAssignee, l.getAssignedUserId(), p.userId(), x.reason());
        return l;
    }
    
    private void assignmentHistory(SalesLead lead, UUID oldAssignee, UUID newAssignee, UUID actor, String reason) {
        var history = new LeadHistory();
        history.setId(UUID.randomUUID());
        history.setTenantId(lead.getTenantId());
        history.setLeadId(lead.getId());
        history.setEventType("ASSIGNMENT");
        history.setOldAssignedUserId(oldAssignee);
        history.setNewAssignedUserId(newAssignee);
        history.setActorId(actor);
        history.setReason(reason);
        history.setCreatedAt(Instant.now());
        histories.save(history);
    }

    public List<LeadHistory> leadHistory(UUID leadId, Principal p) {
        vendor(p);
        requirePermission(p, "LEAD_MANAGE");
        if (leads.findByIdAndTenantId(leadId, p.tenantId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        return histories.findByLeadIdAndTenantIdOrderByCreatedAtDesc(leadId, p.tenantId());
    }

    void history(SalesLead l, String old, String now, UUID actor, String reason) {
        var h = new LeadHistory();
        h.setId(UUID.randomUUID());
        h.setTenantId(l.getTenantId());
        h.setLeadId(l.getId());
        h.setEventType("STAGE");
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
        if (x.status() != null && !"SCHEDULED".equals(x.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New surveys must start in SCHEDULED status; use the survey lifecycle endpoints for transitions");
        e.setStatus("SCHEDULED");
        if (e.getScheduledAt() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled date/time is required");
        return surveys.save(e);
    }
    
    @Transactional
    public QuoteView quote(Input x, Principal p) {
        vendor(p);
        requirePermission(p, "QUOTATION_MANAGE");
        requireCustomer(x.customerId(), p.tenantId());
        if (x.leadId() != null && !Objects.equals(requireLead(x.leadId(), p.tenantId()).getCustomerId(), x.customerId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead does not belong to the selected customer");
        if (x.leadId() != null && x.surveyId() == null
                && !surveys.findByLeadIdAndTenantIdOrderByIdDesc(x.leadId(), p.tenantId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An approved site survey is required before creating a quotation for this lead");
        }
        if (x.surveyId() != null) {
            var survey = surveys.findByIdAndTenantId(x.surveyId(), p.tenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Survey not found in this vendor"));
            if (x.leadId() == null || !x.leadId().equals(survey.getLeadId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Survey must belong to selected lead");
            if (!"APPROVED".equals(survey.getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Quotation creation requires an approved site survey");
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
        BigDecimal sub = BigDecimal.ZERO, discountTotal = BigDecimal.ZERO, tax = BigDecimal.ZERO;
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
                if(product.getDiscountPercent()!=null&&product.getDiscountPercent().signum()>0)
                    unitPrice=unitPrice.multiply(BigDecimal.valueOf(100).subtract(product.getDiscountPercent())).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
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
            BigDecimal lineDiscount = base.multiply(requestedDiscount)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal taxableBase = base.subtract(lineDiscount);
            BigDecimal lineTax = taxableBase.multiply(taxPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            i.setDiscountAmount(lineDiscount);
            i.setTaxableAmount(taxableBase);
            i.setTaxAmount(lineTax);
            items.add(quoteItems.save(i));
            sub = sub.add(base);
            discountTotal = discountTotal.add(lineDiscount);
            tax = tax.add(lineTax);
        }
        var disc = discountTotal;
        q.setSubtotal(sub);
        q.setDiscountAmount(disc);
        q.setTaxAmount(tax);
        q.setGrandTotal(sub.subtract(disc).add(tax));
        var approvalThreshold = tenants.findById(p.tenantId()).map(Tenant::getDiscountApprovalThreshold)
                .orElse(BigDecimal.TEN);
        if (approvalThreshold == null) approvalThreshold = BigDecimal.TEN;
        q.setApprovalRequired(q.getDiscountPercent().compareTo(approvalThreshold) > 0);
        q.setStatus(q.isApprovalRequired() ? "PENDING_APPROVAL" : "DRAFT");
        quotes.save(q);
        if(q.isApprovalRequired()) approvalWorkflows.startIfConfigured("QUOTATION",q.getId(),p);
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
        if(enterpriseSaas!=null)enterpriseSaas.assertBranchAccess(q.getBranchId(),p);
        if ("approve".equals(action) || "reject-approval".equals(action)) {
            if (!"PENDING_APPROVAL".equals(q.getStatus()) || !q.isApprovalRequired())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation is not awaiting approval");
            var workflowStatus=approvalWorkflows.decideForEntity(p.tenantId(),"QUOTATION",q.getId(),
                    "approve".equals(action)?"APPROVE":"REJECT",action,p);
            if(workflowStatus.isPresent()){
                if("APPROVED".equals(workflowStatus.get()))q.setStatus("DRAFT");
                else if("REJECTED".equals(workflowStatus.get()))q.setStatus("APPROVAL_REJECTED");
                return quotes.save(q);
            }
            requirePermission(p, "DISCOUNT_APPROVE");
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
        Quotation saved = quotes.save(q);
        if ("send".equals(action)) {
            CustomerProfile recipient = requireCustomer(q.getCustomerId(), p.tenantId());
            notifications.sendBusinessNotification(recipient.getUserId(), p.tenantId(), "QUOTATION_SENT",
                    recipient.getEmail(), "Your solar quotation is ready",
                    "Hello " + recipient.getName() + ",\n\nYour quotation " + q.getQuotationNumber()
                            + " is ready. Total: INR " + q.getGrandTotal()
                            + ". Sign in to your customer portal to review and accept or reject it.");
        }
        return saved;
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
        if(enterpriseSaas!=null)enterpriseSaas.assertBranchAccess(q.getBranchId(),p);
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
        o.setSubtotal(q.getSubtotal());
        o.setDiscountPercent(q.getDiscountPercent());
        o.setDiscountAmount(q.getDiscountAmount());
        o.setTaxTotal(q.getTaxAmount());
        o.setTotal(q.getGrandTotal());
        o.setCreatedAt(Instant.now());
        orders.save(o);
        if(integrationManagement!=null)integrationManagement.enqueueAccountingEvent("ORDER",o.getId().toString(),"UPSERT",
                Map.of("orderNumber",o.getOrderNumber(),"customerId",o.getCustomerId().toString(),"total",o.getTotal()==null?java.math.BigDecimal.ZERO:o.getTotal(),"status",o.getStatus()),p);
        var initialHistory=new OrderStatusHistory();initialHistory.setId(UUID.randomUUID());initialHistory.setTenantId(p.tenantId());initialHistory.setOrderId(o.getId());initialHistory.setOldStatus(null);initialHistory.setNewStatus("CONFIRMED");initialHistory.setReason("Order created from accepted quotation");initialHistory.setActorId(p.userId());initialHistory.setCreatedAt(Instant.now());orderStatusHistory.save(initialHistory);
        for (var qi : quoteItems.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(i -> i.getQuotationId().equals(qid)).toList()) {
            var i = new OrderItem();
            i.setId(UUID.randomUUID());
            i.setTenantId(p.tenantId());
            i.setOrderId(o.getId());
            i.setProductId(qi.getProductId());
            i.setPackageId(qi.getPackageId());
            i.setDescription(qi.getDescription());
            i.setQuantity(qi.getQuantity());
            i.setUnitPrice(qi.getUnitPrice());
            i.setTaxPercent(qi.getTaxPercent());
            i.setLineTotal(qi.getLineTotal());
            i.setDiscountAmount(qi.getDiscountAmount());
            i.setTaxableAmount(qi.getTaxableAmount());
            i.setTaxAmount(qi.getTaxAmount());
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
