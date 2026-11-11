package com.saumyasolar;
import com.saumyasolar.domain.NotificationPreference;
import com.saumyasolar.repo.NotificationPreferenceRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.NotificationPreferenceService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationPreferenceServiceTest {
 @Test void requiresExplicitConsentBeforeEnablingSms(){
  var repo=mock(NotificationPreferenceRepository.class);var service=new NotificationPreferenceService(repo);
  var actor=new Principal(UUID.randomUUID(),UUID.randomUUID(),"customer@example.test","CUSTOMER",Set.of(),Set.of());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.update(actor,"SMS",true,false));
  verifyNoInteractions(repo);
 }
 @Test void persistsTenantAndUserScopedPreferenceAndConsent(){
  var repo=mock(NotificationPreferenceRepository.class);var service=new NotificationPreferenceService(repo);
  var tenant=UUID.randomUUID();var user=UUID.randomUUID();var actor=new Principal(user,tenant,"customer@example.test","CUSTOMER",Set.of(),Set.of());
  when(repo.findByTenantIdAndUserIdAndChannel(tenant,user,"WHATSAPP")).thenReturn(Optional.empty());
  when(repo.save(any(NotificationPreference.class))).thenAnswer(inv->inv.getArgument(0));
  var result=service.update(actor,"WHATSAPP",true,true);
  assertEquals("WHATSAPP",result.channel());assertTrue(result.enabled());assertTrue(result.consented());
  verify(repo).save(argThat(row->tenant.equals(row.getTenantId())&&user.equals(row.getUserId())));
 }
 @Test void rejectsUnsupportedChannel(){
  var repo=mock(NotificationPreferenceRepository.class);var service=new NotificationPreferenceService(repo);
  var actor=new Principal(UUID.randomUUID(),UUID.randomUUID(),"customer@example.test","CUSTOMER",Set.of(),Set.of());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.update(actor,"PAGER",true,false));
 }
}