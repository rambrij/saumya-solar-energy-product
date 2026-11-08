package com.saumyasolar.service;
import com.saumyasolar.domain.*;import com.saumyasolar.repo.*;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
import java.time.Instant;import java.util.*;
@Component
public class IntegrationSyncScheduler {
 private final IntegrationSyncJobRepository jobs;private final IntegrationConnectionRepository connections;private final List<IntegrationAdapter> adapters;
 public IntegrationSyncScheduler(IntegrationSyncJobRepository j,IntegrationConnectionRepository c,List<IntegrationAdapter> a){jobs=j;connections=c;adapters=a;}
 @Scheduled(fixedDelayString="${app.integrations.dispatch-interval-ms:30000}")
 public void dispatch(){
  Instant now=Instant.now(),staleBefore=now.minusSeconds(600);
  List<IntegrationSyncJob> batch=new ArrayList<>(jobs.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(List.of("QUEUED","RETRY"),now));
  batch.addAll(jobs.findTop50ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc("PROCESSING",staleBefore));
  for(IntegrationSyncJob candidate:batch){
   Instant claimedAt=Instant.now();
   if(jobs.claimIfDue(candidate.getId(),claimedAt,staleBefore)!=1)continue;
   IntegrationSyncJob job=jobs.findById(candidate.getId()).orElse(null);
   if(job==null)continue;
   Optional<IntegrationConnection> c=connections.findByIdAndTenantId(job.getConnectionId(),job.getTenantId());
   if(c.isEmpty()||!"ACTIVE".equals(c.get().getStatus())){job.setStatus("DEAD_LETTER");job.setLastError("Integration connection is unavailable");job.setUpdatedAt(Instant.now());jobs.save(job);continue;}
   Optional<IntegrationAdapter> adapter=adapters.stream().filter(a->a.providerCode().equalsIgnoreCase(c.get().getProviderCode())).findFirst();
   if(adapter.isEmpty()){job.setStatus("WAITING_FOR_ADAPTER");job.setLastError("No provider adapter is installed for "+c.get().getProviderCode());job.setUpdatedAt(Instant.now());jobs.save(job);continue;}
   try{adapter.get().synchronize(c.get(),job);job.setStatus("SUCCEEDED");job.setLastError(null);}
   catch(Exception ex){int n=job.getAttemptCount();String msg=ex.getMessage()==null?"Provider synchronization failed":ex.getMessage();job.setLastError(msg.substring(0,Math.min(1000,msg.length())));job.setStatus(n>=8?"DEAD_LETTER":"RETRY");job.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600,30L*(1L<<Math.min(n,6)))));}
   job.setUpdatedAt(Instant.now());jobs.save(job);
  }
 }
}
