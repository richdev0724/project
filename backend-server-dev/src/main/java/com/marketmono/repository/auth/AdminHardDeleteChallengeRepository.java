package com.marketmono.repository.auth;

import com.marketmono.entity.auth.AdminHardDeleteChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AdminHardDeleteChallengeRepository extends JpaRepository<AdminHardDeleteChallenge, UUID> {

    @Modifying
    @Query("DELETE FROM AdminHardDeleteChallenge c WHERE c.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") LocalDateTime now);
}
