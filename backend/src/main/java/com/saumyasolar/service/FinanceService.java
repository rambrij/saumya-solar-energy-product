        var duplicate = webhookEvents.findByTenantIdAndProviderAndEventId(pay.getTenantId(), key, event.eventId());
        if (duplicate.isPresent()) return Map.of("accepted", true, "duplicate", true);
        PaymentWebhookEvent record = new PaymentWebhookEvent();
        record.setId(UUID.randomUUID());
        record.setProvider(key);
        record.setEventId(event.eventId());
        record.setPayloadHash(HexFormat.of().formatHex(hash(raw)));
        record.setProcessingStatus("RECEIVED");
        record.setReceivedAt(Instant.now());
        webhookEvents.saveAndFlush(record);
        Payment pay = payments.findByProviderAndProviderReference(key, event.paymentReference()).orElse(null);
        if (pay == null) return Map.of("accepted", false, "matched", false, "retryable", true);
        var duplicate = webhookEvents.findByTenantIdAndProviderAndEventId(pay.getTenantId(), key, event.eventId());
        if (duplicate.isPresent()) return Map.of("accepted", true, "duplicate", true);
        if (pay == null) {
            record.setProcessingStatus("UNMATCHED");
            record.setProcessedAt(Instant.now());
            webhookEvents.save(record);
            return Map.of("accepted", true, "matched", false);
        }
        if (pay.getAmount().compareTo(event.amount()) != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook amount does not match the recorded provider payment");
        if ("SUCCEEDED".equals(event.status())) pay.setStatus("CONFIRMED");
        else if ("FAILED".equals(event.status()) && "PENDING".equals(pay.getStatus())) pay.setStatus("FAILED");
        payments.save(pay);
        accountingEvent("PAYMENT", pay.getId().toString(), "UPSERT", Map.of("invoiceId", pay.getInvoiceId().toString(), "amount", pay.getAmount(), "currency", pay.getCurrency(), "status", pay.getStatus()), new Principal(null, pay.getTenantId(), "system@saumya.internal", "SYSTEM", Set.of(), Set.of(), 0));
        Invoice i = invoice(pay.getInvoiceId(), pay.getTenantId());
        if ("CONFIRMED".equals(pay.getStatus())) {
            BigDecimal total = payments.findByTenantIdAndInvoiceId(pay.getTenantId(), i.getId()).stream().filter(v -> "CONFIRMED".equals(v.getStatus())).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            i.setStatus(total.compareTo(i.getGrandTotal()) >= 0 ? "PAID" : "PARTIALLY_PAID");
            invoices.save(i);
        }
        record.setProcessingStatus("PROCESSED");
        record.setProcessedAt(Instant.now());
        webhookEvents.save(record);
        return Map.of("accepted", true, "matched", true, "duplicate", false);
    }
    
    private byte[] hash(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    private void servicePermission(Principal p) {
        vendorTenant(p);
    }
    
    private void event(ServiceRequest t, Principal p, String type, String old, String next, String reason) {
        ServiceEvent e = new ServiceEvent();
        e.setId(UUID.randomUUID());
        e.setTenantId(t.getTenantId());
        e.setServiceRequestId(t.getId());
        e.setActorId(p.userId());
        e.setEventType(type);
        e.setOldStatus(old);
        e.setNewStatus(next);
        e.setReason(reason);
        e.setOccurredAt(Instant.now());
        serviceEvents.save(e);
    }
    
    static void assertAssignedTicketAccess(ServiceRequest t, Principal p) {
        if ((p.permissions() == null || !p.permissions().contains("SERVICE_ASSIGN"))
                && !Objects.equals(t.getAssignedEngineerId(), p.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void ensureTicketAccess(ServiceRequest t, Principal p) {
        if ("VENDOR".equals(p.accountType()) && enterpriseSaas != null)
            enterpriseSaas.assertBranchAccess(t.getBranchId(), p);
        if ("CUSTOMER".equals(p.accountType())) {
            CustomerProfile cp = customer(p);
            if (!cp.getId().equals(t.getCustomerId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } else {
            servicePermission(p);
            assertAssignedTicketAccess(t, p);
        }
    }
    
    @Transactional
    public ServiceRequest createTicket(ServiceCreate x, Principal p) {
        UUID tenant;
        CustomerProfile cp;
        if ("CUSTOMER".equals(p.accountType())) {
            cp = customer(p);
            tenant = p.tenantId();
        } else {
            servicePermission(p);
        permission(p, "SERVICE_CREATE");
            tenant = p.tenantId();
            if (x.customerId() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is required when a vendor creates a service ticket");
            cp = customers.findByIdAndTenantId(x.customerId(), tenant).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        }
        if (x.installationId() != null) {
            installations.findByIdAndTenantId(x.installationId(), tenant).filter(j -> cp.getId().equals(j.getCustomerId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installation not found"));
        }
        Product product = x.productId() == null ? null : products.findByIdAndTenantId(x.productId(), tenant).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (product != null && "CUSTOMER".equals(p.accountType())) {
            Set<UUID> ownedProducts = new HashSet<>();
            for (CustomerOrder customerOrder : orders.findByTenantIdOrderByIdDesc(tenant).stream().filter(o -> cp.getId().equals(o.getCustomerId())).toList())
                for (OrderItem item : orderItems.findByTenantIdOrderByIdDesc(tenant).stream().filter(v -> customerOrder.getId().equals(v.getOrderId())).toList())
                    if (item.getProductId() != null) ownedProducts.add(item.getProductId());
            boolean hasWarranty = warranties.findByTenantIdAndCustomerIdOrderByStartDateDesc(tenant, cp.getId()).stream().anyMatch(w -> product.getId().equals(w.getProductId()) && ( x.serialNumber() == null || Objects.equals(w.getSerialNumber(), x.serialNumber()) ));
            if (!ownedProducts.contains(product.getId()) && !hasWarranty)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is not associated with this customer's order or warranty");
        }
        String warrantyStatus = "UNKNOWN";
        if (product != null) {
            Warranty active = warranties.findByTenantIdAndCustomerIdOrderByStartDateDesc(tenant, cp.getId()).stream().filter(w -> product.getId().equals(w.getProductId()) && ( !product.isSerialized() || ( x.serialNumber() != null && Objects.equals(w.getSerialNumber(), x.serialNumber()) ) ) && "ACTIVE".equals(w.getStatus()) && !LocalDate.now().isBefore(w.getStartDate()) && !LocalDate.now().isAfter(w.getEndDate())).findFirst().orElse(null);
            warrantyStatus = active == null ? "OUT_OF_WARRANTY" : "IN_WARRANTY";
        }
        ServiceSlaPolicy policy = slaPolicies.findByTenantIdAndPriority(tenant, x.priority()).filter(ServiceSlaPolicy::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Configure an active SLA policy for priority " + x.priority() + " before accepting tickets"));
        Instant now = Instant.now();
        ServiceRequest t = new ServiceRequest();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenant);
        t.setTicketNumber("SRV-" + LocalDate.now().getYear() + "-" + t.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        t.setCustomerId(cp.getId());
        t.setInstallationId(x.installationId());
        t.setProductId(x.productId());
        t.setSerialNumber(x.serialNumber());
        t.setCategory(x.category().trim());
        t.setPriority(x.priority());
        t.setDescription(x.description().trim());
        t.setWarrantyStatus(warrantyStatus);
        t.setStatus("OPEN");
        t.setResponseDueAt(calculateSlaDue(now, policy.getResponseMinutes(), policy));
        t.setResolutionDueAt(calculateSlaDue(now, policy.getResolutionMinutes(), policy));
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        tickets.save(t);
        event(t, p, "CREATED", null, "OPEN", "Service request created");
        notifications.sendBusinessNotification(cp.getUserId(), tenant, "SERVICE_TICKET_CREATED", cp.getEmail(), "Service request " + t.getTicketNumber() + " created", "Your service request " + t.getTicketNumber() + " has been created with priority " + t.getPriority() + ".");
        return t;
    }
    
    public ServiceRequest getTicket(UUID id, Principal p) {
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (!"CUSTOMER".equals(p.accountType())) permission(p, "SERVICE_VIEW");
        return t;
    }
    
    public List<ServiceRequest> listTickets(Principal p) {
        if ("CUSTOMER".equals(p.accountType())) {
            CustomerProfile cp = customer(p);
            return tickets.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream().filter(t -> cp.getId().equals(t.getCustomerId())).toList();
        }
        servicePermission(p);
        permission(p, "SERVICE_VIEW");
        List<ServiceRequest> all = tickets.findByTenantIdOrderByCreatedAtDesc(p.tenantId());
        List<ServiceRequest> visible = p.permissions() != null && p.permissions().contains("SERVICE_ASSIGN")
                ? all : all.stream().filter(t -> p.userId().equals(t.getAssignedEngineerId())).toList();
        return enterpriseSaas == null ? visible : enterpriseSaas.filterBranchRecords(visible, ServiceRequest::getBranchId, p);
    }
    
    @Transactional
    public ServiceRequest assignTicket(UUID id, ServiceAssign x, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_ASSIGN");
        UUID tenant = p.tenantId();
        ServiceRequest t = ticket(id, tenant);
        AppUser engineer = users.findByIdAndTenantId(x.engineerId(), tenant).filter(u -> u.isEnabled() && u.getRoles().stream().anyMatch(r -> "SERVICE_ENGINEER".equals(r.getName()) || "TECHNICIAN".equals(r.getName()) || "INSTALLER".equals(r.getName()))).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Engineer must be an enabled same-tenant service engineer"));
        String old = t.getStatus();
        if (!Set.of("OPEN", "ASSIGNED", "VISIT_SCHEDULED").contains(old))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket cannot be assigned in its current state");
        t.setAssignedEngineerId(engineer.getId());
        t.setStatus("ASSIGNED");
        if (t.getFirstRespondedAt() == null) t.setFirstRespondedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "ASSIGNED", old, "ASSIGNED", x.reason());
        CustomerProfile customer = customers.findByIdAndTenantId(t.getCustomerId(), tenant).orElse(null);
        if (customer != null)
            notifications.sendBusinessNotification(customer.getUserId(), tenant, "SERVICE_TICKET_ASSIGNED", customer.getEmail(), "Engineer assigned to " + t.getTicketNumber(), "An engineer has been assigned to your service request " + t.getTicketNumber() + ".");
        return t;
    }
    
    @Transactional
    public ServiceVisit scheduleVisit(UUID id, ServiceVisitCreate x, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_ASSIGN");
                ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (!x.scheduledAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Visit must be scheduled in the future");
        if (t.getAssignedEngineerId() == null || !t.getAssignedEngineerId().equals(x.engineerId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Visit engineer must be the currently assigned engineer");
        if (!Set.of("ASSIGNED", "VISIT_SCHEDULED", "IN_PROGRESS").contains(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is not ready for a service visit");
        ServiceVisit v = new ServiceVisit();
        v.setId(UUID.randomUUID());
        v.setTenantId(p.tenantId());
        v.setServiceRequestId(id);
        v.setEngineerId(x.engineerId());
        v.setScheduledAt(x.scheduledAt());
        visits.save(v);
        String old = t.getStatus();
        t.setStatus("VISIT_SCHEDULED");
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "VISIT_SCHEDULED", old, t.getStatus(), x.reason());
        return v;
    }
    
    @Transactional
    public ServiceVisit startVisit(UUID id, UUID visitId, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_UPDATE");
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        ServiceVisit v = visits.findByIdAndTenantIdAndServiceRequestId(visitId, p.tenantId(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        if (!v.getEngineerId().equals(p.userId()) && !(p.permissions() != null && p.permissions().contains("SERVICE_ASSIGN")) )
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (v.getStartedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Visit has already started");
        v.setStartedAt(Instant.now());
        visits.save(v);
        if (t.getFirstRespondedAt() == null) t.setFirstRespondedAt(Instant.now());
        String old = t.getStatus();
        t.setStatus("IN_PROGRESS");
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "VISIT_STARTED", old, "IN_PROGRESS", "Engineer started service visit");
        return v;
    }
    
    @Transactional
    public ServiceVisit completeVisit(UUID id, UUID visitId, ServiceVisitComplete x, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_RESOLVE");
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        ServiceVisit v = visits.findByIdAndTenantIdAndServiceRequestId(visitId, p.tenantId(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        if (!v.getEngineerId().equals(p.userId()) && !(p.permissions() != null && p.permissions().contains("SERVICE_ASSIGN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (v.getCompletedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Visit is already completed");
        if (v.getStartedAt() == null || !"IN_PROGRESS".equals(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Start the assigned service visit before completing it");
        for (ServicePart part : x.parts() == null ? List.<ServicePart>of() : x.parts()) {
            warehouses.findForUpdate(part.warehouseId(), p.tenantId()).filter(Warehouse::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));
            Product prod = products.findByIdAndTenantId(part.productId(), p.tenantId()).filter(Product::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part product not found"));
            InventoryBalance balance = balances.findForUpdate(p.tenantId(), part.warehouseId(), part.productId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No inventory balance for service part " + prod.getSku()));
            if (balance.getAvailable().compareTo(part.quantity()) < 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock for service part " + prod.getSku());
            balance.setOnHand(balance.getOnHand().subtract(part.quantity()));
            balances.save(balance);
            InventoryTransaction movement = new InventoryTransaction();
            movement.setId(UUID.randomUUID());
            movement.setTenantId(p.tenantId());
            movement.setWarehouseId(part.warehouseId());
            movement.setProductId(part.productId());
            movement.setServiceRequestId(id);
            movement.setType("SERVICE_CONSUMPTION");
            movement.setQuantityDelta(part.quantity().negate());
            movement.setReservedDelta(BigDecimal.ZERO);
            movement.setDamagedDelta(BigDecimal.ZERO);
            movement.setReason("Service ticket " + t.getTicketNumber() + ": " + part.reason());
            movement.setActorId(p.userId());
            movement.setCreatedAt(Instant.now());
            inventoryTransactions.save(movement);
        }
        v.setEngineerNotes(x.engineerNotes());
        v.setResolution(x.resolution());
        v.setEvidenceUrl(x.evidenceUrl());
        try {
            v.setPartsConsumedJson(mapper.writeValueAsString(x.parts() == null ? List.of() : x.parts()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        v.setCompletedAt(Instant.now());
        if (v.getStartedAt() == null) v.setStartedAt(v.getCompletedAt());
        visits.save(v);
        String old = t.getStatus();
        t.setStatus("RESOLVED");
        t.setResolutionNotes(x.resolution());
        t.setResolvedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "RESOLVED", old, "RESOLVED", x.resolution());
        CustomerProfile customer = customers.findByIdAndTenantId(t.getCustomerId(), p.tenantId()).orElse(null);
        if (customer != null)
            notifications.sendBusinessNotification(customer.getUserId(), p.tenantId(), "SERVICE_TICKET_RESOLVED", customer.getEmail(), "Service request " + t.getTicketNumber() + " resolved", x.resolution());
        return v;
    }
    
    @Transactional
    public ServiceRequest confirmService(UUID id, Principal p) {
        CustomerProfile cp = customer(p);
        ServiceRequest t = ticket(id, p.tenantId());
        if (!cp.getId().equals(t.getCustomerId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!"RESOLVED".equals(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a resolved ticket can be confirmed");
        String old = t.getStatus();
        t.setStatus("CUSTOMER_CONFIRMED");
        t.setCustomerConfirmedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "CUSTOMER_CONFIRMED", old, t.getStatus(), "Customer confirmed service resolution");
        return t;
    }
    
    @Transactional
    public ServiceRequest closeTicket(UUID id, String reason, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_CLOSE");
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closure reason is required");
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (!"CUSTOMER_CONFIRMED".equals(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer confirmation is required before closure");
        String old = t.getStatus();
        t.setStatus("CLOSED");
        t.setClosedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "CLOSED", old, "CLOSED", reason);
        return t;
    }
    
    @Transactional
    public ServiceRequest reopenTicket(UUID id, String reason, Principal p) {
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reopen reason is required");
        CustomerProfile cp = customer(p);
        ServiceRequest t = ticket(id, p.tenantId());
        if (!cp.getId().equals(t.getCustomerId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Tenant tenant = tenants.findById(p.tenantId()).orElseThrow();
        if (tenant.getServiceReopenDays() == null || tenant.getServiceReopenDays() < 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket reopening is disabled by vendor policy");
        if (!"CLOSED".equals(t.getStatus()) || t.getClosedAt() == null || Instant.now().isAfter(t.getClosedAt().plus(Duration.ofDays(tenant.getServiceReopenDays()))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is outside the configured reopen window");
        ServiceSlaPolicy policy = slaPolicies.findByTenantIdAndPriority(p.tenantId(), t.getPriority()).filter(ServiceSlaPolicy::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Active SLA policy is required to reopen this ticket"));
        String old = t.getStatus();
        Instant now = Instant.now();
        t.setStatus("OPEN");
        t.setAssignedEngineerId(null);
        t.setClosedAt(null);
        t.setCustomerConfirmedAt(null);
        t.setResolvedAt(null);
        t.setFirstRespondedAt(null);
        t.setResponseBreachedAt(null);
        t.setResolutionBreachedAt(null);
        t.setResponseDueAt(calculateSlaDue(now, policy.getResponseMinutes(), policy));
        t.setResolutionDueAt(calculateSlaDue(now, policy.getResolutionMinutes(), policy));
        t.setUpdatedAt(now);
        tickets.save(t);
        event(t, p, "REOPENED", old, "OPEN", reason);
        return t;
    }
    
    static List<ServiceVisit> visibleServiceVisits(List<ServiceVisit> rows, Principal p) {
        if ("CUSTOMER".equals(p.accountType())
                || p.permissions() != null && p.permissions().contains("SERVICE_ASSIGN")) {
            return rows;
        }
        return rows.stream().filter(visit -> Objects.equals(visit.getEngineerId(), p.userId())).toList();
    }

    public List<ServiceVisit> serviceVisits(UUID id, Principal p) {
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (!"CUSTOMER".equals(p.accountType())) permission(p, "SERVICE_VIEW");
        List<ServiceVisit> rows = visits.findByTenantIdAndServiceRequestIdOrderByScheduledAtAsc(p.tenantId(), id);
        return visibleServiceVisits(rows, p);
    }
    
    public List<ServiceEvent> serviceHistory(UUID id, Principal p) {
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (!"CUSTOMER".equals(p.accountType())) permission(p, "SERVICE_VIEW");
        return serviceEvents.findByTenantIdAndServiceRequestIdOrderByOccurredAtAsc(p.tenantId(), id);
    }
    
    public List<CustomerProfile> serviceCustomers(Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_VIEW");
        List<CustomerProfile> active = customers.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(CustomerProfile::isActive).toList();
        if (p.permissions() == null || !p.permissions().contains("SERVICE_ASSIGN")) {
            Set<UUID> assignedCustomers = tickets.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream()
                    .filter(ticket -> p.userId().equals(ticket.getAssignedEngineerId()))
                    .map(ServiceRequest::getCustomerId).collect(java.util.stream.Collectors.toSet());
            return active.stream().filter(customer -> assignedCustomers.contains(customer.getId())).toList();
        }
        return active;
    }
    
    public List<AppUser> serviceEngineers(Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_ASSIGN");
        return users.findByTenantId(p.tenantId()).stream().filter(AppUser::isEnabled).filter(u -> u.getRoles().stream().anyMatch(r -> Set.of("SERVICE_ENGINEER", "TECHNICIAN", "INSTALLER").contains(r.getName()))).toList();
    }
    
    private String defaultWorkingDays() {
        try {
            return mapper.writeValueAsString(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    private void validateCalendar(ServiceSlaPolicy policy) {
        try {
            ZoneId.of(policy.getTimeZone());
            var holidays = mapper.readTree(policy.getHolidaysJson() == null ? "[]" : policy.getHolidaysJson());
            if (!holidays.isArray()) throw new IllegalArgumentException();
            for (var h : holidays) LocalDate.parse(h.asText());
            var days = mapper.readTree(policy.getWorkingDaysJson() == null ? "[]" : policy.getWorkingDaysJson());
            if (!days.isArray() || days.isEmpty()) throw new IllegalArgumentException();
            for (var d : days) DayOfWeek.valueOf(d.asText().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SLA timezone, holiday dates or working-days JSON");
        }
    }
    
    private Instant calculateSlaDue(Instant start, int minutes, ServiceSlaPolicy policy) {
        if (policy.getBusinessStart() == null || policy.getBusinessEnd() == null)
            return start.plusSeconds(minutes * 60L);
        validateCalendar(policy);
        ZoneId zone = ZoneId.of(policy.getTimeZone());
        Set<LocalDate> holidays = new HashSet<>();
        try {
            for (var h : mapper.readTree(policy.getHolidaysJson())) holidays.add(LocalDate.parse(h.asText()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SLA holiday calendar");
        }
        Set<DayOfWeek> days = new HashSet<>();
        try {
            for (var d : mapper.readTree(policy.getWorkingDaysJson()))
                days.add(DayOfWeek.valueOf(d.asText().toUpperCase(Locale.ROOT)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SLA working days");
        }
        ZonedDateTime cursor = start.atZone(zone);
        long remaining = minutes;
        for (int guard = 0; guard < 3660 && remaining > 0; guard++) {
            LocalDate day = cursor.toLocalDate();
            if (days.contains(day.getDayOfWeek()) && !holidays.contains(day)) {
                ZonedDateTime opens = day.atTime(policy.getBusinessStart()).atZone(zone), closes = day.atTime(policy.getBusinessEnd()).atZone(zone);
                if (cursor.isBefore(opens)) cursor = opens;
                if (cursor.isBefore(closes)) {
                    long available = Duration.between(cursor, closes).toMinutes();
                    if (available >= remaining) return cursor.plusMinutes(remaining).toInstant();
                    remaining -= available;
                }
            }
            cursor = day.plusDays(1).atTime(policy.getBusinessStart()).atZone(zone);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configured SLA calendar cannot resolve the target within ten years");
    }
    
    @Transactional
    public Tenant saveServiceReopenPolicy(int days, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_UPDATE");
        if (p.roles() == null || !p.roles().contains("VENDOR_ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (days < 0 || days > 90)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reopen window must be between 0 and 90 days");
        Tenant t = tenants.findById(p.tenantId()).orElseThrow();
        t.setServiceReopenDays(days);
        t.setUpdatedAt(Instant.now());
        return tenants.save(t);
    }
    
    public List<ServiceSlaPolicy> slaPolicies(Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_VIEW");
        return slaPolicies.findByTenantIdAndActiveTrue(p.tenantId());
    }
    
    @Transactional
    public ServiceSlaPolicy saveSlaPolicy(SlaPolicyInput x, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_UPDATE");
        if (x.resolutionMinutes() < x.responseMinutes())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolution SLA must be at least the response SLA");
        if (( x.businessStart() == null ) != ( x.businessEnd() == null ) || x.businessStart() != null && !x.businessEnd().isAfter(x.businessStart()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business hours must include a valid start and end time");
        ServiceSlaPolicy v = slaPolicies.findByTenantIdAndPriority(p.tenantId(), x.priority()).orElseGet(() -> {
            ServiceSlaPolicy n = new ServiceSlaPolicy();
            n.setId(UUID.randomUUID());
            n.setTenantId(p.tenantId());
            n.setPriority(x.priority());
            n.setCreatedAt(Instant.now());
            return n;
        });
        v.setResponseMinutes(x.responseMinutes());
        v.setResolutionMinutes(x.resolutionMinutes());
        v.setTimeZone(x.timeZone());
        v.setBusinessStart(x.businessStart());
        v.setBusinessEnd(x.businessEnd());
        v.setHolidaysJson(x.holidaysJson() == null ? "[]" : x.holidaysJson());
        v.setWorkingDaysJson(x.workingDaysJson() == null || x.workingDaysJson().isBlank() ? defaultWorkingDays() : x.workingDaysJson());
        validateCalendar(v);
        v.setActive(true);
        v.setUpdatedAt(Instant.now());
        return slaPolicies.save(v);
    }
    
    public List<Refund> listRefunds(Principal p) {
        financeAccess(p);
        permission(p, "PAYMENT_REFUND");
        List<Refund> rows = refunds.findByTenantIdOrderByCreatedAtDesc(p.tenantId());
        if (enterpriseSaas == null || p.roles() != null && p.roles().contains("VENDOR_ADMIN")) return rows;
        Set<UUID> visibleInvoiceIds = enterpriseSaas.filterBranchRecords(
                invoices.findByTenantIdOrderByCreatedAtDesc(p.tenantId()), Invoice::getBranchId, p)
                .stream().map(Invoice::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> visiblePaymentIds = payments.findByTenantIdOrderByCreatedAtDesc(p.tenantId()).stream()
                .filter(payment -> visibleInvoiceIds.contains(payment.getInvoiceId()))
                .map(Payment::getId).collect(java.util.stream.Collectors.toSet());
        return rows.stream().filter(refund -> visiblePaymentIds.contains(refund.getPaymentId())).toList();
    }
    
    @Transactional
    public ServiceRequest pauseSla(UUID id, String reason, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_UPDATE");
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SLA pause reason is required");
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (t.getSlaPausedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "SLA is already paused");
        if (Set.of("CLOSED", "CANCELLED").contains(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed tickets cannot be paused");
        t.setSlaPausedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "SLA_PAUSED", t.getStatus(), t.getStatus(), reason);
        return t;
    }
    
    @Transactional
    public ServiceRequest resumeSla(UUID id, String reason, Principal p) {
        servicePermission(p);
        permission(p, "SERVICE_UPDATE");
        ServiceRequest t = ticket(id, p.tenantId());
        ensureTicketAccess(t, p);
        if (t.getSlaPausedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "SLA is not paused");
        long seconds = Duration.between(t.getSlaPausedAt(), Instant.now()).getSeconds();
        t.setPausedSeconds(t.getPausedSeconds() + seconds);
        if (t.getResponseDueAt() != null) t.setResponseDueAt(t.getResponseDueAt().plusSeconds(seconds));
        if (t.getResolutionDueAt() != null) t.setResolutionDueAt(t.getResolutionDueAt().plusSeconds(seconds));
        t.setSlaPausedAt(null);
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        event(t, p, "SLA_RESUMED", t.getStatus(), t.getStatus(), reason == null ? "SLA resumed" : reason);
        return t;
    }
    
    @Transactional
    public void evaluateSlaBreaches(UUID tenantId) {
        if (tenantId == null || !tenantId.equals(com.saumyasolar.security.TenantExecutionContext.requireTenantId()))
            throw new IllegalArgumentException("Matching tenant execution context is required for SLA evaluation");
        Instant now = Instant.now();
        for (ServiceRequest t : tickets.findForSlaEvaluationByTenantId(tenantId)) {
            if (t.getSlaPausedAt() != null || Set.of("CLOSED", "CANCELLED", "CUSTOMER_CONFIRMED").contains(t.getStatus()))
                continue;
            boolean changed = false;
            if (t.getFirstRespondedAt() == null && t.getResponseDueAt() != null && now.isAfter(t.getResponseDueAt()) && t.getResponseBreachedAt() == null) {
                t.setResponseBreachedAt(now);
                ServiceEvent e = new ServiceEvent();
                e.setId(UUID.randomUUID());
                e.setTenantId(t.getTenantId());
                e.setServiceRequestId(t.getId());
                e.setActorId(t.getAssignedEngineerId());
                e.setEventType("SLA_RESPONSE_BREACHED");
                e.setOldStatus(t.getStatus());
                e.setNewStatus(t.getStatus());
                e.setReason("Response SLA target exceeded");
                e.setOccurredAt(now);
                serviceEvents.save(e);
                changed = true;
            }
            if (t.getResolvedAt() == null && t.getResolutionDueAt() != null && now.isAfter(t.getResolutionDueAt()) && t.getResolutionBreachedAt() == null) {
                t.setResolutionBreachedAt(now);
                ServiceEvent e = new ServiceEvent();
                e.setId(UUID.randomUUID());
                e.setTenantId(t.getTenantId());
                e.setServiceRequestId(t.getId());
                e.setActorId(t.getAssignedEngineerId());
                e.setEventType("SLA_RESOLUTION_BREACHED");
                e.setOldStatus(t.getStatus());
                e.setNewStatus(t.getStatus());
                e.setReason("Resolution SLA target exceeded");
                e.setOccurredAt(now);
                serviceEvents.save(e);
                changed = true;
            }
            if (changed) tickets.save(t);
        }
    }
    
    public boolean responseBreached(ServiceRequest t) {
        return t.getResponseBreachedAt() != null || ( t.getSlaPausedAt() == null && t.getFirstRespondedAt() == null && t.getResponseDueAt() != null && Instant.now().isAfter(t.getResponseDueAt()) );
    }
    
    public boolean resolutionBreached(ServiceRequest t) {
        return t.getResolutionBreachedAt() != null || ( t.getSlaPausedAt() == null && t.getResolvedAt() == null && t.getResolutionDueAt() != null && Instant.now().isAfter(t.getResolutionDueAt()) );
    }
}
