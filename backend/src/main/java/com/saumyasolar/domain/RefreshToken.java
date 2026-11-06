package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
    @Id
    UUID id;
    UUID userId;
    String token;
    Instant expiresAt;
    boolean revoked;
}
