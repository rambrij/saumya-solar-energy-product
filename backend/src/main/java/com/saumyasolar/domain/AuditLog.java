package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="audit_log", indexes={
        @Index(name="ix_audit_tenant_created",columnList="tenant_id,created_at"),
        @Index(name="ix_audit_actor_created",columnList="actor_id,created_at")
})
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id
    private UUID id;
    @Column(name="tenant_id")
    private UUID tenantId;
    @Column(name="actor_id")
    private UUID actorId;
    @Column(name="account_type",length=20)
    private String accountType;
    @Column(nullable=false,length=10)
    private String httpMethod;
    @Column(nullable=false,length=500)
    private String requestPath;
    @Column(nullable=false)
    private int responseStatus;
    @Column(length=64)
    private String sourceIp;
    @Column(nullable=false)
    private Instant createdAt;
}
