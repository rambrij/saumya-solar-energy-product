package com.saumyasolar.service;
import com.saumyasolar.domain.CustomerProfile;
import com.saumyasolar.domain.Warranty;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.WarrantyRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.assertEquals;

class WarrantyExpirySchedulerTest {
 @Test void marksEndedActiveWarrantyExpired(){
  WarrantyRepository warranties=mock(WarrantyRepository.class);CustomerProfileRepository customers=mock(CustomerProfileRepository.class);NotificationService notifications=mock(NotificationService.class);
  Warranty w=new Warranty();w.setId(UUID.randomUUID());w.setTenantId(UUID.randomUUID());w.setCustomerId(UUID.randomUUID());w.setStatus("ACTIVE");w.setEndDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1));
  when(warranties.findByStatusAndEndDateBefore("ACTIVE",LocalDate.now(ZoneId.of("Asia/Kolkata")))).thenReturn(List.of(w));
  new WarrantyExpiryScheduler(warranties,customers,notifications,"Asia/Kolkata").evaluateWarrantyExpiry();
  assertEquals("EXPIRED",w.getStatus());verify(warranties).save(w);verifyNoInteractions(notifications);
 }
 @Test void sendsThirtyDayExpiryReminder(){
  WarrantyRepository warranties=mock(WarrantyRepository.class);CustomerProfileRepository customers=mock(CustomerProfileRepository.class);NotificationService notifications=mock(NotificationService.class);
  UUID tenant=UUID.randomUUID(),customerId=UUID.randomUUID(),user=UUID.randomUUID();
  Warranty w=new Warranty();w.setId(UUID.randomUUID());w.setTenantId(tenant);w.setCustomerId(customerId);w.setStatus("ACTIVE");w.setEndDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(30));
  CustomerProfile c=new CustomerProfile();c.setId(customerId);c.setTenantId(tenant);c.setUserId(user);c.setEmail("customer@example.test");c.setActive(true);
  when(warranties.findByStatusAndEndDate("ACTIVE",LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(30))).thenReturn(List.of(w));when(customers.findByIdAndTenantId(customerId,tenant)).thenReturn(Optional.of(c));
  new WarrantyExpiryScheduler(warranties,customers,notifications,"Asia/Kolkata").evaluateWarrantyExpiry();
  verify(notifications).sendBusinessNotification(user,tenant,"WARRANTY_EXPIRY_30_DAYS","customer@example.test","Warranty expiry reminder", "Your product warranty expires in 30 day(s), on "+w.getEndDate()+". Warranty reference: "+w.getId()+". Contact your vendor for assistance.");
 }
}