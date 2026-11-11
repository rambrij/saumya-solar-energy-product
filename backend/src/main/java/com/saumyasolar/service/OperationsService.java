package com.saumyasolar.service;

import com.saumyasolar.api.OperationsModels.*;
import com.saumyasolar.api.OrderPolicyModels.*;
import com.saumyasolar.config.OrderTransitionPolicyDefaults;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.security.TenantExecutionContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
@Service
public class OperationsService {
    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderStatusHistoryRepository orderHistory;
    private final WarehouseRepository warehouses;
    private final InventoryBalanceRepository balances;
    private final InventoryTransactionRepository transactions;
    private final InventoryReservationRepository reservations;
    private final ProductRepository products;
    private final PackageItemRepository packageItems;
    private final InstallationJobRepository installations;
    private final InstallationChecklistItemRepository checklist;
    private final WarrantyRepository warranties;
    private final AppUserRepository users;
    private final InstallationTeamRepository teams;
    private final InstallationChecklistTemplateRepository templates;
    private final CustomerProfileRepository customers;
    private final EngineerProfileRepository engineerProfiles;
    private final InstallationScheduleHistoryRepository scheduleHistory;
    private final QuotationRepository quotations;
    private final SiteSurveyRepository surveys;
    private final NotificationService notifications;
    private final EnterpriseSaasService enterpriseSaas;
    private final AuditEventRepository audits;
    private final Duration reservationTtl;
    private final OrderTransitionPolicyRepository transitionPolicies;
    
    public OperationsService(
            CustomerOrderRepository a,
            OrderItemRepository b,
            OrderStatusHistoryRepository c,
            WarehouseRepository d,
            InventoryBalanceRepository e,
            InventoryTransactionRepository f,
            InventoryReservationRepository g,
            ProductRepository h,
            PackageItemRepository i,
            InstallationJobRepository j,
            InstallationChecklistItemRepository k,
            WarrantyRepository l,
            AppUserRepository m,
            InstallationTeamRepository teams,
            InstallationChecklistTemplateRepository templates,
            CustomerProfileRepository customers,
            EngineerProfileRepository engineerProfiles,
            InstallationScheduleHistoryRepository scheduleHistory,
            QuotationRepository quotations,
            SiteSurveyRepository surveys,
            NotificationService notifications,
            EnterpriseSaasService enterpriseSaas,
            AuditEventRepository audits,
            @Value("${app.inventory.reservation-ttl:PT24H}") Duration reservationTtl,
            OrderTransitionPolicyRepository transitionPolicies
    ) {
        orders = a;
        orderItems = b;
        orderHistory = c;
        warehouses = d;
        balances = e;
        transactions = f;
        reservations = g;
        products = h;
        packageItems = i;
        installations = j;
        checklist = k;
        warranties = l;
        users = m;
        this.teams = teams;
        this.templates = templates;
        this.customers = customers;
        this.engineerProfiles = engineerProfiles;
        this.scheduleHistory = scheduleHistory;
        this.quotations = quotations;
        this.surveys = surveys;
        this.notifications = notifications;
        this.enterpriseSaas = enterpriseSaas;
        this.audits = audits;
        this.reservationTtl = reservationTtl;
        this.transitionPolicies = transitionPolicies;
    }
    
    private void notifyCustomer(CustomerOrder o, String event, String subject, String body) {
        customers.findByIdAndTenantId(o.getCustomerId(), o.getTenantId()).ifPresent(customer -> notifications.sendBusinessNotification(customer.getUserId(), o.getTenantId(), event, customer.getEmail(), subject, body));
    }
    
