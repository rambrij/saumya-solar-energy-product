package com.saumyasolar.service;

import com.saumyasolar.domain.Warranty;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.WarrantyRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Set;

/** Daily tenant-scoped warranty expiry transitions and opt-in customer alerts. */
@Component
public class WarrantyExpiryScheduler {
 private final WarrantyRepository warranties;
 private final CustomerProfileRepository customers;
 private final NotificationService notifications;
 public WarrantyExpiryScheduler(WarrantyRepository warranties,CustomerProfileRepository customers,NotificationService notifications){
  this.warranties=warranties;this.customers=customers;this.notifications=notifications;
 }
 @Scheduled(cron="${app.warranty.expiry-alert-cron:0 15 8 * * *}",zone="${app.warranty.expiry-alert-zone:Asia/Kolkata}")
 public void evaluateWarrantyExpiry(){
  LocalDate today=LocalDate.now();
  for(Warranty warranty:warranties.findAll()){
   if(warranty.getTenantId()==null||warranty.getEndDate()==null||!"ACTIVE".equals(warranty.getStatus()))continue;
   long days=java.time.temporal.ChronoUnit.DAYS.between(today,warranty.getEndDate());
   if(days<0){warranty.setStatus("EXPIRED");warranties.save(warranty);continue;}
   if(!Set.of(30L,7L,1L).contains(days))continue;
   customers.findByIdAndTenantId(warranty.getCustomerId(),warranty.getTenantId()).filter(c->c.isActive()).ifPresent(customer->{
    String event="WARRANTY_EXPIRY_"+days+"_DAYS";
    String body="Your product warranty expires in "+days+" day(s), on "+warranty.getEndDate()+". Warranty reference: "+warranty.getId()+". Contact your vendor for assistance.";
    notifications.sendBusinessNotification(customer.getUserId(),warranty.getTenantId(),event,customer.getEmail(),"Warranty expiry reminder",body);
   });
  }
 }
}