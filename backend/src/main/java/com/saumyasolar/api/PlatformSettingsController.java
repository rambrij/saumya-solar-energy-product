package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PlatformSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/settings")
public class PlatformSettingsController {
    private final PlatformSettingsService service;
    public PlatformSettingsController(PlatformSettingsService service) { this.service = service; }

    @GetMapping
    public List<PlatformSettingsService.SettingView> list(Authentication authentication) {
        return service.list((Principal) authentication.getPrincipal());
    }

    @PutMapping("/{key}")
    public PlatformSettingsService.SettingView update(@PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request, Authentication authentication) {
        return service.update(key, request.value(), request.expectedVersion(), (Principal) authentication.getPrincipal());
    }

    public record UpdateSettingRequest(@NotNull String value, @NotNull @PositiveOrZero Integer expectedVersion) {}
}
