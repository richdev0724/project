package com.marketmono.repository.auth;

import com.marketmono.entity.auth.PasswordResetToken;
import com.marketmono.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PasswordResetToken entity operations.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    
    /**
     * Find password reset token by token string.
     */
    Optional<PasswordResetToken> findByToken(String token);
    
    /**
     * Find latest valid (non-expired, non-used) token for a user.
     */
    @Query("""
        SELECT p FROM PasswordResetToken p 
        WHERE p.user = :user 
        AND p.usedAt IS NULL 
        AND p.expiresAt > :now 
        ORDER BY p.createdAt DESC 
        LIMIT 1
        """)
    Optional<PasswordResetToken> findLatestValidTokenByUser(@Param("user") User user, @Param("now") LocalDateTime now);
    
    /**
     * Check if user has a valid pending token (for rate limiting).
     */
    @Query("""
        SELECT COUNT(p) 
        FROM PasswordResetToken p 
        WHERE p.user = :user 
        AND p.usedAt IS NULL 
        AND p.createdAt > :since
        """)
    long countRecentTokens(@Param("user") User user, @Param("since") LocalDateTime since);

    default boolean hasRecentToken(User user, LocalDateTime since) {
        return countRecentTokens(user, since) > 0;
    }
    
    /**
     * Invalidate all pending tokens for a user (when password is changed).
     */
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.usedAt = :now WHERE p.user = :user AND p.usedAt IS NULL")
    int invalidateAllByUser(@Param("user") User user, @Param("now") LocalDateTime now);
    
    /**
     * Delete expired tokens (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now AND p.usedAt IS NULL")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}
