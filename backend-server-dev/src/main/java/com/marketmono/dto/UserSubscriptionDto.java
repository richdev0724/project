package com.marketmono.dto;

import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Current user's subscription status DTO.
 * Combines subscription data with product details for dashboard display.
 */
public record UserSubscriptionDto(
    // Subscription info (null if no active subscription)
    UUID subscriptionId,
    SubscriptionStatus status,
    BillingPeriod billingPeriod,
    LocalDateTime currentPeriodEnd,
    Boolean cancelAtPeriodEnd,
    LocalDateTime trialEnd,
    BillingProvider billingProvider,
    String providerManageUrl,
    
    // Product info
    UUID productId,
    String productName,
    String productSlug,

    // Pending plan change info
    UUID pendingProductId,
    BillingPeriod pendingBillingPeriod,
    
    // Computed status
    Boolean hasActiveSubscription,
    Boolean hasAccess,
    Boolean isTrialing,
    Boolean isScheduledForCancellation,
    Long daysRemaining,
    Integer gracePeriodDaysRemaining,  // Days remaining in grace period for PAST_DUE (null if not applicable)
    
    // Prices (from product_prices table)
    List<ProductPriceDto> prices
) {
    /**
     * Create DTO for user with active subscription.
     */
    public static UserSubscriptionDto from(Subscription subscription) {
        if (subscription == null) {
            return noSubscription();
        }
        
        var product = subscription.getProduct();
        
        // Calculate grace period days remaining for PAST_DUE subscriptions
        Integer gracePeriodDaysRemaining = null;
        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE && subscription.getUpdatedAt() != null) {
            // Days since subscription went PAST_DUE
            long daysSincePastDue = java.time.Duration.between(
                subscription.getUpdatedAt(),
                LocalDateTime.now()
            ).toDays();
            // Assuming 1-day grace period (should match config, but hardcoded here for DTO simplicity)
            // For proper implementation, grace period should be passed as parameter or injected
            gracePeriodDaysRemaining = Math.max(0, 1 - (int)daysSincePastDue);
        }
        
        return new UserSubscriptionDto(
            subscription.getId(),
            subscription.getStatus(),
            subscription.getBillingPeriod(),
            subscription.getCurrentPeriodEnd(),
            subscription.getCancelAtPeriodEnd(),
            subscription.getTrialEnd(),
            subscription.getBillingProvider(),
            subscription.getProviderManageUrl(),
            product != null ? product.getId() : null,
            product != null ? product.getName() : null,
            product != null ? product.getSlug() : null,
            subscription.getPendingProductId(),
            subscription.getPendingBillingPeriod(),
            subscription.isActive(),
            subscription.hasWebsiteAccess(),
            subscription.isTrialing(),
            subscription.isScheduledForCancellation(),
            subscription.getDaysRemaining(),
            gracePeriodDaysRemaining,
            product != null ? product.getPrices().stream()
                .map(ProductPriceDto::from)
                .sorted((a, b) -> a.sortOrder().compareTo(b.sortOrder()))
                .collect(Collectors.toList()) : null
        );
    }
    
    /**
     * Create DTO for user without any subscription.
     */
    public static UserSubscriptionDto noSubscription() {
        return new UserSubscriptionDto(
            null, null, null, null, null, null, null, null,
            null, null,
            null, null, null,
            false, false, false, false, 0L, null,
            null
        );
    }
}
