package com.marketmono.entity;

import com.marketmono.entity.auth.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Subscription entity for tracking user product subscriptions.
 * Links users to products with billing and status information.
 */
@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscriptions_user_id", columnList = "user_id"),
    @Index(name = "idx_subscriptions_product_id", columnList = "product_id"),
    @Index(name = "idx_subscriptions_stripe_subscription_id", columnList = "stripe_subscription_id"),
    @Index(name = "idx_subscriptions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    // ============================================
    // Relationships
    // ============================================
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    // ============================================
    // Stripe Integration
    // ============================================
    
    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;
    
    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "stripe_mode", nullable = false, length = 10)
    @Builder.Default
    private StripeMode stripeMode = StripeMode.DEFAULT;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_provider", nullable = false, length = 20)
    @Builder.Default
    private BillingProvider billingProvider = BillingProvider.STRIPE;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @Column(name = "provider_customer_id", length = 255)
    private String providerCustomerId;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Column(name = "provider_product_id", length = 255)
    private String providerProductId;

    @Column(name = "provider_plan_id", length = 255)
    private String providerPlanId;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "provider_manage_url", length = 500)
    private String providerManageUrl;

    @Column(name = "provider_metadata", columnDefinition = "TEXT")
    private String providerMetadata;
    
    // ============================================
    // Subscription Status & Period
    // ============================================
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.INCOMPLETE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 10)
    @Builder.Default
    private BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
    
    // ============================================
    // Period Tracking (Hybrid: Invoice + Billing Cycle)
    // ============================================
    
    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;
    
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;
    
    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;
    
    @Column(name = "trial_end")
    private LocalDateTime trialEnd;
    
    // ============================================
    // Cancellation Tracking
    // ============================================
    
    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private Boolean cancelAtPeriodEnd = false;
    
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;
    
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "pending_product_id")
    private UUID pendingProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_billing_period", length = 20)
    private BillingPeriod pendingBillingPeriod;
    
    // ============================================
    // Pricing (at time of subscription)
    // ============================================
    
    @Column(name = "price_at_subscription", precision = 10, scale = 2)
    private BigDecimal priceAtSubscription;
    
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";
    
    // ============================================
    // Upgrade/Downgrade Tracking
    // ============================================
    
    @Column(name = "previous_product_id")
    private UUID previousProductId;
    
    @Column(name = "upgraded_at")
    private LocalDateTime upgradedAt;
    
    @Column(name = "downgraded_at")
    private LocalDateTime downgradedAt;
    
    // ============================================
    // Audit Fields
    // ============================================
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ============================================
    // Helper Methods
    // ============================================
    
    /**
     * Check if subscription is currently active and usable.
     */
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE 
            || status == SubscriptionStatus.TRIALING;
    }
    
    /**
     * Check if subscription allows access to billing-summary surfaces.
     * Website access uses {@link #hasWebsiteAccess()}.
     */
    public boolean hasAccess() {
        return status == SubscriptionStatus.ACTIVE 
            || status == SubscriptionStatus.TRIALING
            || status == SubscriptionStatus.PAST_DUE;
    }

    /**
     * Check if subscription grants website access.
     */
    public boolean hasWebsiteAccess() {
        return status == SubscriptionStatus.ACTIVE
            || status == SubscriptionStatus.TRIALING;
    }
    
    /**
     * Check if subscription is in trial period.
     */
    public boolean isTrialing() {
        return status == SubscriptionStatus.TRIALING 
            && trialEnd != null 
            && LocalDateTime.now().isBefore(trialEnd);
    }
    
    /**
     * Check if subscription is scheduled for cancellation.
     */
    public boolean isScheduledForCancellation() {
        return cancelAtPeriodEnd != null && cancelAtPeriodEnd;
    }
    
    /**
     * Check if current period is still valid.
     */
    public boolean isWithinCurrentPeriod() {
        if (currentPeriodEnd == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(currentPeriodEnd);
    }
    
    /**
     * Calculate days remaining in current period.
     */
    public long getDaysRemaining() {
        if (currentPeriodEnd == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentPeriodEnd)) {
            return 0;
        }
        return java.time.Duration.between(now, currentPeriodEnd).toDays();
    }
    
    /**
     * Check if this subscription was an upgrade from another product.
     */
    public boolean wasUpgraded() {
        return upgradedAt != null && previousProductId != null;
    }
    
    /**
     * Check if this subscription was a downgrade from another product.
     */
    public boolean wasDowngraded() {
        return downgradedAt != null && previousProductId != null;
    }
    
    /**
     * Mark subscription for cancellation at period end (for downgrades).
     */
    public void scheduleCancel(String reason) {
        this.cancelAtPeriodEnd = true;
        this.cancellationReason = reason;
    }
    
    /**
     * Cancel subscription immediately.
     */
    public void cancelImmediately(String reason) {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }
    
    /**
     * Record an upgrade from another product.
     */
    public void recordUpgrade(UUID fromProductId) {
        this.previousProductId = fromProductId;
        this.upgradedAt = LocalDateTime.now();
        this.downgradedAt = null;
        this.pendingProductId = null;
        this.pendingBillingPeriod = null;
        this.cancelAtPeriodEnd = false;
        this.cancellationReason = null;
    }
    
    /**
     * Record a scheduled downgrade to another product.
     */
    public void recordDowngrade(UUID toProductId, BillingPeriod toBillingPeriod, String reason) {
        this.previousProductId = toProductId;
        this.pendingProductId = toProductId;
        this.pendingBillingPeriod = toBillingPeriod;
        this.downgradedAt = LocalDateTime.now();
        this.cancellationReason = reason;
        this.cancelAtPeriodEnd = true;
    }
}