    private UUID tenant(Principal p) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return p.tenantId();
    }
    
    private void permission(Principal p, String code) {
        if (p.permissions() == null || !p.permissions().contains(code))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + code);
    }
    
    private CustomerOrder order(UUID id, UUID t) {
        return orders.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
    
    private Warehouse warehouse(UUID id, UUID t, Principal p) {
        Warehouse w = warehouses.findByIdAndTenantId(id, t).filter(Warehouse::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));
        enterpriseSaas.assertWarehouseAccess(w, p);
        return w;
    }
    
    private Product product(UUID id, UUID t) {
        return products.findByIdAndTenantId(id, t).filter(Product::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
    
    private InventoryBalance balance(UUID t, UUID w, UUID pr) {
        return balances.findByTenantIdAndWarehouseIdAndProductId(t, w, pr).orElseGet(() -> {
            InventoryBalance b = new InventoryBalance();
            b.setId(UUID.randomUUID());
            b.setTenantId(t);
            b.setWarehouseId(w);
            b.setProductId(pr);
            b.setOnHand(BigDecimal.ZERO);
            b.setReserved(BigDecimal.ZERO);
            b.setDamaged(BigDecimal.ZERO);
            return b;
        });
    }
    
    private void movement(
            UUID t,
            UUID w,
            UUID pr,
            UUID o,
            UUID ins,
            String type,
            BigDecimal delta,
            String reason,
            UUID actor
    ) {
        InventoryTransaction x = new InventoryTransaction();
        x.setId(UUID.randomUUID());
        x.setTenantId(t);
        x.setWarehouseId(w);
        x.setProductId(pr);
        x.setOrderId(o);
        x.setInstallationId(ins);
        x.setType(type);
        BigDecimal onHandDelta = delta, reservedDelta = BigDecimal.ZERO, damagedDelta = BigDecimal.ZERO;
        if ("RESERVATION".equals(type)) {
            onHandDelta = BigDecimal.ZERO;
            reservedDelta = delta.negate();
        } else if ("RELEASE".equals(type)) {
            onHandDelta = BigDecimal.ZERO;
            reservedDelta = delta.negate();
        } else if ("DAMAGE".equals(type)) {
            onHandDelta = BigDecimal.ZERO;
            damagedDelta = delta.negate();
        } else if ("INSTALLATION_CONSUMPTION".equals(type)) {
            reservedDelta = delta;
        } else if ("RESERVATION_RECONCILIATION".equals(type)) {
            onHandDelta = BigDecimal.ZERO;
            reservedDelta = delta;
        }
        x.setQuantityDelta(onHandDelta);
        x.setReservedDelta(reservedDelta);
        x.setDamagedDelta(damagedDelta);
        x.setReason(reason);
        x.setActorId(actor);
        x.setCreatedAt(Instant.now());
        transactions.save(x);
    }
    
    @Transactional
    public InventoryTransaction reverseInventoryTransaction(UUID transactionId, InventoryReversalInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        InventoryTransaction original = transactions.findByIdAndTenantId(transactionId, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory transaction not found"));
        if ("COMPENSATION".equals(original.getType()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A compensation entry cannot itself be reversed");
        if (!Set.of("RECEIPT", "RETURN", "ISSUE", "DAMAGE", "ADJUSTMENT_INCREASE", "ADJUSTMENT_DECREASE").contains(original.getType()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This linked transaction requires its dedicated reversal workflow");
        if (transactions.existsByTenantIdAndReversalOfTransactionId(t, transactionId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory transaction has already been compensated");
        if (original.getWarehouseId() == null || original.getProductId() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction does not reference a stock balance and cannot be compensated automatically");
        warehouse(original.getWarehouseId(), t, p);
        warehouses.findForUpdate(original.getWarehouseId(), t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));
        InventoryBalance b = balances.findForUpdate(t, original.getWarehouseId(), original.getProductId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Inventory balance no longer exists"));
        BigDecimal q = original.getQuantityDelta() == null ? BigDecimal.ZERO : original.getQuantityDelta();
        BigDecimal r = original.getReservedDelta() == null ? BigDecimal.ZERO : original.getReservedDelta();
        BigDecimal d = original.getDamagedDelta() == null ? BigDecimal.ZERO : original.getDamagedDelta();
        if (q.signum() == 0 && r.signum() == 0 && d.signum() == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction has no compensatable quantity movement");
        BigDecimal nextOnHand = b.getOnHand().subtract(q), nextReserved = b.getReserved().subtract(r), nextDamaged = b.getDamaged().subtract(d);
        if (nextOnHand.signum() < 0 || nextReserved.signum() < 0 || nextDamaged.signum() < 0 || nextReserved.add(nextDamaged).compareTo(nextOnHand) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compensation would create negative or unavailable inventory");
        b.setOnHand(nextOnHand);
        b.setReserved(nextReserved);
        b.setDamaged(nextDamaged);
        balances.save(b);
        InventoryTransaction reversal = new InventoryTransaction();
        reversal.setId(UUID.randomUUID());
        reversal.setTenantId(t);
        reversal.setWarehouseId(original.getWarehouseId());
        reversal.setProductId(original.getProductId());
        reversal.setOrderId(original.getOrderId());
        reversal.setInstallationId(original.getInstallationId());
        reversal.setServiceRequestId(original.getServiceRequestId());
        reversal.setReversalOfTransactionId(original.getId());
        reversal.setType("COMPENSATION");
        reversal.setQuantityDelta(q.negate());
        reversal.setReservedDelta(r.negate());
        reversal.setDamagedDelta(d.negate());
        reversal.setReason("Reversal of " + original.getTransactionReference() + ": " + x.reason());
        reversal.setActorId(p.userId());
        reversal.setCreatedAt(Instant.now());
        return transactions.save(reversal);
    }
    
    public List<Warehouse> warehouses(Principal p) {
        permission(p, "INVENTORY_VIEW");
        return enterpriseSaas.filterWarehouses(warehouses.findByTenantIdOrderByNameAsc(tenant(p)), p);
    }
    
    @Transactional
    public Warehouse createWarehouse(WarehouseInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        enterpriseSaas.enforceWarehouseLimit(t);
        Warehouse w = new Warehouse();
        w.setId(UUID.randomUUID());
        w.setTenantId(t);
        w.setCode(x.code().trim().toUpperCase(Locale.ROOT));
        w.setName(x.name().trim());
        w.setAddress(x.address());
        w.setActive(true);
        w.setCreatedAt(Instant.now());
        return warehouses.save(w);
    }
    
    @Transactional
    public Warehouse updateWarehouse(UUID id, WarehouseInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        Warehouse w = warehouse(id, t, p);
        if (warehouses.findByTenantIdOrderByNameAsc(t).stream().anyMatch(v -> !v.getId().equals(id) && v.getCode().equalsIgnoreCase(x.code())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Warehouse code already exists");
        w.setCode(x.code().trim().toUpperCase(Locale.ROOT));
        w.setName(x.name().trim());
        w.setAddress(x.address());
        return warehouses.save(w);
    }
    
    @Transactional
    public Warehouse archiveWarehouse(UUID id, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        Warehouse w = warehouse(id, t, p);
        boolean stock = balances.findByTenantIdOrderByWarehouseIdAscProductIdAsc(t).stream().anyMatch(b -> b.getWarehouseId().equals(id) && ( b.getOnHand().signum() > 0 || b.getReserved().signum() > 0 || b.getDamaged().signum() > 0 ));
        if (stock)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Move or reconcile stock before archiving this warehouse");
        w.setActive(false);
        return warehouses.save(w);
    }
    
    public List<InventoryBalance> balances(Principal p) {
        permission(p, "INVENTORY_VIEW");
        UUID t = tenant(p);
        Set<UUID> allowed = enterpriseSaas.filterWarehouses(warehouses.findByTenantIdOrderByNameAsc(t), p).stream().map(Warehouse::getId).collect(java.util.stream.Collectors.toSet());
        return balances.findByTenantIdOrderByWarehouseIdAscProductIdAsc(t).stream().filter(b -> allowed.contains(b.getWarehouseId())).toList();
    }
    
    public List<InventoryTransaction> ledger(Principal p) {
        permission(p, "INVENTORY_VIEW");
        UUID t = tenant(p);
        Set<UUID> allowed = enterpriseSaas.filterWarehouses(warehouses.findByTenantIdOrderByNameAsc(t), p).stream().map(Warehouse::getId).collect(java.util.stream.Collectors.toSet());
        return transactions.findByTenantIdOrderByCreatedAtDesc(t).stream().filter(x -> allowed.contains(x.getWarehouseId())).toList();
    }
    
    @Transactional
    public InventoryBalance receive(ReceiptInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_RECEIVE");
        warehouse(x.warehouseId(), t, p);
        product(x.productId(), t);
        warehouses.findForUpdate(x.warehouseId(), t).orElseThrow();
        InventoryBalance b = balance(t, x.warehouseId(), x.productId());
        b.setOnHand(b.getOnHand().add(x.quantity()));
        balances.save(b);
        movement(t, x.warehouseId(), x.productId(), null, null, "RECEIPT", x.quantity(), x.reason(), p.userId());
        return b;
    }
    
    @Transactional
    public InventoryBalance returnStock(ReceiptInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_RECEIVE");
        warehouse(x.warehouseId(), t, p);
        product(x.productId(), t);
        warehouses.findForUpdate(x.warehouseId(), t).orElseThrow();
        InventoryBalance b = balance(t, x.warehouseId(), x.productId());
        b.setOnHand(b.getOnHand().add(x.quantity()));
        balances.save(b);
        movement(t, x.warehouseId(), x.productId(), null, null, "RETURN", x.quantity(), x.reason(), p.userId());
        return b;
    }
    
    @Transactional
    public InventoryBalance damage(DamageInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        warehouse(x.warehouseId(), t, p);
        product(x.productId(), t);
        warehouses.findForUpdate(x.warehouseId(), t).orElseThrow();
        InventoryBalance b = balance(t, x.warehouseId(), x.productId());
        if (b.getAvailable().compareTo(x.quantity()) < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Damaged quantity exceeds available stock");
        b.setDamaged(b.getDamaged().add(x.quantity()));
        balances.save(b);
        movement(t, x.warehouseId(), x.productId(), null, null, "DAMAGE", x.quantity().negate(), x.reason(), p.userId());
        return b;
    }
    
    @Transactional
    public InventoryBalance issue(IssueInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        warehouse(x.warehouseId(), t, p);
        product(x.productId(), t);
        warehouses.findForUpdate(x.warehouseId(), t).orElseThrow();
        InventoryBalance b = balance(t, x.warehouseId(), x.productId());
        if (b.getAvailable().compareTo(x.quantity()) < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock for issue");
        b.setOnHand(b.getOnHand().subtract(x.quantity()));
        balances.save(b);
        movement(t, x.warehouseId(), x.productId(), null, null, "ISSUE", x.quantity().negate(), x.reason(), p.userId());
        return b;
    }
    
    public List<Map<String, Object>> lowStock(Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_VIEW");
        Set<UUID> allowed = enterpriseSaas.filterWarehouses(warehouses.findByTenantIdOrderByNameAsc(t), p).stream().map(Warehouse::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Product> byId = new HashMap<>();
        products.findByTenantIdOrderByIdDesc(t).forEach(v -> byId.put(v.getId(), v));
        List<Map<String, Object>> out = new ArrayList<>();
        for (InventoryBalance b : balances.findByTenantIdOrderByWarehouseIdAscProductIdAsc(t)) {
            if (!allowed.contains(b.getWarehouseId())) continue;
            Product v = byId.get(b.getProductId());
            if (v != null && v.getReorderLevel() != null && b.getAvailable().compareTo(v.getReorderLevel()) <= 0)
                out.add(Map.of("warehouseId", b.getWarehouseId(), "productId", v.getId(), "sku", v.getSku(), "productName", v.getName(), "available", b.getAvailable(), "reorderLevel", v.getReorderLevel()));
        }
        return out;
    }
    
    @Transactional
    public InventoryBalance adjust(AdjustmentInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_ADJUST");
        warehouse(x.warehouseId(), t, p);
        product(x.productId(), t);
        warehouses.findForUpdate(x.warehouseId(), t).orElseThrow();
        InventoryBalance b = balance(t, x.warehouseId(), x.productId());
        BigDecimal delta = "INCREASE".equals(x.direction()) ? x.quantity() : x.quantity().negate();
        if (b.getAvailable().add(delta).signum() < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adjustment would make available stock negative");
        b.setOnHand(b.getOnHand().add(delta));
        balances.save(b);
        movement(t, x.warehouseId(), x.productId(), null, null, "ADJUSTMENT_" + x.direction(), delta, x.reason(), p.userId());
        return b;
    }
    
    @Transactional
    public Map<String, Object> transfer(TransferInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INVENTORY_TRANSFER");
        if (x.sourceWarehouseId().equals(x.targetWarehouseId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose two different warehouses");
        warehouse(x.sourceWarehouseId(), t, p);
        warehouse(x.targetWarehouseId(), t, p);
        product(x.productId(), t);
        UUID first = x.sourceWarehouseId().compareTo(x.targetWarehouseId()) < 0 ? x.sourceWarehouseId() : x.targetWarehouseId(), second = first.equals(x.sourceWarehouseId()) ? x.targetWarehouseId() : x.sourceWarehouseId();
        warehouses.findForUpdate(first, t).orElseThrow();
        warehouses.findForUpdate(second, t).orElseThrow();
        InventoryBalance from = balance(t, x.sourceWarehouseId(), x.productId());
        if (from.getAvailable().compareTo(x.quantity()) < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock");
        from.setOnHand(from.getOnHand().subtract(x.quantity()));
        balances.save(from);
        InventoryBalance to = balance(t, x.targetWarehouseId(), x.productId());
        to.setOnHand(to.getOnHand().add(x.quantity()));
        balances.save(to);
        movement(t, x.sourceWarehouseId(), x.productId(), null, null, "TRANSFER_OUT", x.quantity().negate(), x.reason(), p.userId());
        movement(t, x.targetWarehouseId(), x.productId(), null, null, "TRANSFER_IN", x.quantity(), x.reason(), p.userId());
        return Map.of("source", from, "target", to);
    }
    
    @Transactional
    public List<InventoryReservation> reserve(UUID orderId, ReservationInput x, Principal p) {
        UUID t=tenant(p); permission(p,"INVENTORY_RESERVE");
        CustomerOrder o=orders.findForUpdate(orderId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        Instant now=Instant.now();
        List<InventoryReservation> active=reservations.findByTenantIdAndOrderId(t,orderId).stream().filter(r->"RESERVED".equals(r.getStatus())).toList();
        if(!active.isEmpty()) { if(active.stream().anyMatch(r->r.getExpiresAt()==null||r.getExpiresAt().isAfter(now))) return active;
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Expired reservation is awaiting release; retry shortly"); }
        if(!Set.of("CONFIRMED","PAYMENT_PENDING","PAYMENT_PARTIAL","PAID").contains(o.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Order is not eligible for material reservation");
        Warehouse w=warehouse(x.warehouseId(),t,p); warehouses.findForUpdate(w.getId(),t).orElseThrow();
        Map<UUID,BigDecimal> required=new LinkedHashMap<>();
        for(OrderItem line:orderItems.findByTenantIdOrderByIdDesc(t).stream().filter(i->i.getOrderId().equals(orderId)).toList()) {
            if(line.getProductId()!=null) required.merge(line.getProductId(),BigDecimal.valueOf(line.getQuantity()),BigDecimal::add);
            else if(line.getPackageId()!=null) for(PackageItem pi:packageItems.findByTenantIdOrderByIdDesc(t).stream().filter(i->i.getPackageId().equals(line.getPackageId())&&!i.isOptionalItem()).toList()) required.merge(pi.getProductId(),BigDecimal.valueOf((long)pi.getQuantity()*line.getQuantity()),BigDecimal::add);
        }
        if(required.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"No stock-managed product components found on order");
        List<InventoryBalance> locked=new ArrayList<>();
        for(UUID id:required.keySet()) { product(id,t); locked.add(balances.findForUpdate(t,w.getId(),id).orElseGet(()->balance(t,w.getId(),id))); }
        int n=0; for(var e:required.entrySet()) if(locked.get(n++).getAvailable().compareTo(e.getValue())<0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock for product "+e.getKey());
        if(reservationTtl==null||reservationTtl.isNegative()||reservationTtl.isZero()) throw new IllegalStateException("Reservation TTL must be positive");
        Instant expires=now.plus(reservationTtl); String previous=o.getStatus(); List<InventoryReservation> out=new ArrayList<>(); n=0;
        for(var e:required.entrySet()) {
            InventoryBalance b=locked.get(n++); b.setReserved(b.getReserved().add(e.getValue())); balances.save(b);
            InventoryReservation r=new InventoryReservation(); r.setId(UUID.randomUUID()); r.setTenantId(t); r.setOrderId(orderId); r.setWarehouseId(w.getId()); r.setProductId(e.getKey()); r.setQuantity(e.getValue()); r.setStatus("RESERVED"); r.setCreatedAt(now); r.setUpdatedAt(now); r.setExpiresAt(expires); r.setPreReservationOrderStatus(previous); out.add(reservations.save(r));
            movement(t,w.getId(),e.getKey(),orderId,null,"RESERVATION",e.getValue().negate(),"Order reservation: "+x.reason(),p.userId());
        }
        transitionOrder(o,"MATERIAL_RESERVED","Stock reserved: "+x.reason(),p); return out;
    }

    public List<InventoryReservation> reservations(Principal p) {
        UUID t=tenant(p); permission(p,"INVENTORY_VIEW");
        Set<UUID> allowed=enterpriseSaas.filterWarehouses(warehouses.findByTenantIdOrderByNameAsc(t),p).stream().map(Warehouse::getId).collect(java.util.stream.Collectors.toSet());
        return reservations.findByTenantIdOrderByCreatedAtDesc(t).stream().filter(r->allowed.contains(r.getWarehouseId())).toList();
    }

    @Transactional
    public List<InventoryReservation> release(UUID orderId,String reason,Principal p) {
        UUID t=tenant(p); permission(p,"INVENTORY_RESERVE");
        CustomerOrder o=orders.findForUpdate(orderId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        List<InventoryReservation> rows=reservations.findByTenantIdAndOrderId(t,orderId).stream().filter(r->"RESERVED".equals(r.getStatus())).toList();
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"No active reservations");
        if(!"MATERIAL_RESERVED".equals(o.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservations can only be released before installation scheduling");
        for(InventoryReservation r:rows) { warehouse(r.getWarehouseId(),t,p); releaseReservationRow(t,o,r,reason,p.userId(),"USER"); }
        String prior=rows.stream().map(InventoryReservation::getPreReservationOrderStatus).filter(Objects::nonNull).findFirst().orElse("CONFIRMED");
        transitionOrder(o,prior,reason,p); return rows;
    }

    private void releaseReservationRow(UUID t,CustomerOrder o,InventoryReservation r,String reason,UUID actor,String actorType) {
        warehouses.findForUpdate(r.getWarehouseId(),t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found"));
        InventoryBalance b=balances.findForUpdate(t,r.getWarehouseId(),r.getProductId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Reservation balance missing; reconciliation required"));
        if(b.getReserved().compareTo(r.getQuantity())<0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reserved balance is lower than reservation; reconciliation required");
        b.setReserved(b.getReserved().subtract(r.getQuantity())); balances.save(b);
        Instant now=Instant.now(); r.setStatus("RELEASED"); r.setUpdatedAt(now); r.setReleasedAt(now); r.setReleaseReason(reason); r.setReleasedBy(actor); reservations.save(r);
        movement(t,r.getWarehouseId(),r.getProductId(),o.getId(),null,"RELEASE",r.getQuantity(),reason,actor);
        AuditEvent event=new AuditEvent(); event.setId(UUID.randomUUID()); event.setTenantId(t); event.setActorId(actor); event.setActorType(actorType); event.setAction("SYSTEM".equals(actorType)?"INVENTORY_RESERVATION_EXPIRED":"INVENTORY_RESERVATION_RELEASED"); event.setEntityType("INVENTORY_RESERVATION"); event.setEntityId(r.getId().toString()); event.setOutcome("SUCCESS"); event.setReason(reason); event.setCreatedAt(now); audits.save(event);
    }

    @Transactional
    public int expireReservationsForTenant(UUID t,Instant now) {
        if(!t.equals(TenantExecutionContext.requireTenantId())) throw new IllegalStateException("Tenant scope mismatch");
        int released=0;
        for(InventoryReservation candidate:reservations.findTop500ByTenantIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(t,"RESERVED",now)) {
            InventoryReservation r=reservations.findByIdAndTenantIdForUpdate(candidate.getId(),t).orElse(null);
            if(r==null||!"RESERVED".equals(r.getStatus())||r.getExpiresAt()==null||r.getExpiresAt().isAfter(now)) continue;
            CustomerOrder o=orders.findForUpdate(r.getOrderId(),t).orElse(null); if(o==null||!"MATERIAL_RESERVED".equals(o.getStatus())) continue;
            releaseReservationRow(t,o,r,"Reservation expired at "+r.getExpiresAt(),null,"SYSTEM"); released++;
            if(!reservations.existsByTenantIdAndOrderIdAndStatus(t,o.getId(),"RESERVED")) {
                String prior=r.getPreReservationOrderStatus()==null?"CONFIRMED":r.getPreReservationOrderStatus();
                transitionOrder(o,prior,"All order reservations expired; stock was released",null);
            }
        }
        return released;
    }

    @Transactional
    public int reconcileReservationsForTenant(UUID t) {
        if(!t.equals(TenantExecutionContext.requireTenantId())) throw new IllegalStateException("Tenant scope mismatch");
        record StockKey(UUID warehouseId,UUID productId) {}
        Map<StockKey,BigDecimal> expected=new HashMap<>();
        for(InventoryReservation r:reservations.findByTenantIdAndStatus(t,"RESERVED")) expected.merge(new StockKey(r.getWarehouseId(),r.getProductId()),r.getQuantity(),BigDecimal::add);
        int corrected=0;
        for(InventoryBalance b:balances.findByTenantIdOrderByWarehouseIdAscProductIdAsc(t)) {
            StockKey key=new StockKey(b.getWarehouseId(),b.getProductId()); BigDecimal target=expected.getOrDefault(key,BigDecimal.ZERO); expected.remove(key);
            BigDecimal current=b.getReserved()==null?BigDecimal.ZERO:b.getReserved(), damaged=b.getDamaged()==null?BigDecimal.ZERO:b.getDamaged();
            if(current.compareTo(target)==0) continue;
            if(target.signum()<0||target.add(damaged).compareTo(b.getOnHand())>0) { inventoryReconciliationAudit(t,b.getId().toString(),current,target,"Correction violates stock invariants",false); continue; }
            b.setReserved(target); balances.save(b); movement(t,b.getWarehouseId(),b.getProductId(),null,null,"RESERVATION_RECONCILIATION",target.subtract(current),"Reconciled reserved quantity from active reservation records",null);
            inventoryReconciliationAudit(t,b.getId().toString(),current,target,"Reserved balance reconciled from active reservation records",true); corrected++;
        }
        for(var e:expected.entrySet()) if(e.getValue().signum()>0) inventoryReconciliationAudit(t,e.getKey().warehouseId()+":"+e.getKey().productId(),BigDecimal.ZERO,e.getValue(),"Reservation references missing balance; manual review required",false);
        return corrected;
    }

    private void inventoryReconciliationAudit(UUID t,String id,BigDecimal before,BigDecimal after,String reason,boolean success) {
        AuditEvent event=new AuditEvent(); event.setId(UUID.randomUUID()); event.setTenantId(t); event.setActorType("SYSTEM"); event.setAction(success?"INVENTORY_RESERVATION_RECONCILED":"INVENTORY_RESERVATION_RECONCILIATION_BLOCKED"); event.setEntityType("INVENTORY_BALANCE"); event.setEntityId(id); event.setOutcome(success?"SUCCESS":"FAILURE"); event.setReason(reason); event.setBeforeJson("{\"reserved\":\""+before+"\"}"); event.setAfterJson("{\"reserved\":\""+after+"\"}"); event.setCreatedAt(Instant.now()); audits.save(event);
    }

    private void status(CustomerOrder o,String old,Principal p,String reason) {
        OrderStatusHistory h=new OrderStatusHistory(); h.setId(UUID.randomUUID()); h.setTenantId(o.getTenantId()); h.setOrderId(o.getId()); h.setOldStatus(old); h.setNewStatus(o.getStatus()); h.setReason(reason); h.setActorId(p.userId()); h.setActorType("USER"); h.setCreatedAt(Instant.now()); orderHistory.save(h);
    }

    @Transactional
    public CustomerOrder transition(UUID id, OrderTransitionInput x, Principal p) {
        UUID t=tenant(p);
        CustomerOrder o=orders.findForUpdate(id,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        enterpriseSaas.assertBranchAccess(o.getBranchId(),p);
        return transitionOrder(o,x.status(),x.reason(),p);
    }

    public List<TransitionOption> transitionOptions(Principal p) {
        UUID t=tenant(p); permission(p,"ORDER_VIEW");
        Set<String> granted=p.permissions()==null?Set.of():p.permissions();
        return effectiveTransitionRules(t).stream()
                .filter(OrderTransitionPolicy::isEnabled)
                .filter(rule->granted.contains(rule.getRequiredPermission()))
                .map(rule->new TransitionOption(rule.getFromStatus(),rule.getToStatus(),rule.getRequiredPermission()))
                .toList();
    }

    public List<PolicyRule> transitionPolicy(Principal p) {
        UUID t=tenant(p); permission(p,"ORDER_POLICY_MANAGE");
        return effectiveTransitionRules(t).stream()
                .map(rule->new PolicyRule(rule.getFromStatus(),rule.getToStatus(),rule.getRequiredPermission(),rule.isEnabled()))
                .toList();
    }

    @Transactional
    public List<PolicyRule> updateTransitionPolicy(TransitionPolicyUpdate input, Principal p) {
        UUID t=tenant(p); permission(p,"ORDER_POLICY_MANAGE");
        if(input==null||input.transitions()==null||input.transitions().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one order transition policy entry is required");
        Set<String> seen=new HashSet<>();
        for (PolicyToggle toggle:input.transitions()) {
            if(toggle==null||toggle.fromStatus()==null||toggle.fromStatus().isBlank()||toggle.fromStatus().length()>40
                    ||toggle.toStatus()==null||toggle.toStatus().isBlank()||toggle.toStatus().length()>40)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Transition source and target statuses are required and must be at most 40 characters");
            String from=toggle.fromStatus().trim().toUpperCase(Locale.ROOT);
            String to=toggle.toStatus().trim().toUpperCase(Locale.ROOT);
            String key=from+"->"+to;
            if(!seen.add(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Duplicate transition policy entry: "+key);
            OrderTransitionPolicyDefaults.Rule baseline=OrderTransitionPolicyDefaults.RULES.stream()
                    .filter(rule->rule.fromStatus().equals(from)&&rule.toStatus().equals(to)).findFirst()
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported order transition: "+key));
            OrderTransitionPolicy policy=transitionPolicies.findByTenantIdAndFromStatusAndToStatus(t,from,to)
                    .orElseGet(()->{
                        OrderTransitionPolicy created=new OrderTransitionPolicy();
                        created.setId(UUID.randomUUID()); created.setTenantId(t); created.setFromStatus(from);
                        created.setToStatus(to); created.setRequiredPermission(baseline.permission());
                        created.setCreatedAt(Instant.now()); return created;
                    });
            // The permission required for an edge is code-owned; tenant configuration can only enable/disable it.
            if(!baseline.permission().equals(policy.getRequiredPermission()))
                throw new IllegalStateException("Stored order transition permission differs from the approved permission catalogue");
            boolean wasEnabled=policy.isEnabled();
            policy.setEnabled(toggle.enabled()); policy.setUpdatedAt(Instant.now()); transitionPolicies.save(policy);
            AuditEvent event=new AuditEvent(); event.setId(UUID.randomUUID()); event.setTenantId(t); event.setActorId(p.userId());
            event.setActorType("USER"); event.setAction("ORDER_TRANSITION_POLICY_UPDATED"); event.setEntityType("ORDER_TRANSITION_POLICY");
            event.setEntityId(from+"->"+to); event.setOutcome("SUCCESS");
            event.setBeforeJson("{\"enabled\":"+wasEnabled+",\"requiredPermission\":\""+baseline.permission()+"\"}");
            event.setAfterJson("{\"enabled\":"+toggle.enabled()+",\"requiredPermission\":\""+baseline.permission()+"\"}");
            event.setReason("Vendor order transition policy changed"); event.setCreatedAt(Instant.now()); audits.save(event);
        }
        return transitionPolicy(p);
    }

    private List<OrderTransitionPolicy> effectiveTransitionRules(UUID tenantId) {
        List<OrderTransitionPolicy> effective=new ArrayList<>();
        for(OrderTransitionPolicyDefaults.Rule baseline:OrderTransitionPolicyDefaults.RULES) {
            OrderTransitionPolicy row=transitionPolicies.findByTenantIdAndFromStatusAndToStatus(
                    tenantId,baseline.fromStatus(),baseline.toStatus()).orElseGet(()->transitionPolicies
                    .findByTenantIdIsNullAndFromStatusAndToStatus(baseline.fromStatus(),baseline.toStatus())
                    .orElseGet(()->{
                        OrderTransitionPolicy fallback=new OrderTransitionPolicy();
                        fallback.setFromStatus(baseline.fromStatus()); fallback.setToStatus(baseline.toStatus());
                        fallback.setRequiredPermission(baseline.permission()); fallback.setEnabled(true); return fallback;
                    }));
            effective.add(row);
        }
        return effective;
    }

    private CustomerOrder transitionOrder(CustomerOrder order,String requestedStatus,String reason,Principal actor) {
        String from=order.getStatus();
        String to=requestedStatus==null?"":requestedStatus.trim().toUpperCase(Locale.ROOT);
        OrderTransitionPolicy policy=transitionPolicies.findByTenantIdAndFromStatusAndToStatus(
                order.getTenantId(),from,to).orElseGet(()->transitionPolicies
                .findByTenantIdIsNullAndFromStatusAndToStatus(from,to).orElse(null));
        if(policy==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Order cannot move from "+from+" to "+to);
        if(!policy.isEnabled() && actor!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Order transition "+from+" → "+to+" is disabled by this vendor's policy");
        if(actor!=null) permission(actor,policy.getRequiredPermission());
        if("CANCELLED".equals(to)) {
            if(reason==null||reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Order cancellation reason is required");
            if(reservations.findByTenantIdAndOrderId(order.getTenantId(),order.getId()).stream().anyMatch(r->"RESERVED".equals(r.getStatus())))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Release active inventory reservations before cancelling the order");
        }
        order.setStatus(to); orders.save(order);
        OrderStatusHistory h=new OrderStatusHistory(); h.setId(UUID.randomUUID()); h.setTenantId(order.getTenantId());
        h.setOrderId(order.getId()); h.setOldStatus(from); h.setNewStatus(to);
        h.setReason(reason==null||reason.isBlank()?"System lifecycle transition":reason.trim());
        h.setActorId(actor==null?null:actor.userId()); h.setActorType(actor==null?"SYSTEM":"USER");
        h.setCreatedAt(Instant.now()); orderHistory.save(h);
        return order;
    }

    public List<OrderStatusHistory> orderHistory(UUID id, Principal p) {
        UUID t = tenant(p);
        permission(p, "ORDER_VIEW");
        order(id, t);
        return orderHistory.findByTenantIdAndOrderIdOrderByCreatedAtAsc(t, id);
    }
    
    public List<InstallationTeam> teams(Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_VIEW");
        List<InstallationTeam> active = teams.findByTenantIdOrderByNameAsc(t).stream().filter(InstallationTeam::isActive).toList();
        if (p.permissions() == null || !p.permissions().contains("INSTALLATION_ASSIGN"))
            return active.stream().filter(team -> team.getMemberIdsJson() != null && team.getMemberIdsJson().contains(p.userId().toString())).toList();
        return active;
    }
    
    @Transactional
    public InstallationTeam updateTeam(UUID id, TeamInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_ASSIGN");
        InstallationTeam team = teams.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        validateTeamMembers(t, x.memberIds());
        team.setName(x.name().trim());
        team.setMemberIdsJson(x.memberIds().stream().map(v -> "\"" + v + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]")));
        return teams.save(team);
    }
    
    @Transactional
    public InstallationTeam archiveTeam(UUID id, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_ASSIGN");

        InstallationTeam team = teams.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean assigned = installations.findByTenantIdOrderByScheduledAtAsc(t).stream().anyMatch(j -> id.equals(j.getTeamId()) && Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED").contains(j.getStatus()));
        if (assigned)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Team is assigned to an active installation");
        team.setActive(false);
        return teams.save(team);
    }
    
    private void validateTeamMembers(UUID t, List<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A team must include at least one enabled installer/engineer");
        Set<UUID> ids = new HashSet<>(memberIds);
        if (ids.size() != memberIds.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate team members are not allowed");
        for (UUID id : ids)
            if (users.findByIdAndTenantId(id, t).filter(u -> u.isEnabled() && u.getRoles().stream().anyMatch(r -> Set.of("SERVICE_ENGINEER", "INSTALLER", "TECHNICIAN", "OPERATIONS_MANAGER").contains(r.getName()))).isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every team member must be an enabled same-tenant installer or engineer");
    }
    
    @Transactional
    public InstallationTeam createTeam(TeamInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_ASSIGN");

        if (teams.findByTenantIdOrderByNameAsc(t).stream().anyMatch(v -> v.getName().equalsIgnoreCase(x.name())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Team name already exists");
        validateTeamMembers(t, x.memberIds());
        Set<UUID> ids = new HashSet<>(x.memberIds());
        InstallationTeam team = new InstallationTeam();
        team.setId(UUID.randomUUID());
        team.setTenantId(t);
        team.setName(x.name().trim());
        team.setMemberIdsJson(ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]")));
        team.setActive(true);
        team.setCreatedAt(Instant.now());
        return teams.save(team);
    }
    
    public List<InstallationChecklistTemplate> checklistTemplates(Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_VIEW");
        return templates.findByTenantIdAndActiveTrueOrderByCodeAsc(t);
    }
    
    @Transactional
    public InstallationChecklistTemplate createChecklistTemplate(ChecklistTemplateInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_UPDATE");

        if (templates.findByTenantIdAndActiveTrueOrderByCodeAsc(t).stream().anyMatch(v -> v.getCode().equalsIgnoreCase(x.code())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checklist code already exists");
        InstallationChecklistTemplate v = new InstallationChecklistTemplate();
        v.setId(UUID.randomUUID());
        v.setTenantId(t);
        v.setCode(x.code().trim().toUpperCase(Locale.ROOT));
        v.setTitle(x.title().trim());
        v.setMandatory(x.mandatory());
        v.setActive(true);
        v.setCreatedAt(Instant.now());
        return templates.save(v);
    }
    
    @Transactional
    public InstallationChecklistTemplate updateChecklistTemplate(UUID id, ChecklistTemplateInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_UPDATE");

        InstallationChecklistTemplate v = templates.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.setTitle(x.title().trim());
        v.setMandatory(x.mandatory());
        return templates.save(v);
    }
    
    @Transactional
    public InstallationChecklistTemplate archiveChecklistTemplate(UUID id, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_UPDATE");

        InstallationChecklistTemplate v = templates.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.setActive(false);
        return templates.save(v);
    }
    
    public List<InstallationJob> installations(Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_VIEW");
        List<InstallationJob> all = installations.findByTenantIdOrderByScheduledAtAsc(t);
        List<InstallationJob> visible = p.permissions() != null && p.permissions().contains("INSTALLATION_ASSIGN")
                ? all : all.stream().filter(j -> p.userId().equals(j.getAssignedEngineerId())).toList();
        return enterpriseSaas.filterBranchRecords(visible, InstallationJob::getBranchId, p);
    }
    
    @Transactional
    public InstallationJob createInstallation(InstallationInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_SCHEDULE");

        CustomerOrder o = order(x.orderId(), t);
        if (!x.scheduledAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Installation schedule must be in the future");
        UUID approvedSurveyId = x.surveyId();
        UUID expectedLeadId = null;
        if (o.getQuotationId() != null) {
            Quotation quote = quotations.findByIdAndTenantId(o.getQuotationId(), t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order quotation not found"));
            expectedLeadId = quote.getLeadId();
            if (quote.getSurveyId() != null) {
                if (approvedSurveyId != null && !approvedSurveyId.equals(quote.getSurveyId()))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected survey differs from the quotation survey");
                approvedSurveyId = quote.getSurveyId();
            }
        }
        if (approvedSurveyId != null) {
            SiteSurvey approvedSurvey = surveys.findByIdAndTenantId(approvedSurveyId, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site survey not found"));
            if (!"APPROVED".equals(approvedSurvey.getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Installation requires an approved site survey");
            if (expectedLeadId != null && !expectedLeadId.equals(approvedSurvey.getLeadId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved survey must belong to the quotation lead");
        }
        if (!Set.of("MATERIAL_RESERVED", "INSTALLATION_SCHEDULED").contains(o.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserve order materials before scheduling installation");
        if (users.findByIdAndTenantId(x.assignedEngineerId(), t).filter(u -> u.isEnabled() && "VENDOR".equals(u.getAccountType()) && u.getRoles().stream().anyMatch(r -> Set.of("SERVICE_ENGINEER", "INSTALLER", "TECHNICIAN", "OPERATIONS_MANAGER", "VENDOR_ADMIN").contains(r.getName()))).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Engineer must be an enabled same-tenant installer");
        users.findForUpdate(x.assignedEngineerId(), t).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Engineer is not available in this tenant"));
        teams.findForUpdate(x.teamId(), t).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active team from this vendor"));
        if (installations.findByTenantIdAndOrderId(t, o.getId()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Installation already exists");
        if (installations.findByTenantIdOrderByScheduledAtAsc(t).stream().anyMatch(existing -> Set.of("SCHEDULED", "IN_PROGRESS").contains(existing.getStatus()) && x.scheduledAt().isBefore(existing.getScheduledAt().plusSeconds(7200)) && x.scheduledAt().plusSeconds(7200).isAfter(existing.getScheduledAt()) && ( Objects.equals(x.assignedEngineerId(), existing.getAssignedEngineerId()) || Objects.equals(x.teamId(), existing.getTeamId()) )))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Engineer or team already has an installation in the requested two-hour window");
        EngineerProfile profile = engineerProfiles.findForUpdate(t, x.assignedEngineerId()).orElse(null);
        if (profile != null && profile.getAvailableFrom() != null) {
            java.time.LocalTime requested = x.scheduledAt().atZone(java.time.ZoneId.systemDefault()).toLocalTime();
            java.time.LocalTime requestedEnd = requested.plusHours(2);
            if (requested.isBefore(profile.getAvailableFrom()) || !requestedEnd.isAfter(requested) || requestedEnd.isAfter(profile.getAvailableUntil()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-hour installation slot is outside the engineer availability window");
        }
        if (profile != null && installations.findByTenantIdOrderByScheduledAtAsc(t).stream().filter(existing -> x.assignedEngineerId().equals(existing.getAssignedEngineerId()) && Set.of("SCHEDULED", "IN_PROGRESS").contains(existing.getStatus())).count() >= profile.getMaxConcurrentJobs())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Engineer has reached configured concurrent workload capacity");
        InstallationTeam team = teams.findByIdAndTenantId(x.teamId(), t).filter(InstallationTeam::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active team from this vendor"));
        if (!team.getMemberIdsJson().contains(x.assignedEngineerId().toString()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned engineer must belong to the selected team");
        InstallationJob j = new InstallationJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(t);
        j.setOrderId(o.getId());
        j.setCustomerId(o.getCustomerId());
        j.setApprovedSurveyId(approvedSurveyId);
        j.setSiteAddress(x.siteAddress());
        j.setScheduledAt(x.scheduledAt());
        j.setAssignedEngineerId(x.assignedEngineerId());
        j.setTeamId(team.getId());
        j.setTeamName(team.getName());
        j.setCapacityKw(x.capacityKw());
        j.setNotes(x.notes());
        j.setStatus("SCHEDULED");
        j.setCreatedAt(Instant.now());
        installations.save(j);
        List<InstallationChecklistTemplate> selectedTemplates = templates.findByTenantIdAndActiveTrueOrderByCodeAsc(t);
        if (selectedTemplates.isEmpty()) {
            String[][] defaults = { { "SITE_SAFETY", "Site safety and access verified" },
                                    { "STRUCTURE", "Mounting structure inspected" },
                                    { "MODULES", "PV modules installed and secured" },
                                    { "INVERTER", "Inverter installed and configured" },
                                    { "WIRING", "DC/AC wiring and protection verified" },
                                    { "EARTHING", "Earthing and surge protection tested" },
                                    { "DOCUMENTS", "Customer handover documents prepared" }
            };
            for (String[] a : defaults) {
                InstallationChecklistItem ci = new InstallationChecklistItem();
                ci.setId(UUID.randomUUID());
                ci.setTenantId(t);
                ci.setInstallationId(j.getId());
                ci.setCode(a[0]);
                ci.setTitle(a[1]);
                ci.setMandatory(true);
                checklist.save(ci);
            }
        } else for (InstallationChecklistTemplate a : selectedTemplates) {
            InstallationChecklistItem ci = new InstallationChecklistItem();
            ci.setId(UUID.randomUUID());
            ci.setTenantId(t);
            ci.setInstallationId(j.getId());
            ci.setCode(a.getCode());
            ci.setTitle(a.getTitle());
            ci.setMandatory(a.isMandatory());
            checklist.save(ci);
        }
        transitionOrder(o,"INSTALLATION_SCHEDULED","Installation scheduled",p);
        notifyCustomer(o, "INSTALLATION_SCHEDULED", "Installation scheduled", "Your solar installation is scheduled for " + j.getScheduledAt() + " at " + j.getSiteAddress() + ".");
        return j;
    }
    
    private InstallationJob installation(UUID id, UUID t, Principal p) {
        InstallationJob j = installations.findByIdAndTenantId(id, t).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installation not found"));
        if ((p.permissions() == null || !p.permissions().contains("INSTALLATION_ASSIGN")) && !Objects.equals(j.getAssignedEngineerId(), p.userId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        enterpriseSaas.assertBranchAccess(j.getBranchId(), p);
        return j;
    }
    
    public List<InstallationChecklistItem> checklist(UUID id, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_VIEW");
        installation(id, t, p);
        return checklist.findByTenantIdAndInstallationIdOrderByIdAsc(t, id);
    }
    
    @Transactional
    public InstallationChecklistItem updateChecklist(UUID id, UUID itemId, ChecklistUpdateInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_UPDATE");
        InstallationJob j = installation(id, t, p);
        InstallationChecklistItem c = checklist.findByIdAndTenantIdAndInstallationId(itemId, t, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        c.setCompleted(Boolean.TRUE.equals(x.completed()));
        c.setEvidenceUrl(x.evidenceUrl());
        c.setNotes(x.notes());
        c.setCompletedBy(p.userId());
        c.setCompletedAt(c.isCompleted() ? Instant.now() : null);
        if ("SCHEDULED".equals(j.getStatus())) {
            j.setStatus("IN_PROGRESS");
            CustomerOrder o = order(j.getOrderId(), t);
            if ("INSTALLATION_SCHEDULED".equals(o.getStatus())) {
                transitionOrder(o,"INSTALLATION_IN_PROGRESS","Field execution started",p);
            }
        }
        installations.save(j);
        return checklist.save(c);
    }
    
    @Transactional
    public InstallationJob reschedule(UUID id, RescheduleInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_SCHEDULE");
        InstallationJob j = installation(id, t, p);
        if (!Set.of("SCHEDULED", "IN_PROGRESS").contains(j.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Installation cannot be rescheduled");
        if (!x.scheduledAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rescheduled date must be in the future");
        users.findForUpdate(j.getAssignedEngineerId(), t).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Assigned engineer is no longer active in this tenant"));
        teams.findForUpdate(j.getTeamId(), t).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Assigned installation team is no longer active"));
        if (installations.findByTenantIdOrderByScheduledAtAsc(t).stream().anyMatch(other -> !other.getId().equals(id) && Set.of("SCHEDULED", "IN_PROGRESS").contains(other.getStatus()) && x.scheduledAt().isBefore(other.getScheduledAt().plusSeconds(7200)) && x.scheduledAt().plusSeconds(7200).isAfter(other.getScheduledAt()) && ( Objects.equals(j.getAssignedEngineerId(), other.getAssignedEngineerId()) || Objects.equals(j.getTeamId(), other.getTeamId()) )))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Engineer or team already has an installation in the requested two-hour window");
        EngineerProfile profile = engineerProfiles.findForUpdate(t, j.getAssignedEngineerId()).orElse(null);
        if (profile != null) {
            if (profile.getAvailableFrom() != null) {
                java.time.LocalTime requested = x.scheduledAt().atZone(java.time.ZoneId.systemDefault()).toLocalTime();
                java.time.LocalTime requestedEnd = requested.plusHours(2);
                if (requested.isBefore(profile.getAvailableFrom()) || !requestedEnd.isAfter(requested) || requestedEnd.isAfter(profile.getAvailableUntil()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-hour installation slot is outside the engineer availability window");
            }
            long workload = installations.findByTenantIdOrderByScheduledAtAsc(t).stream().filter(other -> !other.getId().equals(id) && j.getAssignedEngineerId().equals(other.getAssignedEngineerId()) && Set.of("SCHEDULED", "IN_PROGRESS").contains(other.getStatus())).count();
            if (workload >= profile.getMaxConcurrentJobs())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Engineer has reached configured concurrent workload capacity");
        }
        Instant previous = j.getScheduledAt();
        j.setScheduledAt(x.scheduledAt());
        j.setRescheduleReason(x.reason());
        InstallationJob saved = installations.save(j);
        InstallationScheduleHistory h = new InstallationScheduleHistory();
        h.setId(UUID.randomUUID());
        h.setTenantId(t);
        h.setInstallationId(id);
        h.setActorId(p.userId());
        h.setEventType("RESCHEDULED");
        h.setPreviousScheduledAt(previous);
        h.setNewScheduledAt(x.scheduledAt());
        h.setReason(x.reason());
        h.setCreatedAt(Instant.now());
        scheduleHistory.save(h);
        CustomerOrder scheduledOrder = order(j.getOrderId(), t);
        notifyCustomer(scheduledOrder, "INSTALLATION_RESCHEDULED", "Installation rescheduled", "Your installation has been rescheduled to " + x.scheduledAt() + ". Reason: " + x.reason());
        return saved;
    }
    
    @Transactional
    public InstallationJob cancelInstallation(UUID id, String reason, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_UPDATE");
        InstallationJob j = installation(id, t, p);
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        if (!Set.of("SCHEDULED", "IN_PROGRESS").contains(j.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Installation cannot be cancelled in its current state");
        j.setStatus("CANCELLED");
        j.setRescheduleReason(reason);
        InstallationJob saved = installations.save(j);
        CustomerOrder order = order(j.getOrderId(), t);
        if (Set.of("INSTALLATION_SCHEDULED", "INSTALLATION_IN_PROGRESS").contains(order.getStatus())) {
            transitionOrder(order,"MATERIAL_RESERVED","Installation cancelled: " + reason,p);
        }
        InstallationScheduleHistory h = new InstallationScheduleHistory();
        h.setId(UUID.randomUUID());
        h.setTenantId(t);
        h.setInstallationId(id);
        h.setActorId(p.userId());
        h.setEventType("CANCELLED");
        h.setPreviousScheduledAt(j.getScheduledAt());
        h.setNewScheduledAt(null);
        h.setReason(reason);
        h.setCreatedAt(Instant.now());
        scheduleHistory.save(h);
        notifyCustomer(order, "INSTALLATION_CANCELLED", "Installation cancelled", "Your installation scheduled for " + j.getScheduledAt() + " has been cancelled. Reason: " + reason);
        return saved;
    }
    
    public List<InstallationScheduleHistory> scheduleHistory(UUID id, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_VIEW");
        installation(id, t, p);
        return scheduleHistory.findByTenantIdAndInstallationIdOrderByCreatedAtAsc(t, id);
    }
    
    @Transactional
    public InstallationJob completeInstallation(UUID id, String reason, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_UPDATE");
        InstallationJob j = installation(id, t, p);
        if (!"IN_PROGRESS".equals(j.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Installation must be in progress");
        if (checklist.findByTenantIdAndInstallationIdOrderByIdAsc(t, id).stream().anyMatch(c -> c.isMandatory() && !c.isCompleted()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete every mandatory checklist item before marking installation complete");
        CustomerOrder o = order(j.getOrderId(), t);
        if (!"INSTALLATION_IN_PROGRESS".equals(o.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order must be in installation progress");
        List<InventoryReservation> rows = reservations.findByTenantIdAndOrderId(t, o.getId()).stream().filter(r -> "RESERVED".equals(r.getStatus())).toList();
        for (InventoryReservation r : rows) {
            warehouse(r.getWarehouseId(), t, p);
            warehouses.findForUpdate(r.getWarehouseId(), t).orElseThrow();
            InventoryBalance b = balances.findForUpdate(t, r.getWarehouseId(), r.getProductId()).orElseThrow();
            b.setReserved(b.getReserved().subtract(r.getQuantity()));
            b.setOnHand(b.getOnHand().subtract(r.getQuantity()));
            balances.save(b);
            r.setStatus("CONSUMED");
            r.setUpdatedAt(Instant.now());
            reservations.save(r);
            movement(t, r.getWarehouseId(), r.getProductId(), o.getId(), id, "INSTALLATION_CONSUMPTION", r.getQuantity().negate(), reason, p.userId());
        }
        j.setStatus("COMPLETED");
        j.setCompletedAt(Instant.now());
        installations.save(j);
        transitionOrder(o,"INSTALLED",reason,p);
        return j;
    }
    
    @Transactional
    public InstallationJob commission(UUID id, CommissionInput x, Principal p) {
        UUID t = tenant(p);
        permission(p, "INSTALLATION_COMMISSION");
        InstallationJob j = installation(id, t, p);
        if (checklist.findByTenantIdAndInstallationIdOrderByIdAsc(t, id).stream().anyMatch(c -> c.isMandatory() && !c.isCompleted()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete all mandatory checklist items");
        if (!x.customerSignedOff())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer sign-off is required");
        CustomerOrder o = order(j.getOrderId(), t);
        if (!"COMPLETED".equals(j.getStatus()) || !"INSTALLED".equals(o.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete installation and customer handover before commissioning");
        j.setCustomerSignedOff(true);
        j.setCommissioningNotes(x.commissioningNotes());
        j.setStatus("COMMISSIONED");
        j.setCompletedAt(Instant.now());
        j.setCommissionedAt(Instant.now());
        installations.save(j);
        Map<UUID, Integer> orderedQuantities = new LinkedHashMap<>();
        for (OrderItem line : orderItems.findByTenantIdOrderByIdDesc(t).stream().filter(v -> v.getOrderId().equals(o.getId())).toList()) {
            if (line.getProductId() != null)
                orderedQuantities.merge(line.getProductId(), line.getQuantity(), Integer::sum);
            if (line.getPackageId() != null)
                for (PackageItem pi : packageItems.findByTenantIdOrderByIdDesc(t).stream().filter(v -> v.getPackageId().equals(line.getPackageId()) && !v.isOptionalItem()).toList())
                    orderedQuantities.merge(pi.getProductId(), pi.getQuantity() * line.getQuantity(), Integer::sum);
        }
        Map<UUID, List<EquipmentInput>> supplied = new HashMap<>();
        Set<String> serials = new HashSet<>();
        for (EquipmentInput e : x.equipment() == null ? List.<EquipmentInput>of() : x.equipment()) {
            if (!orderedQuantities.containsKey(e.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warranty product is not part of this order");
            if (e.serialNumber() != null && !e.serialNumber().isBlank() && !serials.add(e.serialNumber().trim()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate equipment serial number");
            supplied.computeIfAbsent(e.productId(), k -> new ArrayList<>()).add(e);
        }
        for (var entry : orderedQuantities.entrySet()) {
            Product pr = product(entry.getKey(), t);
            List<EquipmentInput> equipment = supplied.getOrDefault(pr.getId(), List.of());
            if (pr.isSerialized()) {
                if (equipment.size() != entry.getValue())
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Enter every serial number for " + pr.getName() + " before commissioning");
                for (EquipmentInput e : equipment)
                    if (e.serialNumber() == null || e.serialNumber().isBlank())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial number is required for " + pr.getName());
                for (EquipmentInput e : equipment) createWarranty(o, j, pr, 1, e.serialNumber().trim());
            } else createWarranty(o, j, pr, entry.getValue(), null);
        }
        transitionOrder(o,"COMMISSIONED","Commissioned with customer sign-off",p);
        notifyCustomer(o, "INSTALLATION_COMMISSIONED", "Solar installation commissioned", "Your solar installation has been commissioned. Product warranties are now activated.");
        return j;
    }
    
    private void createWarranty(CustomerOrder o, InstallationJob j, Product pr, int quantity, String serial) {
        Warranty w = new Warranty();
        w.setId(UUID.randomUUID());
        w.setTenantId(o.getTenantId());
        w.setOrderId(o.getId());
        w.setInstallationId(j.getId());
        w.setCustomerId(o.getCustomerId());
        w.setProductId(pr.getId());
        w.setSerialNumber(serial);
        w.setQuantity(quantity);
        w.setStartDate(LocalDate.now());
        w.setEndDate(LocalDate.now().plusYears(Math.max(1, pr.getWarrantyYears() == null ? 1 : pr.getWarrantyYears())));
        w.setTerms("Manufacturer warranty applies; retain the product invoice and serial number. Confirm the manufacturer's warranty certificate and exclusions before customer issuance.");
        w.setStatus("ACTIVE");
        w.setActivatedAt(Instant.now());
        warranties.save(w);
    }
    
    public List<Warranty> warranties(Principal p) {
        UUID t = tenant(p);
        permission(p, "SERVICE_VIEW");
        List<Warranty> tenantWarranties = warranties.findByTenantIdOrderByStartDateDesc(t);
        if (p.permissions() != null && p.permissions().contains("INSTALLATION_ASSIGN")) {
            return tenantWarranties;
        }
        Set<UUID> assignedInstallationIds = installations.findByTenantIdOrderByScheduledAtAsc(t).stream()
                .filter(job -> p.userId().equals(job.getAssignedEngineerId()))
                .map(InstallationJob::getId)
                .collect(java.util.stream.Collectors.toSet());
        return tenantWarranties.stream()
                .filter(warranty -> warranty.getInstallationId() != null
                        && assignedInstallationIds.contains(warranty.getInstallationId()))
                .toList();
    }
    
    private CustomerProfile customer(Principal p) {
        if (!"CUSTOMER".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        CustomerProfile cp = customers.findByTenantIdAndUserId(p.tenantId(), p.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        if (!cp.isActive()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account is inactive");
        return cp;
    }
    
    @Transactional
    public CustomerOrder createCustomerProductOrder(CustomerProductOrderInput x, Principal p) {
        CustomerProfile cp = customer(p);
        Product pr = products.findByIdAndTenantId(x.productId(), p.tenantId()).filter(Product::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is not available in this vendor catalogue"));
        BigDecimal discount = pr.getDiscountPercent() == null ? BigDecimal.ZERO : pr.getDiscountPercent();
        if (pr.getPrice() == null || pr.getPrice().signum() < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product sale price configuration is invalid");
        if (discount.signum() < 0 || discount.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product discount configuration is invalid");
        BigDecimal unit = pr.getPrice().multiply(BigDecimal.valueOf(100).subtract(discount)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal base = unit.multiply(BigDecimal.valueOf(x.quantity()));
        BigDecimal taxPct = pr.getTaxPercent() == null ? BigDecimal.ZERO : pr.getTaxPercent();
        if (taxPct.signum() < 0 || taxPct.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product GST configuration is invalid");
        BigDecimal tax = base.multiply(taxPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        CustomerOrder o = new CustomerOrder();
        o.setId(UUID.randomUUID());
        o.setTenantId(p.tenantId());
        o.setQuotationId(null);
        o.setCustomerId(cp.getId());
        o.setOrderNumber("ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        o.setStatus("PENDING_VENDOR_ACCEPTANCE");
        o.setTotal(base.add(tax));
        o.setCreatedAt(Instant.now());
        orders.save(o);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(p.tenantId());
        item.setOrderId(o.getId());
        item.setProductId(pr.getId());
        item.setDescription(pr.getName() + " · " + pr.getSku());
        item.setQuantity(x.quantity());
        item.setUnitPrice(unit);
        item.setTaxPercent(taxPct);
        item.setLineTotal(base);
        orderItems.save(item);
        status(o, null, p, "Customer placed a product order; vendor acceptance required");
        return o;
    }
    
    @Transactional
    public CustomerOrder decideCustomerOrder(UUID id, OrderDecisionInput x, Principal p) {
        UUID t = tenant(p);
        CustomerOrder o = order(id, t);
        if (!"PENDING_VENDOR_ACCEPTANCE".equals(o.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending customer order can be accepted or rejected");
        return transitionOrder(o,"ACCEPT".equals(x.decision())?"CONFIRMED":"REJECTED",x.reason(),p);
    }
    
    public List<CustomerOrder> customerOrders(Principal p) {
        CustomerProfile cp = customer(p);
        return orders.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(o -> cp.getId().equals(o.getCustomerId())).toList();
    }
    
    public List<InstallationJob> customerInstallations(Principal p) {
        CustomerProfile cp = customer(p);
        return installations.findByTenantIdAndCustomerIdOrderByScheduledAtAsc(p.tenantId(), cp.getId());
    }
    
    public List<Warranty> customerWarranties(Principal p) {
        CustomerProfile cp = customer(p);
        return warranties.findByTenantIdAndCustomerIdOrderByStartDateDesc(p.tenantId(), cp.getId());
    }
}
