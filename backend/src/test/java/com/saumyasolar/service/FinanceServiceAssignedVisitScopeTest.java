package com.saumyasolar.service;

import com.saumyasolar.domain.ServiceVisit;
import com.saumyasolar.domain.ServiceRequest;
import com.saumyasolar.security.Principal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinanceServiceAssignedVisitScopeTest {
    @Test
    void assignedEngineerSeesOnlyTheirOwnVisits() {
        UUID engineerA = UUID.randomUUID();
        UUID engineerB = UUID.randomUUID();
        List<ServiceVisit> rows = List.of(visit(engineerA), visit(engineerB));

        List<ServiceVisit> visible = FinanceService.visibleServiceVisits(rows,
                principal(engineerA, Set.of("SERVICE_VIEW")));

        assertEquals(1, visible.size());
        assertEquals(engineerA, visible.get(0).getEngineerId());
    }

    @Test
    void dispatcherWithServiceAssignCanSeeAllVisitsForVisibleTicket() {
        List<ServiceVisit> rows = List.of(visit(UUID.randomUUID()), visit(UUID.randomUUID()));

        List<ServiceVisit> visible = FinanceService.visibleServiceVisits(rows,
                principal(UUID.randomUUID(), Set.of("SERVICE_VIEW", "SERVICE_ASSIGN")));

        assertEquals(2, visible.size());
    }

    @Test
    void owningCustomerCanSeeAllVisitsForTheirTicket() {
        List<ServiceVisit> rows = List.of(visit(UUID.randomUUID()), visit(UUID.randomUUID()));

        List<ServiceVisit> visible = FinanceService.visibleServiceVisits(rows,
                new Principal(UUID.randomUUID(), UUID.randomUUID(), "customer@example.test",
                        "CUSTOMER", Set.of("CUSTOMER"), Set.of(), 0));

        assertEquals(2, visible.size());
    }

    @Test
    void unassignedEngineerCannotMutateAnotherEngineersTicket() {
        UUID assignedEngineer = UUID.randomUUID();
        ServiceRequest ticket = ticket(assignedEngineer);
        Principal otherEngineer = principal(UUID.randomUUID(), Set.of("SERVICE_UPDATE", "SERVICE_CLOSE"));

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> FinanceService.assertAssignedTicketAccess(ticket, otherEngineer));
    }

    @Test
    void assignedEngineerCanMutateTheirOwnTicket() {
        UUID assignedEngineer = UUID.randomUUID();
        ServiceRequest ticket = ticket(assignedEngineer);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> FinanceService.assertAssignedTicketAccess(ticket,
                        principal(assignedEngineer, Set.of("SERVICE_UPDATE"))));
    }

    @Test
    void serviceDispatcherCanMutateAnyTicketWithinTheirTenant() {
        ServiceRequest ticket = ticket(UUID.randomUUID());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> FinanceService.assertAssignedTicketAccess(ticket,
                        principal(UUID.randomUUID(), Set.of("SERVICE_UPDATE", "SERVICE_ASSIGN"))));
    }

    private static ServiceRequest ticket(UUID assignedEngineerId) {
        ServiceRequest ticket = new ServiceRequest();
        ticket.setAssignedEngineerId(assignedEngineerId);
        return ticket;
    }

    private static ServiceVisit visit(UUID engineerId) {
        ServiceVisit visit = new ServiceVisit();
        visit.setEngineerId(engineerId);
        return visit;
    }

    private static Principal principal(UUID userId, Set<String> permissions) {
        return new Principal(userId, UUID.randomUUID(), "engineer@example.test",
                "VENDOR", Set.of("SERVICE_ENGINEER"), permissions, 0);
    }
}
