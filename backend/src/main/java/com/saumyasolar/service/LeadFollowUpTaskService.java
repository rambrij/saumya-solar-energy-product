package com.saumyasolar.service;

import com.saumyasolar.api.LeadFollowUpModels.CreateFollowUp;
import com.saumyasolar.domain.AppUser;
import com.saumyasolar.domain.LeadFollowUpTask;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.LeadFollowUpTaskRepository;
import com.saumyasolar.repo.SalesLeadRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LeadFollowUpTaskService {
    private final LeadFollowUpTaskRepository tasks;
    private final SalesLeadRepository leads;
    private final AppUserRepository users;
    public LeadFollowUpTaskService(LeadFollowUpTaskRepository tasks, SalesLeadRepository leads, AppUserRepository users) {
        this.tasks=tasks; this.leads=leads; this.users=users;
    }
    private void authorize(Principal actor) {
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId()==null
                || actor.permissions()==null || !actor.permissions().contains("LEAD_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lead management permission is required");
    }
    public List<LeadFollowUpTask> list(UUID leadId, Principal actor) {
        authorize(actor);
        leads.findByIdAndTenantId(leadId,actor.tenantId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Lead not found"));
        return tasks.findByTenantIdAndLeadIdOrderByDueAtAsc(actor.tenantId(),leadId);
    }
    @Transactional
    public LeadFollowUpTask create(UUID leadId, CreateFollowUp input, Principal actor) {
        authorize(actor);
        leads.findByIdAndTenantId(leadId,actor.tenantId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Lead not found"));
        if(input.dueAt()==null || !input.dueAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Follow-up due date must be in the future");
        AppUser assignee=users.findById(input.assignedTo())
                .filter(u->actor.tenantId().equals(u.getTenantId()) && u.isEnabled() && "VENDOR".equals(u.getAccountType())
                        && u.getRoles().stream().anyMatch(role->Set.of("VENDOR_ADMIN","SALES_EXECUTIVE","SALES_MANAGER","MANAGER").contains(role.getName())))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Assignee must be an enabled sales user in this vendor"));
        LeadFollowUpTask task=new LeadFollowUpTask();
        task.setId(UUID.randomUUID());task.setTenantId(actor.tenantId());task.setLeadId(leadId);
        task.setAssignedTo(assignee.getId());task.setCreatedBy(actor.userId());task.setDueAt(input.dueAt());
        task.setStatus("OPEN");task.setNote(input.note()==null?null:input.note().trim());task.setCreatedAt(Instant.now());
        return tasks.save(task);
    }
    @Transactional
    public LeadFollowUpTask complete(UUID id, Principal actor) {
        authorize(actor);
        LeadFollowUpTask task=tasks.findByIdAndTenantId(id,actor.tenantId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Follow-up task not found"));
        if(!task.getAssignedTo().equals(actor.userId())
                &&(actor.roles()==null||!actor.roles().contains("VENDOR_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the assigned user or vendor administrator can complete this task");
        if(!"OPEN".equals(task.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Follow-up task is not open");
        task.setStatus("COMPLETED");task.setCompletedAt(Instant.now());
        return tasks.save(task);
    }
}
