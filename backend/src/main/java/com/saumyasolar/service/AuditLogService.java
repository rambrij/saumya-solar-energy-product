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
