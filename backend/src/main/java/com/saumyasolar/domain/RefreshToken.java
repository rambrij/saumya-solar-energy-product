package com.saumyasolar.domain;

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
@Table(name = "refresh_token", indexes = {
        @Index(name = "ix_refresh_token_family", columnList = "family_id"),
        @Index(name = "ix_refresh_token_user_family", columnList = "user_id,family_id")
})
@Getter @Setter @NoArgsConstructor
public class RefreshToken {
    @Id UUID id;
    UUID userId;
    UUID familyId;
    UUID replacedByTokenId;
    String token;
    Instant expiresAt;
    Instant createdAt;
    boolean revoked;
}
