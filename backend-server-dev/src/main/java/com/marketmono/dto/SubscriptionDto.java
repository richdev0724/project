package com.marketmono.dto;

import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Full subscription DTO with all fields.
 * Used for detailed views and API responses.
 */
public record SubscriptionDto(
    UUID id,
    
    // Relationships (IDs only, fetch details separately if needed)
    UUID userId,
    UUID productId,
    String productName,  // Convenience field when product is loaded
    
    // Stripe Integration
    String stripeSubscriptionId,
    String stripeCustomerId,
    StripeMode stripeMode,

    // Provider integration
    BillingProvider billingProvider,
    String providerSubscriptionId,
    String providerCustomerId,
    String providerUserId,
    String providerProductId,
    String providerPlanId,
    String providerStatus,
    String providerManageUrl,
    
    // Status
    SubscriptionStatus status,
    BillingPeriod billingPeriod,
    
    // Period Tracking
    LocalDateTime currentPeriodStart,
    LocalDateTime currentPeriodEnd,
    LocalDateTime trialEnd,
    
    // Cancellation
    Boolean cancelAtPeriodEnd,
    LocalDateTime canceledAt,
    String cancellationReason,

    // Pending plan change
    UUID pendingProductId,
    BillingPeriod pendingBillingPeriod,
    
    // Pricing
    BigDecimal priceAtSubscription,
    String currency,
    
    // Upgrade/Downgrade tracking
    UUID previousProductId,
    LocalDateTime upgradedAt,
    LocalDateTime downgradedAt,
    
    // Audit
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    
    // Computed values
    Boolean isActive,
    Boolean hasAccess,
    Boolean isTrialing,
    Boolean isScheduledForCancellation,
    Long daysRemaining,
    Boolean wasUpgraded,
    Boolean wasDowngraded
) {
    /**
     * Create DTO from entity (without product details).
     */
    public static SubscriptionDto from(Subscription subscription) {
        return from(subscription, null);
    }
    
    /**
     * Create DTO from entity with optional product name.
     */
    public static SubscriptionDto from(Subscription subscription, String productName) {
        return new SubscriptionDto(
            subscription.getId(),
            subscription.getUser() != null ? subscription.getUser().getId() : null,
            subscription.getProduct() != null ? subscription.getProduct().getId() : null,
            productName != null ? productName : 
                (subscription.getProduct() != null ? subscription.getProduct().getName() : null),
            subscription.getStripeSubscriptionId(),
            subscription.getStripeCustomerId(),
            subscription.getStripeMode(),
            subscription.getBillingProvider(),
            subscription.getProviderSubscriptionId(),
            subscription.getProviderCustomerId(),
            subscription.getProviderUserId(),
            subscription.getProviderProductId(),
            subscription.getProviderPlanId(),
            subscription.getProviderStatus(),
            subscription.getProviderManageUrl(),
            subscription.getStatus(),
            subscription.getBillingPeriod(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            subscription.getTrialEnd(),
            subscription.getCancelAtPeriodEnd(),
            subscription.getCanceledAt(),
            subscription.getCancellationReason(),
            subscription.getPendingProductId(),
            subscription.getPendingBillingPeriod(),
            subscription.getPriceAtSubscription(),
            subscription.getCurrency(),
            subscription.getPreviousProductId(),
            subscription.getUpgradedAt(),
            subscription.getDowngradedAt(),
            subscription.getCreatedAt(),
            subscription.getUpdatedAt(),
            // Computed values
            subscription.isActive(),
            subscription.hasAccess(),
            subscription.isTrialing(),
            subscription.isScheduledForCancellation(),
            subscription.getDaysRemaining(),
            subscription.wasUpgraded(),
            subscription.wasDowngraded()
        );
    }
}
