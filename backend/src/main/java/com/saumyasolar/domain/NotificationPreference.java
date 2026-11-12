package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notification_preference", uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","user_id","channel"}))
@Getter @Setter @NoArgsConstructor
public class NotificationPreference {
    @Id UUID id;
    @Column(name="tenant_id", nullable=false) UUID tenantId;
    @Column(name="user_id", nullable=false) UUID userId;
    @Column(nullable=false, length=20) String channel;
    @Column(nullable=false) boolean enabled=true;
    @Column(nullable=false) boolean consented=false;
    @Column(name="consent_updated_at") Instant consentUpdatedAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
}
