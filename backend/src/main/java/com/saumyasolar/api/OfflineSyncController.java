package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.OfflineSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offline-sync")
public class OfflineSyncController {
    private final OfflineSyncService sync;
    public OfflineSyncController(OfflineSyncService sync) { this.sync = sync; }

    @PostMapping("/operations")
    public List<OfflineSyncService.SyncResult> synchronize(
            @Valid @RequestBody BatchRequest request, Authentication authentication) {
        return sync.synchronize(request.operations(), (Principal) authentication.getPrincipal());
    }

    @GetMapping("/operations")
    public List<OfflineSyncService.SyncResult> status(Authentication authentication) {
        return sync.status((Principal) authentication.getPrincipal());
    }

    public record BatchRequest(@NotNull @Size(min = 1, max = 50)
                               List<@Valid OfflineSyncService.SyncCommand> operations) {}
}
