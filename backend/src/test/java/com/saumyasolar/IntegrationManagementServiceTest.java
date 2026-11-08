package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.IntegrationManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationManagementServiceTest {
    @Mock IntegrationConnectionRepository connections;
    @Mock IntegrationSyncJobRepository jobs;
    @Mock AuditEventRepository audits;
    @Mock com.saumyasolar.service.EnterpriseSaasService entitlements;
    IntegrationManagementService service;

    @BeforeEach void setUp(){service=new IntegrationManagementService(connections,jobs,audits,new ObjectMapper(),entitlements);}

    @Test void integrationConfigurationRejectsInlineCredentials(){
        var error=assertThrows(ResponseStatusException.class,()->service.create(
                new IntegrationManagementService.ConnectionInput("ZOHO","ACCOUNTING","Zoho Books",null,"{\"apiKey\":\"secret\"}"),
                principal(UUID.randomUUID())));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        verify(connections,never()).save(any());
    }

    @Test void integrationConfigurationRequiresExternalSecretReference(){
        var error=assertThrows(ResponseStatusException.class,()->service.create(
                new IntegrationManagementService.ConnectionInput("ZOHO","ACCOUNTING","Zoho Books","plaintext-secret","{}"),
                principal(UUID.randomUUID())));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        verify(connections,never()).save(any());
    }

    @Test void enqueueIsTenantScopedAndIdempotent(){
        UUID tenantId=UUID.randomUUID(),connectionId=UUID.randomUUID();
        IntegrationConnection connection=new IntegrationConnection();connection.setId(connectionId);connection.setTenantId(tenantId);connection.setStatus("ACTIVE");
        IntegrationSyncJob existing=new IntegrationSyncJob();existing.setId(UUID.randomUUID());existing.setIdempotencyKey("invoice-1-v1");
        when(connections.findByIdAndTenantId(connectionId,tenantId)).thenReturn(Optional.of(connection));
        when(jobs.findByTenantIdAndConnectionIdAndIdempotencyKey(tenantId,connectionId,"invoice-1-v1")).thenReturn(Optional.of(existing));

        IntegrationSyncJob result=service.enqueue(connectionId,new IntegrationManagementService.JobInput("INVOICE","invoice-1","UPSERT","invoice-1-v1","{}"),principal(tenantId));

        assertEquals(existing.getId(),result.getId());
        verify(jobs,never()).save(any());
    }

    @Test void cannotEnqueueAgainstAnotherTenantsConnection(){
        UUID tenantId=UUID.randomUUID(),connectionId=UUID.randomUUID();
        when(connections.findByIdAndTenantId(connectionId,tenantId)).thenReturn(Optional.empty());
        var error=assertThrows(ResponseStatusException.class,()->service.enqueue(connectionId,
                new IntegrationManagementService.JobInput("INVOICE","invoice-1","UPSERT","key-1","{}"),principal(tenantId)));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        verify(jobs,never()).save(any());
    }

    private Principal principal(UUID tenant){return new Principal(UUID.randomUUID(),tenant,"vendor@example.test","VENDOR",Set.of("VENDOR_ADMIN"),Set.of("INTEGRATION_MANAGE"),0);}
}
