package com.marketmono.repository;

import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.StripeMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Subscription entity operations.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    /**
     * Find subscription by Stripe subscription ID.
     */
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<Subscription> findByBillingProviderAndProviderSubscriptionId(
        BillingProvider billingProvider,
        String providerSubscriptionId
    );
    
    /**
     * Find all subscriptions for a user.
     */
    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Find active subscription for a user (should be at most one).
     * Includes PAST_DUE subscriptions - grace period check handled by service layer.
     */
    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.user.id = :userId 
        AND s.status IN ('ACTIVE', 'TRIALING', 'PAST_DUE') 
        ORDER BY s.createdAt DESC
        """)
    List<Subscription> findActiveSubscriptionsByUserId(@Param("userId") UUID userId);
    
    /**
     * Find active subscription for a user with grace period check for PAST_DUE status.
     * PAST_DUE subscriptions are only considered active if updatedAt is within grace period.
     * 
     * @param userId User ID
     * @param gracePeriodCutoff Cutoff date for PAST_DUE grace period (now - gracePeriodDays)
     * @return Active subscription if exists and within grace period
     */
    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.user.id = :userId 
        AND (
            s.status IN ('ACTIVE', 'TRIALING') 
            OR (s.status = 'PAST_DUE' AND s.updatedAt >= :gracePeriodCutoff)
        )
        ORDER BY s.createdAt DESC
        """)
    List<Subscription> findActiveSubscriptionsWithGracePeriod(
        @Param("userId") UUID userId,
        @Param("gracePeriodCutoff") LocalDateTime gracePeriodCutoff
    );
    
    /**
     * Find all active subscriptions for a product.
     */
    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.product.id = :productId 
        AND s.status IN ('ACTIVE', 'TRIALING')
        """)
    List<Subscription> findActiveSubscriptionsByProductId(@Param("productId") UUID productId);
    
    /**
     * Count active subscribers for a product.
     */
    @Query("""
        SELECT COUNT(s) FROM Subscription s 
        WHERE s.product.id = :productId 
        AND s.status IN ('ACTIVE', 'TRIALING')
        """)
    long countActiveSubscribersByProductId(@Param("productId") UUID productId);
    
    /**
     * Find subscriptions by status.
     */
    List<Subscription> findByStatusOrderByCreatedAtDesc(SubscriptionStatus status);

    /**
     * Count subscriptions by Stripe environment and lifecycle status for observability.
     */
    long countByStripeModeAndStatus(StripeMode stripeMode, SubscriptionStatus status);

    /**
     * Count subscriptions that currently allow access by Stripe environment.
     */
    @Query("""
        SELECT COUNT(s)
        FROM Subscription s
        WHERE s.stripeMode = :stripeMode
        AND s.status IN ('ACTIVE', 'TRIALING', 'PAST_DUE')
        """)
    long countAccessAllowedByStripeMode(@Param("stripeMode") StripeMode stripeMode);

    /**
     * Count real production paid subscribers for the launch cap.
     * Manual/local grants, TEST-mode subscriptions, Beta users, and admins do not consume launch slots.
     */
    @Query("""
        SELECT COUNT(DISTINCT s.user.id)
        FROM Subscription s
        WHERE s.stripeMode = 'PROD'
        AND s.stripeSubscriptionId IS NOT NULL
        AND s.stripeSubscriptionId <> ''
        AND s.status = 'ACTIVE'
        AND COALESCE(s.user.betaUser, false) = false
        AND NOT EXISTS (
            SELECT r
            FROM UserRole r
            WHERE r.user.id = s.user.id
            AND r.role = 'ADMIN'
        )
        """)
    long countRealPaidSubscribersForLaunchCap();

    @Query("""
        SELECT COUNT(s)
        FROM Subscription s
        WHERE s.user.id = :userId
        AND s.stripeMode = 'PROD'
        AND s.stripeSubscriptionId IS NOT NULL
        AND s.stripeSubscriptionId <> ''
        AND s.status = 'ACTIVE'
        AND COALESCE(s.user.betaUser, false) = false
        AND NOT EXISTS (
            SELECT r
            FROM UserRole r
            WHERE r.user.id = s.user.id
            AND r.role = 'ADMIN'
        )
        """)
    long countRealPaidSubscriberByUserId(@Param("userId") UUID userId);

    default boolean existsRealPaidSubscriberByUserId(UUID userId) {
        return countRealPaidSubscriberByUserId(userId) > 0;
    }

    /**
     * Count access-granting subscriptions scheduled for cancellation by Stripe environment.
     */
    long countByStripeModeAndCancelAtPeriodEndTrueAndStatusIn(
        StripeMode stripeMode,
        Collection<SubscriptionStatus> statuses
    );
    
    /**
     * Find subscriptions scheduled for cancellation at period end.
     */
    List<Subscription> findByCancelAtPeriodEndTrueAndStatusIn(List<SubscriptionStatus> statuses);
    
    /**
     * Find subscriptions expiring within a date range.
     */
    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.currentPeriodEnd BETWEEN :start AND :end 
        AND s.status IN ('ACTIVE', 'TRIALING')
        """)
    List<Subscription> findExpiringSubscriptions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    
    /**
     * Find subscriptions that were upgraded/downgraded from a specific product.
     */
    List<Subscription> findByPreviousProductId(UUID previousProductId);
    
    /**
     * Check if user has any active subscription.
     * Includes PAST_DUE subscriptions - grace period check handled by service layer.
     */
    @Query("""
        SELECT COUNT(s) 
        FROM Subscription s 
        WHERE s.user.id = :userId 
        AND s.status IN ('ACTIVE', 'TRIALING', 'PAST_DUE')
        """)
    long countActiveSubscriptions(@Param("userId") UUID userId);

    default boolean hasActiveSubscription(UUID userId) {
        return countActiveSubscriptions(userId) > 0;
    }
    
    /**
     * Check if user has active subscription with grace period check for PAST_DUE status.
     * 
     * @param userId User ID
     * @param gracePeriodCutoff Cutoff date for PAST_DUE grace period (now - gracePeriodDays)
     * @return true if user has active subscription within grace period
     */
    @Query("""
        SELECT COUNT(s) 
        FROM Subscription s 
        WHERE s.user.id = :userId 
        AND (
            s.status IN ('ACTIVE', 'TRIALING') 
            OR (s.status = 'PAST_DUE' AND s.updatedAt >= :gracePeriodCutoff)
        )
        """)
    long countActiveSubscriptionsWithGracePeriod(
        @Param("userId") UUID userId,
        @Param("gracePeriodCutoff") LocalDateTime gracePeriodCutoff
    );

    default boolean hasActiveSubscriptionWithGracePeriod(UUID userId, LocalDateTime gracePeriodCutoff) {
        return countActiveSubscriptionsWithGracePeriod(userId, gracePeriodCutoff) > 0;
    }

    @Query("""
        SELECT COUNT(s)
        FROM Subscription s
        WHERE s.user.id = :userId
        AND s.status IN ('ACTIVE', 'TRIALING')
        """)
    long countWebsiteAccessSubscriptions(@Param("userId") UUID userId);

    default boolean hasWebsiteAccessSubscription(UUID userId) {
        return countWebsiteAccessSubscriptions(userId) > 0;
    }
    
    /**
     * Find subscription history for a user (including canceled).
     */
    @Query("""
        SELECT s FROM Subscription s 
        LEFT JOIN FETCH s.product 
        WHERE s.user.id = :userId 
        ORDER BY s.createdAt DESC
        """)
    List<Subscription> findSubscriptionHistoryByUserId(@Param("userId") UUID userId);
    
    /**
     * Find all subscriptions with product eagerly loaded (for admin list).
     */
    @Query("""
        SELECT s FROM Subscription s 
        LEFT JOIN FETCH s.product 
        LEFT JOIN FETCH s.user 
        ORDER BY s.createdAt DESC
        """)
    List<Subscription> findAllWithDetails();
    
    /**
     * Find subscriptions by Stripe mode (for testing vs production queries).
     */
    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.stripeMode = :stripeMode 
        AND s.status IN ('ACTIVE', 'TRIALING')
        """)
    List<Subscription> findByStripeModeAndActive(@Param("stripeMode") StripeMode stripeMode);
}
