package com.saumyasolar.service;

import com.saumyasolar.domain.NotificationPreference;
import com.saumyasolar.repo.NotificationPreferenceRepository;
import com.saumyasolar.security.Principal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationPreferenceService {
  private static final Set<String> CHANNELS=Set.of("EMAIL","SMS","WHATSAPP","IN_APP");
  private final NotificationPreferenceRepository repository;
  public NotificationPreferenceService(NotificationPreferenceRepository repository){this.repository=repository;}
  public List<View> list(Principal actor){ requireTenant(actor); return repository.findByTenantIdAndUserId(actor.tenantId(),actor.userId()).stream().map(View::from).toList(); }
  @Transactional public View update(Principal actor,String channel,boolean enabled,boolean consented){
    requireTenant(actor); String c=channel==null?"":channel.toUpperCase(Locale.ROOT); if(!CHANNELS.contains(c)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported notification channel");
    if(("SMS".equals(c)||"WHATSAPP".equals(c)) && enabled && !consented) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit channel consent is required");
    var row=repository.findByTenantIdAndUserIdAndChannel(actor.tenantId(),actor.userId(),c).orElseGet(()->{var n=new NotificationPreference();n.setId(UUID.randomUUID());n.setTenantId(actor.tenantId());n.setUserId(actor.userId());n.setChannel(c);return n;});
    row.setEnabled(enabled);row.setConsented(consented);row.setConsentUpdatedAt(Instant.now());row.setUpdatedAt(Instant.now());return View.from(repository.save(row));
  }
  private void requireTenant(Principal actor){if(actor==null||actor.userId()==null||actor.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant user required"); if("VENDOR".equals(actor.accountType())&&(actor.permissions()==null||!actor.permissions().contains("NOTIFICATION_VIEW")))throw new ResponseStatusException(HttpStatus.FORBIDDEN); if(!Set.of("VENDOR","CUSTOMER").contains(actor.accountType()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
  public record View(String channel,boolean enabled,boolean consented,Instant consentUpdatedAt){static View from(NotificationPreference n){return new View(n.getChannel(),n.isEnabled(),n.isConsented(),n.getConsentUpdatedAt());}}
}