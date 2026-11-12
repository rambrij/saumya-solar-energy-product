package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.PlatformSetting;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.repo.PlatformSettingRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PlatformSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformSettingsServiceTest {
    private Principal platform() {
        return new Principal(UUID.randomUUID(), null, "platform@example.test", "PLATFORM",
                Set.of("PLATFORM_SUPER_ADMIN"), Set.of("PLATFORM_MANAGE"), 0);
    }

    @Test
    void returnsOnlyTypedAllowlistedDefaultsAndOverrides() {
        PlatformSettingRepository repository = mock(PlatformSettingRepository.class);
        when(repository.findAll()).thenReturn(java.util.List.of());
        var service = new PlatformSettingsService(repository, mock(AuditEventRepository.class), new ObjectMapper());
        var result = service.list(platform());
        assertTrue(result.stream().anyMatch(x -> x.key().equals("PLATFORM_DEFAULT_CURRENCY") && x.value().equals("INR")));
        assertTrue(result.stream().anyMatch(x -> x.key().equals("PLATFORM_DEFAULT_TIME_ZONE") && x.value().equals("Asia/Kolkata")));
        assertFalse(result.stream().anyMatch(x -> x.key().toLowerCase().contains("secret") || x.key().toLowerCase().contains("password")));
    }

    @Test
    void rejectsVendorAndSupportRolesBeforeDatabaseAccess() {
        PlatformSettingRepository repository = mock(PlatformSettingRepository.class);
        var service = new PlatformSettingsService(repository, mock(AuditEventRepository.class), new ObjectMapper());
        Principal vendor = new Principal(UUID.randomUUID(), UUID.randomUUID(), "vendor@example.test",
                "VENDOR", Set.of("VENDOR_ADMIN"), Set.of("PLATFORM_MANAGE"), 0);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.list(vendor)).getStatusCode());
        Principal support = new Principal(UUID.randomUUID(), null, "support@example.test",
                "PLATFORM", Set.of("PLATFORM_SUPPORT"), Set.of("PLATFORM_MANAGE"), 0);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.list(support)).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnknownKeysAndInvalidTypedValues() {
        PlatformSettingRepository repository = mock(PlatformSettingRepository.class);
        var service = new PlatformSettingsService(repository, mock(AuditEventRepository.class), new ObjectMapper());
        Principal actor = platform();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.update("APP_PAYMENT_SECRET", "secret", 0, actor)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.update("PLATFORM_DEFAULT_CURRENCY", "INR; DROP TABLE tenant", 0, actor)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.update("PLATFORM_DEFAULT_TIME_ZONE", "Not/A_TimeZone", 0, actor)).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void requiresExpectedVersionAndPersistsAudit() {
        PlatformSettingRepository repository = mock(PlatformSettingRepository.class);
        AuditEventRepository audits = mock(AuditEventRepository.class);
        when(repository.findById("PLATFORM_DEFAULT_CURRENCY")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PlatformSetting.class))).thenAnswer(invocation -> {
            PlatformSetting row = invocation.getArgument(0); row.setVersion(1); return row;
        });
        var service = new PlatformSettingsService(repository, audits, new ObjectMapper());
        Principal actor = platform();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.update("PLATFORM_DEFAULT_CURRENCY", "USD", null, actor)).getStatusCode());
        var updated = service.update("PLATFORM_DEFAULT_CURRENCY", "USD", 0, actor);
        assertEquals("USD", updated.value());
        assertEquals(1, updated.version());
        verify(audits).save(argThat(event -> "PLATFORM_SETTING_UPDATED".equals(event.getAction())
                && "PLATFORM_SETTING".equals(event.getEntityType())));
    }
}
