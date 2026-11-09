package com.saumyasolar.service;

import com.saumyasolar.api.CatalogModels.*;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CatalogAdminService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EnterpriseSaasService enterpriseSaas;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SalesLeadRepository leads;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final CustomerProfileRepository customers;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    
    public CatalogAdminService(
            ProductRepository products, ProductCategoryRepository categories, CustomerProfileRepository customers,
            AppUserRepository users, RoleRepository roles, PasswordEncoder encoder
    ) {
        this.products = products;
        this.categories = categories;
        this.customers = customers;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }
    
    private UUID tenant(Principal p) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return p.tenantId();
    }
    
    private void require(Principal p, String code) {
        if (p.permissions() == null || !p.permissions().contains(code))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + code);
    }
    
    private Product product(UUID id, UUID t) {
        return products.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
    
    private ProductCategory category(UUID id, UUID t) {
        return categories.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }
    
    private CustomerProfile customer(UUID id, UUID t, Principal p) {
        CustomerProfile customer = customers.findByIdAndTenantId(id, t)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (p.roles() != null && p.roles().contains("SALES_EXECUTIVE")
                && (leads == null || leads.findByTenantIdOrderByIdDesc(t).stream()
                    .noneMatch(lead -> id.equals(lead.getCustomerId()) && p.userId().equals(lead.getAssignedUserId()))))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        return customer;
    }
    
    private void secureUrl(String value, String field) {
        if (value != null && !value.isBlank() && !value.startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must use HTTPS");
    }
    
    private void apply(Product e, ProductInput x) {
        e.setSku(x.sku().trim());
        e.setName(x.name().trim());
        e.setDescription(x.description());
        e.setManufacturer(x.manufacturer());
        e.setModel(x.model());
        e.setTechnology(x.technology());
        e.setPrice(x.price());
        e.setPurchasePrice(x.purchasePrice());
        e.setDiscountPercent(x.discountPercent());
        e.setTaxPercent(x.taxPercent());
        e.setWarrantyYears(x.warrantyYears());
        e.setImageUrl(x.imageUrl());
        e.setDatasheetUrl(x.datasheetUrl());
        e.setReorderLevel(x.reorderLevel());
        e.setUnitOfMeasure(x.unitOfMeasure());
        e.setHsnCode(x.hsnCode());
        e.setSerialized(Boolean.TRUE.equals(x.serialized()));
        e.setAttributesJson(x.attributesJson());
    }
    
    @Transactional
    public Product createProduct(ProductInput x, Principal p) {
        UUID t = tenant(p);
        require(p, "PRODUCT_CREATE");
        if (enterpriseSaas != null) enterpriseSaas.enforceProductLimit(t);
        if (categories.findByIdAndTenantId(x.categoryId(), t).filter(ProductCategory::isActive).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active category belonging to this vendor");
        if (products.findByTenantIdOrderByIdDesc(t).stream().anyMatch(v -> v.getSku().equalsIgnoreCase(x.sku())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        secureUrl(x.imageUrl(), "Image URL");
        secureUrl(x.datasheetUrl(), "Datasheet URL");
        Product e = new Product();
        e.setId(UUID.randomUUID());
        e.setTenantId(t);
        e.setCategoryId(x.categoryId());
        apply(e, x);
        e.setActive(x.active() == null || x.active());
        return products.save(e);
    }
    
    @Transactional
    public Product updateProduct(UUID id, ProductInput x, Principal p) {
        UUID t = tenant(p);
        require(p, "PRODUCT_UPDATE");
        Product e = product(id, t);
        if (categories.findByIdAndTenantId(x.categoryId(), t).filter(ProductCategory::isActive).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an active category from this vendor");
        products.findByTenantIdOrderByIdDesc(t).stream().filter(v -> !v.getId().equals(id) && v.getSku().equalsIgnoreCase(x.sku())).findAny().ifPresent(v -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists in this vendor");
        });
        secureUrl(x.imageUrl(), "Image URL");
        secureUrl(x.datasheetUrl(), "Datasheet URL");
        apply(e, x);
        e.setCategoryId(x.categoryId());
        if (x.active() != null) e.setActive(x.active());
        return products.save(e);
    }
    
    @Transactional
    public Product archiveProduct(UUID id, Principal p) {
        UUID t = tenant(p);
        require(p, "PRODUCT_ARCHIVE");
        Product e = product(id, t);
        e.setActive(false);
        return products.save(e);
    }
    
    @Transactional
    public ProductCategory updateCategory(UUID id, CategoryInput x, Principal p) {
        UUID t = tenant(p);
        require(p, "PRODUCT_UPDATE");
        ProductCategory e = category(id, t);
        categories.findByNameAndTenantId(x.name(), t).filter(v -> !v.getId().equals(id)).ifPresent(v -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name already exists");
        });
        e.setName(x.name().trim());
        e.setDescription(x.description());
        if (x.active() != null) e.setActive(x.active());
        return categories.save(e);
    }
    
    @Transactional
    public ProductCategory archiveCategory(UUID id, Principal p) {
        UUID t = tenant(p);
        require(p, "PRODUCT_ARCHIVE");
        ProductCategory e = category(id, t);
        boolean referenced = products.findByTenantIdOrderByIdDesc(t).stream().anyMatch(x -> id.equals(x.getCategoryId()) && x.isActive());
        if (referenced)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Move or archive active products before archiving this category");
        e.setActive(false);
        return categories.save(e);
    }
    
    @Transactional
    public CustomerProfile updateCustomer(UUID id, CustomerInput x, Principal p) {
        UUID t = tenant(p);
        require(p, "CUSTOMER_UPDATE");
        CustomerProfile e = customer(id, t);
        customers.findByTenantIdAndEmailIgnoreCase(t, x.email()).filter(v -> !v.getId().equals(id)).ifPresent(v -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer email already exists");
        });
        e.setName(x.name().trim());
        e.setEmail(x.email());
        e.setPhone(x.phone());
        e.setAddress(x.address());
        if (x.active() != null) e.setActive(x.active());
        return customers.save(e);
    }
    
    @Transactional
    public CustomerProfile archiveCustomer(UUID id, Principal p) {
        UUID t = tenant(p);
        require(p, "CUSTOMER_UPDATE");
        CustomerProfile e = customer(id, t);
        e.setActive(false);
        return customers.save(e);
    }
    
    @Transactional
    public AppUser updateEmployee(UUID id, EmployeeUpdateInput x, Principal p) {
        UUID t = tenant(p);
        require(p, "USER_UPDATE");
        AppUser u = users.findById(id).filter(v -> t.equals(v.getTenantId()) && "VENDOR".equals(v.getAccountType())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        users.findByEmailIgnoreCase(x.email()).filter(v -> !v.getId().equals(id)).ifPresent(v -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        });
        u.setName(x.name().trim());
        u.setEmail(x.email().trim().toLowerCase(Locale.ROOT));
        if (x.newPassword() != null && !x.newPassword().isBlank()) {
            if (x.newPassword().length() < 12)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters");
            u.setPasswordHash(encoder.encode(x.newPassword()));
            u.setTokenVersion(u.getTokenVersion() + 1);
        }
        if (x.roleName() != null && !x.roleName().isBlank()) {
            Role role = roles.findByNameAndTenantId(x.roleName(), t).filter(r -> !Set.of("VENDOR_ADMIN", "PLATFORM_SUPER_ADMIN", "CUSTOMER").contains(r.getName())).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is not assignable in this vendor"));
            u.getRoles().clear();
            u.getRoles().add(role);
            u.setTokenVersion(u.getTokenVersion() + 1);
        }
        return users.save(u);
    }
}
