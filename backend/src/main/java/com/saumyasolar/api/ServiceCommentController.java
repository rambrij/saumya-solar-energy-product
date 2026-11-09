package com.saumyasolar.api;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.NotificationService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
@RestController
@RequestMapping("/api/v1/service-requests/{ticketId}/comments")
public class ServiceCommentController {
    private final ServiceRequestRepository tickets;
    private final ServiceCommentRepository comments;
    private final CustomerProfileRepository customers;
    private final NotificationService notifications;
    
    public ServiceCommentController(
            ServiceRequestRepository tickets,
            ServiceCommentRepository comments,
            CustomerProfileRepository customers,
            NotificationService notifications
    ) {
        this.tickets = tickets;
        this.comments = comments;
        this.customers = customers;
        this.notifications = notifications;
    }
    
    public record CommentInput(
            @NotBlank @Size(max = 4000) String body, @Pattern(regexp = "CUSTOMER|INTERNAL") String visibility
    ) {
    }
    
    private Principal p(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    private ServiceRequest access(UUID id, Principal p) {
        if (p.tenantId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        ServiceRequest t = tickets.findByIdAndTenantId(id, p.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service ticket not found"));
        if ("CUSTOMER".equals(p.accountType())) {
            CustomerProfile c = customers.findByTenantIdAndUserId(p.tenantId(), p.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!c.getId().equals(t.getCustomerId()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service ticket not found");
        } else {
            if (!"VENDOR".equals(p.accountType()) || p.permissions() == null || !p.permissions().contains("SERVICE_MANAGE"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            boolean manager = p.roles() != null && p.roles().stream().anyMatch(r -> Set.of("VENDOR_ADMIN", "SERVICE_MANAGER").contains(r));
            if (!manager && !p.userId().equals(t.getAssignedEngineerId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return t;
    }
    
    @GetMapping
    public List<ServiceComment> list(@PathVariable UUID ticketId, Authentication a) {
        Principal p = p(a);
        access(ticketId, p);
        List<ServiceComment> rows = comments.findByTenantIdAndServiceRequestIdOrderByCreatedAtAsc(p.tenantId(), ticketId);
        return "CUSTOMER".equals(p.accountType()) ? rows.stream().filter(x -> "CUSTOMER".equals(x.getVisibility())).toList() : rows;
    }
    
    @PostMapping
    @Transactional
    public ServiceComment add(@PathVariable UUID ticketId, @Valid @RequestBody CommentInput x, Authentication a) {
        Principal p = p(a);
        ServiceRequest t = access(ticketId, p);
        String visibility = "CUSTOMER".equals(p.accountType()) ? "CUSTOMER" : ( x.visibility() == null ? "CUSTOMER" : x.visibility() );
        if ("INTERNAL".equals(visibility) && "CUSTOMER".equals(p.accountType()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        ServiceComment c = new ServiceComment();
        c.setId(UUID.randomUUID());
        c.setTenantId(p.tenantId());
        c.setServiceRequestId(ticketId);
        c.setAuthorId(p.userId());
        c.setVisibility(visibility);
        c.setBody(x.body().trim());
        c.setCreatedAt(Instant.now());
        ServiceComment saved = comments.save(c);
        if ("CUSTOMER".equals(visibility) && !"CUSTOMER".equals(p.accountType()))
            customers.findByIdAndTenantId(t.getCustomerId(), p.tenantId()).ifPresent(customer -> notifications.sendBusinessNotification(customer.getUserId(), p.tenantId(), "SERVICE_TICKET_COMMENT", customer.getEmail(), "Update on service request " + t.getTicketNumber(), x.body().trim()));
        return saved;
    }
}
