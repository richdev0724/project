package com.marketmono.repository;

import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.WhopCheckoutAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhopCheckoutAttemptRepository extends JpaRepository<WhopCheckoutAttempt, UUID> {
    Optional<WhopCheckoutAttempt> findByIdAndStatus(UUID id, WhopCheckoutAttemptStatus status);

    List<WhopCheckoutAttempt> findTop10ByLockedBuyerEmailIgnoreCaseOrderByCreatedAtDesc(String lockedBuyerEmail);

    Optional<WhopCheckoutAttempt> findFirstByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
        UUID userId,
        WhopCheckoutAttemptStatus status,
        LocalDateTime now
    );

    Optional<WhopCheckoutAttempt> findFirstByUser_IdAndStatusAndCompletedAtAfterOrderByCompletedAtDesc(
        UUID userId,
        WhopCheckoutAttemptStatus status,
        LocalDateTime since
    );

    @Query("""
        SELECT a
        FROM WhopCheckoutAttempt a
        WHERE LOWER(a.lockedBuyerEmail) = LOWER(:buyerEmail)
        AND a.whopPlanId = :whopPlanId
        AND a.status = com.marketmono.entity.WhopCheckoutAttemptStatus.CREATED
        AND a.expiresAt <= :now
        ORDER BY a.createdAt DESC
        """)
    List<WhopCheckoutAttempt> findExpiredCreatedAttemptsForReview(
        @Param("buyerEmail") String buyerEmail,
        @Param("whopPlanId") String whopPlanId,
        @Param("now") LocalDateTime now
    );
}
