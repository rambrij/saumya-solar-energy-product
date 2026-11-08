package com.saumyasolar.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "mfa_challenge")
@Getter @Setter @NoArgsConstructor
public class MfaChallenge {
    @Id UUID id;
    UUID userId;
    String codeHash;
    Instant expiresAt;
    int attempts;
    boolean used;
    Instant createdAt;
}