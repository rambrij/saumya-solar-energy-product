package com.saumyasolar;

import com.saumyasolar.api.Phase2OperationsController;
import com.saumyasolar.domain.Payment;
import com.saumyasolar.domain.Refund;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase2OperationsControllerTest {
    @Mock
    EngineerProfileRepository engineers;
    @Mock
    WarrantyClaimRepository claims;
    @Mock
    WarrantyRepository warranties;
    @Mock
    CustomerProfileRepository customers;
    @Mock
    AppUserRepository users;
    @Mock
    InstallationJobRepository installations;
    @Mock
    CustomerOrderRepository orders;
    @Mock
    ServiceRequestRepository tickets;
    @Mock
    InventoryTransactionRepository inventory;
    @Mock
    InvoiceRepository invoices;
    @Mock
    PaymentRepository payments;
    @Mock
    SalesLeadRepository leads;
    @Mock
    RefundRepository refunds;
    
    Phase2OperationsController controller;
    
    @BeforeEach
    void setUp() {
        controller = new Phase2OperationsController(
                engineers, claims, warranties, customers, users,
                installations, orders, tickets, inventory, invoices, payments, leads, refunds
        );
    }
    
    @Test
    void operationsReportIncludesConfirmedPaymentsAndSubtractsRecordedRefunds() {
        UUID tenantId = UUID.randomUUID();
        UUID confirmedId = UUID.randomUUID();
        UUID partialId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Payment confirmed = payment(confirmedId, tenantId, "100.00", "CONFIRMED", now);
        Payment partiallyRefunded = payment(partialId, tenantId, "50.00", "PARTIALLY_REFUNDED", now);
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setTenantId(tenantId);
        refund.setPaymentId(partialId);
        refund.setAmount(new BigDecimal("20.00"));
        refund.setStatus("RECORDED");
        
        when(orders.findByTenantIdOrderByIdDesc(tenantId)).thenReturn(List.of());
        when(installations.findByTenantIdOrderByScheduledAtAsc(tenantId)).thenReturn(List.of());
        when(tickets.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(invoices.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(payments.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(confirmed, partiallyRefunded));
        when(leads.findByTenantIdOrderByIdDesc(tenantId)).thenReturn(List.of());
        when(warranties.findByTenantIdOrderByStartDateDesc(tenantId)).thenReturn(List.of());
        when(inventory.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(refunds.findByTenantIdAndPaymentId(tenantId, confirmedId)).thenReturn(List.of());
        when(refunds.findByTenantIdAndPaymentId(tenantId, partialId)).thenReturn(List.of(refund));
        
        Authentication auth = auth(tenantId, "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("INSTALLATION_ASSIGN"));
        Map<String, Object> result = controller.operationsReport(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), auth);
        
        assertEquals(new BigDecimal("130.00"), result.get("paymentsReceived"));
    }
    
    @Test
    void customerCannotReadVendorOperationsReport() {
        Authentication auth = auth(UUID.randomUUID(), "CUSTOMER", Set.of("CUSTOMER"), Set.of("INSTALLATION_ASSIGN"));
        assertThrows(
                ResponseStatusException.class,
                () -> controller.operationsReport(LocalDate.now().minusDays(1), LocalDate.now(), auth)
        );
    }
    
    @Test
    void customRoleWithInstallationAssignPermissionCanViewEngineerDirectory() {
        UUID tenantId = UUID.randomUUID();
        UUID engineerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        com.saumyasolar.domain.AppUser engineer = new com.saumyasolar.domain.AppUser();
        engineer.setId(engineerId);
        engineer.setTenantId(tenantId);
        engineer.setName("Assigned engineer");
        engineer.setEmail("engineer@example.test");
        engineer.setEnabled(true);

        com.saumyasolar.domain.EngineerProfile profile = new com.saumyasolar.domain.EngineerProfile();
        profile.setId(profileId);
        profile.setTenantId(tenantId);
        profile.setUserId(engineerId);
        profile.setActive(true);
        profile.setMaxConcurrentJobs(3);

        when(engineers.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(profile));
        when(users.findByIdAndTenantId(engineerId, tenantId)).thenReturn(java.util.Optional.of(engineer));
        when(installations.findByTenantIdOrderByScheduledAtAsc(tenantId)).thenReturn(List.of());

        Authentication customOperationsRole = auth(
                tenantId, "VENDOR", Set.of("CUSTOM_OPERATIONS_ROLE"), Set.of("INSTALLATION_ASSIGN")
        );
        List<Map<String, Object>> result = controller.engineers(customOperationsRole);

        assertEquals(1, result.size());
        assertEquals(engineerId, result.get(0).get("userId"));
    }

    private Payment payment(UUID id, UUID tenantId, String amount, String status, Instant createdAt) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setTenantId(tenantId);
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(status);
        payment.setCreatedAt(createdAt);
        return payment;
    }
    
    private Authentication auth(UUID tenantId, String accountType, Set<String> roles, Set<String> permissions) {
        Principal principal = new Principal(
                UUID.randomUUID(), tenantId, "test@example.test",
                accountType, roles, permissions, 0
        );
        return new UsernamePasswordAuthenticationToken(principal, null);
    }
}
