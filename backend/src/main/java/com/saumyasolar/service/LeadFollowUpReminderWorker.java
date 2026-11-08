package com.saumyasolar.service;

import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.LeadFollowUpTaskRepository;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class LeadFollowUpReminderWorker {
    private final LeadFollowUpTaskRepository tasks;
    private final AppUserRepository users;
    private final NotificationService notifications;
    public LeadFollowUpReminderWorker(LeadFollowUpTaskRepository tasks, AppUserRepository users, NotificationService notifications) {
        this.tasks=tasks;this.users=users;this.notifications=notifications;
    }
    @Scheduled(fixedDelayString="${app.leads.follow-up-reminder-interval-ms:60000}")
    @Transactional
    public void sendDueReminders() {
        for(var task:tasks.findTop50ByStatusAndDueAtLessThanEqualAndReminderSentAtIsNullOrderByDueAtAsc("OPEN",Instant.now())) {
            var user=users.findById(task.getAssignedTo()).filter(u->u.isEnabled()).orElse(null);
            if(user==null) { task.setReminderSentAt(Instant.now()); tasks.save(task); continue; }
            boolean queued=notifications.sendBusinessNotification(user.getId(),task.getTenantId(),"LEAD_FOLLOW_UP_DUE",
                    user.getEmail(),"Lead follow-up is due","A lead follow-up task is due. "+(task.getNote()==null?"":task.getNote()));
            if(queued) { task.setReminderSentAt(Instant.now()); tasks.save(task); }
        }
    }
}
