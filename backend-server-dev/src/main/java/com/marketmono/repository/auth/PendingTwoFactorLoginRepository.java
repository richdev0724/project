package com.marketmono.repository.auth;

import com.marketmono.entity.auth.PendingTwoFactorLogin;
import com.marketmono.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for pending 2FA login requests.
 */
@Repository
public interface PendingTwoFactorLoginRepository extends JpaRepository<PendingTwoFactorLogin, UUID> {
    
    /**
     * Find a pending 2FA login by token.
     */
    Optional<PendingTwoFactorLogin> findByToken(String token);
    
    /**
     * Delete all pending 2FA logins for a user.
     */
    void deleteByUser(User user);
    
    /**
     * Delete expired pending 2FA logins.
     */
    @Modifying
    @Query("DELETE FROM PendingTwoFactorLogin p WHERE p.expiresAt < :now")
    int deleteExpired(LocalDateTime now);
}
