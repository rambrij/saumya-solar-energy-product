package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_invitation")
@Getter @Setter @NoArgsConstructor
public class VendorInvitation {
    @Id
    private UUID id;
    @Column(name="tenant_id", nullable=false)
    private UUID tenantId;
    @Column(name="user_id", nullable=false)
    private UUID userId;
    @Column(name="token_hash", nullable=false, unique=true, length=64)
    private String tokenHash;
    @Column(name="expires_at", nullable=false)
    private Instant expiresAt;
    @Column(name="accepted_at")
    private Instant acceptedAt;
    @Column(name="revoked_at")
    private Instant revokedAt;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="invited_by")
    private UUID invitedBy;
}
