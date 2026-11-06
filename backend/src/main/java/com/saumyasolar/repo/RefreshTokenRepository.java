package com.saumyasolar.repo;

import com.saumyasolar.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    java.util.List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);
}
