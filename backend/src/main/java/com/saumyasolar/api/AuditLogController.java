package com.saumyasolar.api;

import com.saumyasolar.api.AuditLogModels.AuditLogView;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.AuditLogService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {
    private final AuditLogService audit;
    public AuditLogController(AuditLogService audit) { this.audit = audit; }
    @GetMapping
    List<AuditLogView> list(Authentication authentication) {
        return audit.list((Principal) authentication.getPrincipal());
    }
}
