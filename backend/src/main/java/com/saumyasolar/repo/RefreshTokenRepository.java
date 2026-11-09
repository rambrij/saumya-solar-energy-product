package com.saumyasolar.repo;

import com.saumyasolar.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.token=:token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);
    
    java.util.List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);
}
