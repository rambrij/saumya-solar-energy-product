package com.saumyasolar.service;
import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;import com.saumyasolar.repo.*;import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;import java.util.*;
@Service
public class IntegrationManagementService {
 private final IntegrationConnectionRepository connections;private final IntegrationSyncJobRepository jobs;private final AuditEventRepository audits;private final ObjectMapper mapper;private final EnterpriseSaasService entitlements;
 public IntegrationManagementService(IntegrationConnectionRepository c,IntegrationSyncJobRepository j,AuditEventRepository a,ObjectMapper m,EnterpriseSaasService entitlements){connections=c;jobs=j;audits=a;mapper=m;this.entitlements=entitlements;}
 private UUID tenant(Principal p){if(!"VENDOR".equals(p.accountType())||p.tenantId()==null||p.permissions()==null||!p.permissions().contains("INTEGRATION_MANAGE"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p.tenantId();}
 private void scan(JsonNode node){if(node.isObject())node.fields().forEachRemaining(e->{String k=e.getKey().toLowerCase(Locale.ROOT);if(k.contains("secret")||k.contains("password")||k.contains("token")||k.contains("apikey")||k.contains("clientsecret"))throw new IllegalArgumentException();scan(e.getValue());});else if(node.isArray())node.forEach(this::scan);}
 private void validateJson(String value){try{JsonNode n=mapper.readTree(value);if(n==null||!n.isObject())throw new IllegalArgumentException();scan(n);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configuration/payload must be a JSON object and must not contain credentials");}}
 private void audit(Principal p,String action,String type,UUID id,Object value){try{AuditEvent a=new AuditEvent();a.setId(UUID.randomUUID());a.setTenantId(p.tenantId());a.setActorId("SYSTEM".equals(p.accountType())?null:p.userId());a.setActorType(p.accountType());a.setAction(action);a.setEntityType(type);a.setEntityId(id.toString());a.setOutcome("SUCCESS");a.setAfterJson(mapper.writeValueAsString(value));a.setCreatedAt(Instant.now());audits.save(a);}catch(Exception ex){throw new IllegalStateException("Could not persist integration audit event",ex);}}
 @Transactional public IntegrationConnection create(ConnectionInput x,Principal p){
  UUID t=tenant(p);
  if(x.providerCode()==null||!x.providerCode().trim().matches("[A-Za-z0-9][A-Za-z0-9_-]{1,59}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provider code must be 2 to 60 alphanumeric characters, dashes or underscores");
  String type=x.integrationType().toUpperCase(Locale.ROOT);
  if(!Set.of("ACCOUNTING","PAYMENT_GATEWAY","SMS","WHATSAPP","GENERIC").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported integration type");
  String feature=switch(type){case "ACCOUNTING"->"accountingIntegration";case "PAYMENT_GATEWAY"->"paymentGateway";default->"apiIntegrations";};
  entitlements.assertFeatureEnabled(t,feature);
  if(x.secretReference()!=null&&!x.secretReference().isBlank()&&!x.secretReference().startsWith("env://"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use env:// secret references; raw credentials are not accepted");
  if(x.secretReference()!=null&&x.secretReference().startsWith("env://")&&!x.secretReference().substring(6).matches("[A-Z_][A-Z0-9_]{0,127}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid environment secret reference");
  validateJson(x.configJson());IntegrationConnection c=new IntegrationConnection();c.setId(UUID.randomUUID());c.setTenantId(t);c.setProviderCode(x.providerCode().trim().toUpperCase(Locale.ROOT));c.setIntegrationType(type);
  c.setDisplayName(x.displayName().trim());c.setStatus("ACTIVE");c.setSecretReference(x.secretReference());c.setConfigJson(x.configJson());c.setCreatedAt(Instant.now());c.setUpdatedAt(Instant.now());connections.save(c);
  audit(p,"INTEGRATION_CREATED","INTEGRATION_CONNECTION",c.getId(),Map.of("providerCode",c.getProviderCode(),"integrationType",type));return c;
 }
 public List<IntegrationConnection> list(Principal p){return connections.findByTenantIdOrderByCreatedAtDesc(tenant(p));}
 @Transactional public IntegrationConnection setStatus(UUID id,String status,Principal p){
  UUID t=tenant(p);
  if(!Set.of("ACTIVE","DISABLED").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Integration status must be ACTIVE or DISABLED");
  IntegrationConnection c=connections.findByIdAndTenantId(id,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Integration not found"));
  c.setStatus(status);c.setUpdatedAt(Instant.now());connections.save(c);
  audit(p,"INTEGRATION_"+status,"INTEGRATION_CONNECTION",id,Map.of("providerCode",c.getProviderCode(),"status",status));
  return c;
 }
 @Transactional public IntegrationSyncJob enqueue(UUID connectionId,JobInput x,Principal p){
  UUID t=tenant(p);IntegrationConnection c=connections.findByIdAndTenantId(connectionId,t).filter(v->"ACTIVE".equals(v.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Active integration not found"));
  if(x.entityType()==null||!x.entityType().matches("[A-Za-z][A-Za-z0-9_]{1,59}")||x.entityId()==null||x.entityId().isBlank()||x.entityId().length()>100||x.operation()==null||!x.operation().matches("[A-Za-z][A-Za-z0-9_]{1,29}")||x.idempotencyKey()==null||x.idempotencyKey().isBlank()||x.idempotencyKey().length()>160)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Entity type, entity ID, operation and idempotency key must be valid and within their length limits");
  validateJson(x.payloadJson());
  Optional<IntegrationSyncJob> existing=jobs.findByTenantIdAndConnectionIdAndIdempotencyKey(t,connectionId,x.idempotencyKey());if(existing.isPresent())return existing.get();
  IntegrationSyncJob j=new IntegrationSyncJob();j.setId(UUID.randomUUID());j.setTenantId(t);j.setConnectionId(connectionId);j.setEntityType(x.entityType().toUpperCase(Locale.ROOT));j.setEntityId(x.entityId());j.setOperation(x.operation().toUpperCase(Locale.ROOT));j.setIdempotencyKey(x.idempotencyKey());j.setPayloadJson(x.payloadJson());j.setStatus("QUEUED");j.setAttemptCount(0);j.setNextAttemptAt(Instant.now());j.setCreatedAt(Instant.now());j.setUpdatedAt(Instant.now());jobs.save(j);
  audit(p,"INTEGRATION_SYNC_QUEUED","INTEGRATION_SYNC_JOB",j.getId(),Map.of("entityType",j.getEntityType(),"entityId",j.getEntityId(),"operation",j.getOperation()));return j;
 }
 public List<IntegrationSyncJob> jobs(UUID connectionId,Principal p){UUID t=tenant(p);connections.findByIdAndTenantId(connectionId,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Integration not found"));return jobs.findTop100ByTenantIdAndConnectionIdOrderByCreatedAtDesc(t,connectionId);}
 @Transactional public IntegrationSyncJob retry(UUID id,Principal p){UUID t=tenant(p);IntegrationSyncJob j=jobs.findByIdAndTenantId(id,t).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Sync job not found"));
  if(!Set.of("DEAD_LETTER","WAITING_FOR_ADAPTER","RETRY").contains(j.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed or waiting jobs can be retried");
  j.setStatus("QUEUED");j.setAttemptCount(0);j.setLastError(null);j.setNextAttemptAt(Instant.now());j.setUpdatedAt(Instant.now());IntegrationSyncJob saved=jobs.save(j);audit(p,"INTEGRATION_SYNC_RETRIED","INTEGRATION_SYNC_JOB",j.getId(),Map.of("status","QUEUED"));return saved;
 }
 @Transactional public void enqueueAccountingEvent(String entityType,String entityId,String operation,Map<String,Object> payload,Principal p){
  if(p==null||(!"VENDOR".equals(p.accountType())&&!"SYSTEM".equals(p.accountType()))||p.tenantId()==null)return;
  List<IntegrationConnection> accounting=connections.findByTenantIdAndIntegrationTypeAndStatus(p.tenantId(),"ACCOUNTING","ACTIVE");
  for(IntegrationConnection connection:accounting){
   String key=entityType.toUpperCase(Locale.ROOT)+":"+entityId+":"+operation.toUpperCase(Locale.ROOT)+":v1";
   if(jobs.findByTenantIdAndConnectionIdAndIdempotencyKey(p.tenantId(),connection.getId(),key).isPresent())continue;
   try{
    String json=mapper.writeValueAsString(payload);validateJson(json);
    IntegrationSyncJob job=new IntegrationSyncJob();job.setId(UUID.randomUUID());job.setTenantId(p.tenantId());job.setConnectionId(connection.getId());
    job.setEntityType(entityType.toUpperCase(Locale.ROOT));job.setEntityId(entityId);job.setOperation(operation.toUpperCase(Locale.ROOT));
    job.setIdempotencyKey(key);job.setPayloadJson(json);job.setStatus("QUEUED");job.setAttemptCount(0);job.setNextAttemptAt(Instant.now());
    job.setCreatedAt(Instant.now());job.setUpdatedAt(Instant.now());jobs.save(job);
    audit(p,"ACCOUNTING_SYNC_QUEUED","INTEGRATION_SYNC_JOB",job.getId(),Map.of("entityType",job.getEntityType(),"entityId",entityId));
   }catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Unable to serialize accounting synchronization payload",ex);}
  }
 }
 public record ConnectionInput(String providerCode,String integrationType,String displayName,String secretReference,String configJson){}
 public record JobInput(String entityType,String entityId,String operation,String idempotencyKey,String payloadJson){}
}
