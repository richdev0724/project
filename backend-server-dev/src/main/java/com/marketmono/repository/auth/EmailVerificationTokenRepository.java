package com.marketmono.repository.auth;

import com.marketmono.entity.auth.EmailVerificationToken;
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
 * Repository for EmailVerificationToken entity operations.
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    
    /**
     * Find verification token by token string.
     */
    Optional<EmailVerificationToken> findByToken(String token);
    
    /**
     * Find latest valid (non-expired, non-verified) token for a user.
     */
    @Query("""
        SELECT e FROM EmailVerificationToken e 
        WHERE e.user = :user 
        AND e.verifiedAt IS NULL 
        AND e.expiresAt > :now 
        ORDER BY e.createdAt DESC 
        LIMIT 1
        """)
    Optional<EmailVerificationToken> findLatestValidTokenByUser(@Param("user") User user, @Param("now") LocalDateTime now);
    
    /**
     * Check if user has a valid pending token (for rate limiting resend).
     */
    @Query("""
        SELECT COUNT(e) 
        FROM EmailVerificationToken e 
        WHERE e.user = :user 
        AND e.verifiedAt IS NULL 
        AND e.createdAt > :since
        """)
    long countRecentTokens(@Param("user") User user, @Param("since") LocalDateTime since);

    default boolean hasRecentToken(User user, LocalDateTime since) {
        return countRecentTokens(user, since) > 0;
    }

    /**
     * Invalidate all pending verification tokens for a user.
     */
    @Modifying
    @Query("UPDATE EmailVerificationToken e SET e.verifiedAt = :now WHERE e.user = :user AND e.verifiedAt IS NULL")
    int invalidateAllByUser(@Param("user") User user, @Param("now") LocalDateTime now);
    
    /**
     * Delete expired tokens (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.expiresAt < :now AND e.verifiedAt IS NULL")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}
