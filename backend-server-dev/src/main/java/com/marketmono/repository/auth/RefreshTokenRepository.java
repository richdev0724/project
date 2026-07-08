package com.marketmono.repository.auth;

import com.marketmono.entity.auth.RefreshToken;
import com.marketmono.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken entity operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    /**
     * Find refresh token by token string.
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * Find all active (non-revoked, non-expired) tokens for a user.
     */
    @Query("SELECT r FROM RefreshToken r WHERE r.user = :user AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUser(@Param("user") User user, @Param("now") LocalDateTime now);
    
    /**
     * Revoke all tokens for a user (logout from all devices).
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user AND r.revoked = false")
    int revokeAllByUser(@Param("user") User user);
    
    /**
     * Delete expired tokens (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * Count active sessions for a user.
     */
    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.user = :user AND r.revoked = false AND r.expiresAt > :now")
    long countActiveSessionsByUser(@Param("user") User user, @Param("now") LocalDateTime now);
}
