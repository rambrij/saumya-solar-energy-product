package com.saumyasolar.service;

import com.saumyasolar.domain.AuditLog;
import com.saumyasolar.repo.AuditLogRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;
    public AuditLogService(AuditLogRepository repository) { this.repository=repository; }

    public java.util.List<com.saumyasolar.api.AuditLogModels.AuditLogView> list(Principal actor) {
        java.util.List<AuditLog> rows;
        if ("PLATFORM".equals(actor.accountType())
                && actor.roles() != null && actor.roles().contains("PLATFORM_SUPER_ADMIN")
                && actor.permissions() != null && actor.permissions().contains("PLATFORM_MANAGE")) {
            rows = repository.findTop100ByOrderByCreatedAtDesc();
        } else if ("VENDOR".equals(actor.accountType()) && actor.tenantId() != null
                && actor.roles() != null && actor.roles().contains("VENDOR_ADMIN")) {
            rows = repository.findTop100ByTenantIdOrderByCreatedAtDesc(actor.tenantId());
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Audit log access is restricted to administrators");
        }
        return rows.stream().map(row -> new com.saumyasolar.api.AuditLogModels.AuditLogView(
                row.getId(), row.getTenantId(), row.getActorId(), row.getAccountType(), row.getHttpMethod(),
                row.getRequestPath(), row.getResponseStatus(), row.getSourceIp(), row.getCreatedAt())).toList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(Principal actor,String method,String path,int status,String sourceIp) {
        AuditLog row=new AuditLog();
        row.setId(UUID.randomUUID());
        row.setTenantId(actor.tenantId());
        row.setActorId(actor.userId());
        row.setAccountType(actor.accountType());
        row.setHttpMethod(method);
        row.setRequestPath(path.length()>500?path.substring(0,500):path);
        row.setResponseStatus(status);
        row.setSourceIp(sourceIp==null?null:sourceIp.substring(0,Math.min(64,sourceIp.length())));
        row.setCreatedAt(Instant.now());
        repository.save(row);
    }
}
