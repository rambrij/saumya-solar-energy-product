package com.saumyasolar;

import com.saumyasolar.service.EnterpriseSaasService;
import com.saumyasolar.service.TenantStorageMeteringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantStorageMeteringServiceTest {
    @TempDir
    Path temp;
    
    @Test
    void measuresTenantBytesAcrossAllPrivateEvidenceRoots() throws Exception {
        EnterpriseSaasService entitlements = mock(EnterpriseSaasService.class);
        UUID tenant = UUID.randomUUID();
        Path service = temp.resolve("service"), survey = temp.resolve("survey"), warranty = temp.resolve("warranty");
        Files.createDirectories(service.resolve(tenant.toString()).resolve("service-tickets"));
        Files.createDirectories(survey.resolve(tenant.toString()).resolve("surveys"));
        Files.createDirectories(warranty.resolve(tenant.toString()).resolve("warranty-claims"));
        Files.write(service.resolve(tenant.toString()).resolve("service-tickets/a.bin"), new byte[3]);
        Files.write(survey.resolve(tenant.toString()).resolve("surveys/b.bin"), new byte[4]);
        Files.write(warranty.resolve(tenant.toString()).resolve("warranty-claims/c.bin"), new byte[5]);
        TenantStorageMeteringService meter = new TenantStorageMeteringService(entitlements, service.toString(), survey.toString(), warranty.toString());
        assertEquals(12L, meter.usedBytes(tenant));
    }
    
    @Test
    void rejectsAnUploadThatWouldExceedTheTenantPlanLimit() throws Exception {
        EnterpriseSaasService entitlements = mock(EnterpriseSaasService.class);
        UUID tenant = UUID.randomUUID();
        Path service = temp.resolve("service"), survey = temp.resolve("survey"), warranty = temp.resolve("warranty");
        Files.createDirectories(service.resolve(tenant.toString()).resolve("service-tickets"));
        Files.write(service.resolve(tenant.toString()).resolve("service-tickets/existing.bin"), new byte[8]);
        when(entitlements.storageLimitBytes(tenant)).thenReturn(10L);
        TenantStorageMeteringService meter = new TenantStorageMeteringService(entitlements, service.toString(), survey.toString(), warranty.toString());
        assertThrows(ResponseStatusException.class, () -> meter.assertCanStore(tenant, 3));
        verify(entitlements).storageLimitBytes(tenant);
    }
}
