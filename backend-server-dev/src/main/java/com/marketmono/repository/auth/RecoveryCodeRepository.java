package com.marketmono.repository.auth;

import com.marketmono.entity.auth.RecoveryCode;
import com.marketmono.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for 2FA recovery codes.
 */
@Repository
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {
    
    /**
     * Find all unused recovery codes for a user.
     */
    List<RecoveryCode> findByUserAndUsedFalse(User user);
    
    /**
     * Count unused recovery codes for a user.
     */
    long countByUserAndUsedFalse(User user);
    
    /**
     * Delete all recovery codes for a user.
     */
    void deleteByUser(User user);
    
    /**
     * Find all recovery codes for a user.
     */
    List<RecoveryCode> findByUser(User user);
}
