package com.saumyasolar.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
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
    @Column(length = 64)
    String codeHash;
    Instant expiresAt;
    int attempts;
    boolean used;
    Instant createdAt;
}