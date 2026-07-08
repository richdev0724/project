package com.marketmono.service;

import com.marketmono.dto.*;
import com.marketmono.entity.*;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Subscription operations and business logic.
 * Note: Actual Stripe integration will be added when Stripe keys are available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    
    private static final String SUBSCRIPTION_NOT_FOUND = "Subscription not found with id: ";
    private static final String USER_NOT_FOUND = "User not found with id: ";
    private static final String PRODUCT_NOT_FOUND = "Product not found with id: ";
    
    private final SubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StripeConfigService stripeConfigService;
    
    // ============================================
    // Read Operations
    // ============================================
    
    /**
     * Get all subscriptions (admin use).
     */
    @Transactional(readOnly = true)
    public List<SubscriptionDto> getAllSubscriptions() {
        return subscriptionRepository.findAllWithDetails().stream()
            .map(SubscriptionDto::from)
            .toList();
    }
    
    /**
     * Get subscription by ID.
     */
    @Transactional(readOnly = true)
    public SubscriptionDto getSubscriptionById(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new SubscriptionNotFoundException(SUBSCRIPTION_NOT_FOUND + id));
        return SubscriptionDto.from(subscription);
    }
    
    /**
     * Get active subscription for a user with grace period check.
     */
    @Transactional(readOnly = true)
    public UserSubscriptionDto getUserActiveSubscription(UUID userId) {
        return findLatestActiveSubscriptionWithGracePeriod(userId)
            .map(UserSubscriptionDto::from)
            .orElse(UserSubscriptionDto.noSubscription());
    }
    
    /**
     * Get subscription history for a user.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionDto> getUserSubscriptionHistory(UUID userId) {
        return subscriptionRepository.findSubscriptionHistoryByUserId(userId).stream()
            .map(SubscriptionDto::from)
            .toList();
    }
    
    /**
     * Check if user has active subscription with grace period check.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID userId) {
        LocalDateTime gracePeriodCutoff = calculateGracePeriodCutoff();
        return subscriptionRepository.hasActiveSubscriptionWithGracePeriod(userId, gracePeriodCutoff);
    }

    @Transactional(readOnly = true)
    public boolean hasWebsiteAccess(UUID userId) {
        return subscriptionRepository.hasWebsiteAccessSubscription(userId);
    }
    
    /**
     * Calculate grace period cutoff date for PAST_DUE subscriptions.
     * Subscriptions that went PAST_DUE before this date are considered expired.
     * Grace period is configurable via admin settings.
     */
    private LocalDateTime calculateGracePeriodCutoff() {
        return LocalDateTime.now().minusDays(stripeConfigService.getPastDueGracePeriodDays());
    }
    
    // ============================================
    // Create Operations
    // ============================================
    
    /**
     * Create a new subscription for a user.
     * Note: In production, this would integrate with Stripe Checkout.
     */
    @Transactional
    public SubscriptionDto createSubscription(UUID userId, CreateSubscriptionRequest request) {
        // Verify user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND + userId));
        
        // Verify product exists and is available
        Product product = productRepository.findById(request.productId())
            .orElseThrow(() -> new ProductNotFoundException(PRODUCT_NOT_FOUND + request.productId()));
        
        if (!product.isAvailableForSubscription()) {
            throw new ProductNotAvailableException("Product is not available for subscription: " + product.getName());
        }
        
        // Check for existing active subscription
        if (subscriptionRepository.hasActiveSubscription(userId)) {
            throw new ExistingSubscriptionException("User already has an active subscription. Use upgrade/downgrade instead.");
        }
        
        // Determine price based on billing period
        int priceCents = getProductPrice(product, request.billingPeriod());
        
        // Create subscription (in INCOMPLETE status until Stripe confirms)
        Subscription subscription = Subscription.builder()
            .user(user)
            .product(product)
            .billingPeriod(request.billingPeriod())
            .status(SubscriptionStatus.INCOMPLETE)
            .priceAtSubscription(BigDecimal.valueOf(priceCents / 100.0))
            .currency("USD")
            .build();
        
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Created subscription for user {} to product {} (status: INCOMPLETE)", 
            userId, product.getName());
        
        // TODO: Integrate with Stripe Checkout to create checkout session
        // The subscription status will be updated via webhook when payment succeeds
        
        return SubscriptionDto.from(saved);
    }

    /**
     * Grant local subscription access from an internal admin action.
     * This is intentionally local-only; it does not create a Stripe subscription.
     */
    @Transactional
    public SubscriptionDto grantSubscription(UUID userId, AdminGrantSubscriptionRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND + userId));

        Product product = productRepository.findById(request.productId())
            .orElseThrow(() -> new ProductNotFoundException(PRODUCT_NOT_FOUND + request.productId()));

        if (!product.isAvailableForSubscription()) {
            throw new ProductNotAvailableException("Product is not available for subscription: " + product.getName());
        }

        Subscription subscription = findLatestActiveSubscription(userId)
            .map(existing -> {
                if (isStripeManaged(existing)) {
                    throw new ExistingSubscriptionException(
                        "User already has a Stripe-managed active subscription. Use Stripe billing actions for Stripe-backed subscriptions."
                    );
                }
                return existing;
            })
            .orElseGet(Subscription::new);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = now.plusDays(resolveGrantDurationDays(request));
        int priceCents = resolveGrantPriceCents(product, request.billingPeriod());
        StripeMode stripeMode = resolveEffectiveStripeMode(user);
        String stripeCustomerId = getCustomerIdForMode(user, stripeMode);

        subscription.setUser(user);
        subscription.setProduct(product);
        subscription.setStripeSubscriptionId(null);
        subscription.setStripeMode(stripeMode);
        if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            subscription.setStripeCustomerId(stripeCustomerId);
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingPeriod(request.billingPeriod());
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setNextBillingDate(periodEnd);
        subscription.setTrialEnd(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCanceledAt(null);
        subscription.setCancellationReason(null);
        subscription.setPendingProductId(null);
        subscription.setPendingBillingPeriod(null);
        subscription.setPreviousProductId(null);
        subscription.setUpgradedAt(null);
        subscription.setDowngradedAt(null);
        subscription.setPriceAtSubscription(BigDecimal.valueOf(priceCents).movePointLeft(2));
        subscription.setCurrency("USD");

        Subscription saved = subscriptionRepository.save(subscription);
        log.info(
            "Granted local subscription {} for user {} to product {} for {} days",
            saved.getId(),
            userId,
            product.getName(),
            resolveGrantDurationDays(request)
        );

        return SubscriptionDto.from(saved);
    }

    /**
     * Revoke local subscription access from an internal admin action.
     */
    @Transactional
    public SubscriptionDto revokeSubscription(UUID userId, AdminRevokeSubscriptionRequest request) {
        Subscription subscription = findLatestActiveSubscription(userId)
            .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));

        if (isStripeManaged(subscription)) {
            throw new InvalidSubscriptionActionException(
                "Stripe-managed subscriptions must be canceled through Stripe billing actions before local access is revoked."
            );
        }

        subscription.setPendingProductId(null);
        subscription.setPendingBillingPeriod(null);
        subscription.setDowngradedAt(null);
        subscription.setPreviousProductId(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.cancelImmediately(normalizeRevokeReason(request));

        Subscription updated = subscriptionRepository.save(subscription);
        log.info("Revoked local subscription {} for user {}", updated.getId(), userId);

        return SubscriptionDto.from(updated);
    }
    
    // ============================================
    // Upgrade/Downgrade Operations
    // ============================================
    
    /**
     * Upgrade subscription to a higher tier product.
     * Applied immediately with proration.
     */
    @Transactional
    public SubscriptionDto upgradeSubscription(UUID userId, UpgradeSubscriptionRequest request) {
        // Find current subscription
        Subscription current = findLatestActiveSubscription(userId)
            .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));
        requireLocalLifecycleMutationSupported(current);
        
        // Verify new product exists
        Product newProduct = productRepository.findById(request.newProductId())
            .orElseThrow(() -> new ProductNotFoundException(PRODUCT_NOT_FOUND + request.newProductId()));
        
        if (!newProduct.isAvailableForSubscription()) {
            throw new ProductNotAvailableException("Product is not available: " + newProduct.getName());
        }
        
        // Validate this is an upgrade (new product should cost more)
        int currentPrice = getCurrentPrice(current);
        int newPrice = getProductPrice(newProduct, 
            request.billingPeriod() != null ? request.billingPeriod() : current.getBillingPeriod());
        
        if (newPrice <= currentPrice) {
            throw new InvalidUpgradeException("New product price must be higher than current. Use downgrade for lower tier.");
        }
        
        // Record upgrade
        UUID previousProductId = current.getProduct().getId();
        current.setProduct(newProduct);
        current.recordUpgrade(previousProductId);
        
        if (request.billingPeriod() != null) {
            current.setBillingPeriod(request.billingPeriod());
        }
        
        // Update price
        current.setPriceAtSubscription(BigDecimal.valueOf(newPrice / 100.0));
        
        Subscription updated = subscriptionRepository.save(current);
        log.info("Upgraded subscription {} from product {} to {}", 
            updated.getId(), previousProductId, newProduct.getName());
        
        // TODO: Call Stripe to update subscription with proration
        
        return SubscriptionDto.from(updated);
    }
    
    /**
     * Schedule downgrade to a lower tier product (at period end).
     */
    @Transactional
    public SubscriptionDto downgradeSubscription(UUID userId, DowngradeSubscriptionRequest request) {
        // Find current subscription
        Subscription current = findLatestActiveSubscription(userId)
            .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));
        requireLocalLifecycleMutationSupported(current);
        
        // Verify new product exists
        Product newProduct = productRepository.findById(request.newProductId())
            .orElseThrow(() -> new ProductNotFoundException(PRODUCT_NOT_FOUND + request.newProductId()));
        
        if (!newProduct.isAvailableForSubscription()) {
            throw new ProductNotAvailableException("Product is not available: " + newProduct.getName());
        }

        BillingPeriod targetBillingPeriod = request.billingPeriod() != null
            ? request.billingPeriod()
            : current.getBillingPeriod();

        if (current.getProduct().getId().equals(request.newProductId())
                && current.getBillingPeriod() == targetBillingPeriod) {
            throw new InvalidDowngradeException("New product and billing period match the current subscription.");
        }
        
        // Validate this is a downgrade
        int currentPrice = getCurrentPrice(current);
        int newPrice = getProductPrice(newProduct, targetBillingPeriod);
        
        if (newPrice >= currentPrice) {
            throw new InvalidDowngradeException("New product price must be lower than current. Use upgrade for higher tier.");
        }
        
        // Schedule downgrade for period end
        current.recordDowngrade(request.newProductId(), targetBillingPeriod, request.reason());
        
        Subscription updated = subscriptionRepository.save(current);
        log.info("Scheduled downgrade for subscription {} to product {} at period end", 
            updated.getId(), newProduct.getName());
        
        // TODO: Call Stripe to schedule subscription update at period end
        
        return SubscriptionDto.from(updated);
    }
    
    // ============================================
    // Cancellation Operations
    // ============================================
    
    /**
     * Cancel subscription.
     */
    @Transactional
    public SubscriptionDto cancelSubscription(UUID userId, CancelSubscriptionRequest request) {
        Subscription subscription = findLatestActiveSubscription(userId)
            .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));
        requireLocalLifecycleMutationSupported(subscription);

        subscription.setPendingProductId(null);
        subscription.setPendingBillingPeriod(null);
        subscription.setDowngradedAt(null);
        subscription.setPreviousProductId(null);
        
        if (Boolean.TRUE.equals(request.cancelImmediately())) {
            subscription.cancelImmediately(request.reason());
            log.info("Canceled subscription {} immediately", subscription.getId());
        } else {
            subscription.scheduleCancel(request.reason());
            log.info("Scheduled cancellation for subscription {} at period end", subscription.getId());
        }
        
        Subscription updated = subscriptionRepository.save(subscription);
        
        // TODO: Call Stripe to cancel subscription
        
        return SubscriptionDto.from(updated);
    }
    
    /**
     * Reactivate a subscription scheduled for cancellation.
     */
    @Transactional
    public SubscriptionDto reactivateSubscription(UUID userId) {
        Subscription subscription = findLatestActiveSubscription(userId)
            .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));
        requireLocalLifecycleMutationSupported(subscription);
        
        if (!subscription.isScheduledForCancellation()) {
            throw new IllegalStateException("Subscription is not scheduled for cancellation");
        }
        
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancellationReason(null);
        
        // If downgrade was scheduled, clear it
        if (subscription.wasDowngraded()) {
            subscription.setDowngradedAt(null);
            subscription.setPreviousProductId(null);
            subscription.setPendingProductId(null);
            subscription.setPendingBillingPeriod(null);
        }
        
        Subscription updated = subscriptionRepository.save(subscription);
        log.info("Reactivated subscription {}", updated.getId());
        
        // TODO: Call Stripe to reactivate subscription
        
        return SubscriptionDto.from(updated);
    }
    
    // ============================================
    // Helper Methods
    // ============================================
    
    private int getCurrentPrice(Subscription subscription) {
        if (subscription.getPriceAtSubscription() != null) {
            return subscription.getPriceAtSubscription().multiply(BigDecimal.valueOf(100)).intValue();
        }
        return getProductPrice(subscription.getProduct(), subscription.getBillingPeriod());
    }

    private int resolveGrantDurationDays(AdminGrantSubscriptionRequest request) {
        if (request.durationDays() != null) {
            return request.durationDays();
        }
        return request.billingPeriod() == BillingPeriod.ANNUAL ? 365 : 30;
    }

    private StripeMode resolveEffectiveStripeMode(User user) {
        StripeMode userMode = user.getStripeMode();
        if (userMode == null || userMode == StripeMode.DEFAULT) {
            return stripeConfigService.getDefaultMode();
        }
        return userMode;
    }

    private String getCustomerIdForMode(User user, StripeMode mode) {
        return mode == StripeMode.PROD
            ? user.getStripeCustomerIdProd()
            : user.getStripeCustomerIdTest();
    }

    private String normalizeRevokeReason(AdminRevokeSubscriptionRequest request) {
        if (request.reason() != null && !request.reason().isBlank()) {
            return request.reason().trim();
        }
        return "Revoked by admin";
    }

    private boolean isStripeManaged(Subscription subscription) {
        return subscription.getStripeSubscriptionId() != null
            && !subscription.getStripeSubscriptionId().isBlank();
    }

    private void requireLocalLifecycleMutationSupported(Subscription subscription) {
        if (subscription.getBillingProvider() == BillingProvider.WHOP) {
            throw new InvalidSubscriptionActionException(
                "Whop-managed subscriptions must be managed through the Whop billing portal."
            );
        }
    }

    private Optional<Subscription> findLatestActiveSubscription(UUID userId) {
        return subscriptionRepository.findActiveSubscriptionsByUserId(userId).stream().findFirst();
    }

    private Optional<Subscription> findLatestActiveSubscriptionWithGracePeriod(UUID userId) {
        LocalDateTime gracePeriodCutoff = calculateGracePeriodCutoff();
        return subscriptionRepository.findActiveSubscriptionsWithGracePeriod(userId, gracePeriodCutoff)
            .stream()
            .findFirst();
    }

    private int requireActiveProductPrice(Product product, BillingPeriod period) {
        int priceCents = getProductPrice(product, period);
        if (priceCents <= 0) {
            throw new ProductNotAvailableException(
                "No active " + period + " price is configured for product: " + product.getName()
            );
        }
        return priceCents;
    }

    private int resolveGrantPriceCents(Product product, BillingPeriod period) {
        ProductPrice.BillingPeriod pricePeriod = switch (period) {
            case MONTHLY -> ProductPrice.BillingPeriod.MONTHLY;
            case ANNUAL -> ProductPrice.BillingPeriod.ANNUAL;
        };

        return product.getPrices().stream()
            .filter(price -> price.getBillingPeriod() == pricePeriod
                && Boolean.TRUE.equals(price.getIsActive()))
            .findFirst()
            .map(ProductPrice::getPriceCents)
            .orElseThrow(() -> new ProductNotAvailableException(
                "No active " + period + " price is configured for product: " + product.getName()
            ));
    }
    
    private int getProductPrice(Product product, BillingPeriod period) {
        // Convert entity BillingPeriod to ProductPrice.BillingPeriod
        ProductPrice.BillingPeriod pricePeriod = switch (period) {
            case MONTHLY -> ProductPrice.BillingPeriod.MONTHLY;
            case ANNUAL -> ProductPrice.BillingPeriod.ANNUAL;
        };
        
        return product.getPrices().stream()
            .filter(p -> p.getBillingPeriod() == pricePeriod && Boolean.TRUE.equals(p.getIsActive()))
            .findFirst()
            .map(ProductPrice::getPriceCents)
            .orElse(0);
    }
    
    // ============================================
    // Exception Classes
    // ============================================
    
    public static class SubscriptionNotFoundException extends RuntimeException {
        public SubscriptionNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class ProductNotAvailableException extends RuntimeException {
        public ProductNotAvailableException(String message) {
            super(message);
        }
    }
    
    public static class ExistingSubscriptionException extends RuntimeException {
        public ExistingSubscriptionException(String message) {
            super(message);
        }
    }
    
    public static class InvalidUpgradeException extends RuntimeException {
        public InvalidUpgradeException(String message) {
            super(message);
        }
    }
    
    public static class InvalidDowngradeException extends RuntimeException {
        public InvalidDowngradeException(String message) {
            super(message);
        }
    }

    public static class InvalidSubscriptionActionException extends RuntimeException {
        public InvalidSubscriptionActionException(String message) {
            super(message);
        }
    }
}
