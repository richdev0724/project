package com.marketmono.controller;

import com.marketmono.dto.*;
import com.marketmono.service.PaymentHistoryService;
import com.marketmono.service.SubscriptionFeatureService;
import com.marketmono.service.SubscriptionService;
import com.marketmono.util.PrincipalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Subscription operations.
 * 
 * User endpoints (require authentication):
 * - GET /api/v1/subscriptions/me - Current user's active subscription
 * - GET /api/v1/subscriptions/me/history - User's subscription history
 * - POST /api/v1/subscriptions - Create new subscription
 * - POST /api/v1/subscriptions/upgrade - Upgrade to higher tier
 * - POST /api/v1/subscriptions/downgrade - Downgrade to lower tier (at period end)
 * - POST /api/v1/subscriptions/cancel - Cancel subscription
 * - POST /api/v1/subscriptions/reactivate - Reactivate scheduled cancellation
 * 
 * Admin endpoints:
 * - GET /api/v1/admin/subscriptions - All subscriptions
 * - GET /api/v1/admin/subscriptions/{id} - Subscription by ID
 * - GET /api/v1/admin/subscriptions/user/{userId} - User's subscriptions
 *
 * Internal admin bridge endpoints (shared-secret auth from backend-admin):
 * - POST /api/v1/admin/stripe/subscriptions/user/{userId}/upgrade
 * - POST /api/v1/admin/stripe/subscriptions/user/{userId}/downgrade
 * - POST /api/v1/admin/stripe/subscriptions/user/{userId}/cancel
 * - POST /api/v1/admin/stripe/subscriptions/user/{userId}/reactivate
 * - POST /api/v1/admin/stripe/subscriptions/user/{userId}/grant
 * - POST /api/v1/admin/stripe/subscriptions/user/{userId}/revoke
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PaymentHistoryService paymentHistoryService;
    
    // ============================================
    // User Endpoints
    // ============================================
    
    /**
     * Get current user's active subscription.
     * GET /api/v1/subscriptions/me
     */
    @GetMapping("/api/v1/subscriptions/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSubscriptionDto> getMySubscription(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.debug("GET /api/v1/subscriptions/me - userId: {}", userId);
        
        UserSubscriptionDto subscription = subscriptionService.getUserActiveSubscription(userId);
        return ResponseEntity.ok(subscription);
    }
    
    /**
     * Get current user's subscription history.
     * GET /api/v1/subscriptions/me/history
     */
    @GetMapping("/api/v1/subscriptions/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SubscriptionDto>> getMySubscriptionHistory(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.debug("GET /api/v1/subscriptions/me/history - userId: {}", userId);
        
        List<SubscriptionDto> history = subscriptionService.getUserSubscriptionHistory(userId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * Check if current user has an active subscription with detailed status.
     * GET /api/v1/subscriptions/me/status
     */
    @GetMapping("/api/v1/subscriptions/me/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionStatusResponse> getMySubscriptionStatus(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.debug("GET /api/v1/subscriptions/me/status - userId: {}", userId);
        
        boolean hasActive = subscriptionService.hasActiveSubscription(userId);
        UserSubscriptionDto subscription = subscriptionService.getUserActiveSubscription(userId);
        SubscriptionFeatureAccessDto featureAccess = subscriptionFeatureService.getFeatureAccess(subscription);
        
        String status = subscription.status() != null ? subscription.status().name() : null;
        Integer gracePeriodDaysRemaining = subscription.gracePeriodDaysRemaining();
        
        return ResponseEntity.ok(new SubscriptionStatusResponse(
            hasActive,
            status,
            subscription.hasAccess(),
            gracePeriodDaysRemaining,
            featureAccess
        ));
    }

    /**
     * Get current user's payment history.
     * GET /api/v1/subscriptions/me/payments
     */
    @GetMapping("/api/v1/subscriptions/me/payments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaymentHistoryDto>> getMyPaymentHistory(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.debug("GET /api/v1/subscriptions/me/payments - userId: {}", userId);

        return ResponseEntity.ok(paymentHistoryService.getUserPaymentHistory(userId));
    }
    
    /**
     * Create a new subscription.
     * POST /api/v1/subscriptions
     */
    @PostMapping("/api/v1/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDto> createSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.info("POST /api/v1/subscriptions - userId: {}, productId: {}", userId, request.productId());
        
        SubscriptionDto created = subscriptionService.createSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Upgrade subscription to a higher tier.
     * POST /api/v1/subscriptions/upgrade
     */
    @PostMapping("/api/v1/subscriptions/upgrade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDto> upgradeSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpgradeSubscriptionRequest request) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.info("POST /api/v1/subscriptions/upgrade - userId: {}, newProductId: {}", userId, request.newProductId());
        
        SubscriptionDto upgraded = subscriptionService.upgradeSubscription(userId, request);
        return ResponseEntity.ok(upgraded);
    }
    
    /**
     * Downgrade subscription to a lower tier (scheduled for period end).
     * POST /api/v1/subscriptions/downgrade
     */
    @PostMapping("/api/v1/subscriptions/downgrade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDto> downgradeSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DowngradeSubscriptionRequest request) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.info("POST /api/v1/subscriptions/downgrade - userId: {}, newProductId: {}", userId, request.newProductId());
        
        SubscriptionDto downgraded = subscriptionService.downgradeSubscription(userId, request);
        return ResponseEntity.ok(downgraded);
    }
    
    /**
     * Cancel subscription.
     * POST /api/v1/subscriptions/cancel
     */
    @PostMapping("/api/v1/subscriptions/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDto> cancelSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CancelSubscriptionRequest request) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.info("POST /api/v1/subscriptions/cancel - userId: {}, immediately: {}", userId, request.cancelImmediately());
        
        SubscriptionDto cancelled = subscriptionService.cancelSubscription(userId, request);
        return ResponseEntity.ok(cancelled);
    }
    
    /**
     * Reactivate a subscription scheduled for cancellation.
     * POST /api/v1/subscriptions/reactivate
     */
    @PostMapping("/api/v1/subscriptions/reactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDto> reactivateSubscription(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        log.info("POST /api/v1/subscriptions/reactivate - userId: {}", userId);
        
        SubscriptionDto reactivated = subscriptionService.reactivateSubscription(userId);
        return ResponseEntity.ok(reactivated);
    }
    
    // ============================================
    // Admin Endpoints
    // ============================================
    
    /**
     * Get all subscriptions (admin view).
     * GET /api/v1/admin/subscriptions
     */
    @GetMapping("/api/v1/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubscriptionDto>> getAllSubscriptions() {
        log.debug("GET /api/v1/admin/subscriptions");
        List<SubscriptionDto> subscriptions = subscriptionService.getAllSubscriptions();
        return ResponseEntity.ok(subscriptions);
    }
    
    /**
     * Get subscription by ID (admin view).
     * GET /api/v1/admin/subscriptions/{id}
     */
    @GetMapping("/api/v1/admin/subscriptions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> getSubscriptionById(@PathVariable UUID id) {
        log.debug("GET /api/v1/admin/subscriptions/{}", id);
        SubscriptionDto subscription = subscriptionService.getSubscriptionById(id);
        return ResponseEntity.ok(subscription);
    }
    
    /**
     * Get subscription history for a specific user (admin view).
     * GET /api/v1/admin/subscriptions/user/{userId}
     */
    @GetMapping("/api/v1/admin/subscriptions/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubscriptionDto>> getUserSubscriptions(@PathVariable UUID userId) {
        log.debug("GET /api/v1/admin/subscriptions/user/{}", userId);
        List<SubscriptionDto> subscriptions = subscriptionService.getUserSubscriptionHistory(userId);
        return ResponseEntity.ok(subscriptions);
    }
    
    /**
     * Get active subscription for a specific user (admin view).
     * GET /api/v1/admin/subscriptions/user/{userId}/active
     */
    @GetMapping("/api/v1/admin/subscriptions/user/{userId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSubscriptionDto> getUserActiveSubscription(@PathVariable UUID userId) {
        log.debug("GET /api/v1/admin/subscriptions/user/{}/active", userId);
        UserSubscriptionDto subscription = subscriptionService.getUserActiveSubscription(userId);
        return ResponseEntity.ok(subscription);
    }

    /**
     * Upgrade a specific user's subscription via the trusted backend-admin bridge.
     * POST /api/v1/admin/stripe/subscriptions/user/{userId}/upgrade
     */
    @PostMapping("/api/v1/admin/stripe/subscriptions/user/{userId}/upgrade")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> adminUpgradeSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody UpgradeSubscriptionRequest request) {
        UUID adminId = PrincipalUtils.requireInternalAdminActorId(userDetails);
        log.info(
            "POST /api/v1/admin/stripe/subscriptions/user/{}/upgrade - adminId: {}, newProductId: {}",
            userId,
            adminId,
            request.newProductId()
        );

        SubscriptionDto upgraded = subscriptionService.upgradeSubscription(userId, request);
        return ResponseEntity.ok(upgraded);
    }

    /**
     * Downgrade a specific user's subscription via the trusted backend-admin bridge.
     * POST /api/v1/admin/stripe/subscriptions/user/{userId}/downgrade
     */
    @PostMapping("/api/v1/admin/stripe/subscriptions/user/{userId}/downgrade")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> adminDowngradeSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody DowngradeSubscriptionRequest request) {
        UUID adminId = PrincipalUtils.requireInternalAdminActorId(userDetails);
        log.info(
            "POST /api/v1/admin/stripe/subscriptions/user/{}/downgrade - adminId: {}, newProductId: {}",
            userId,
            adminId,
            request.newProductId()
        );

        SubscriptionDto downgraded = subscriptionService.downgradeSubscription(userId, request);
        return ResponseEntity.ok(downgraded);
    }

    /**
     * Cancel a specific user's subscription via the trusted backend-admin bridge.
     * POST /api/v1/admin/stripe/subscriptions/user/{userId}/cancel
     */
    @PostMapping("/api/v1/admin/stripe/subscriptions/user/{userId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> adminCancelSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody CancelSubscriptionRequest request) {
        UUID adminId = PrincipalUtils.requireInternalAdminActorId(userDetails);
        log.info(
            "POST /api/v1/admin/stripe/subscriptions/user/{}/cancel - adminId: {}, immediately: {}",
            userId,
            adminId,
            request.cancelImmediately()
        );

        SubscriptionDto cancelled = subscriptionService.cancelSubscription(userId, request);
        return ResponseEntity.ok(cancelled);
    }

    /**
     * Reactivate a specific user's subscription via the trusted backend-admin bridge.
     * POST /api/v1/admin/stripe/subscriptions/user/{userId}/reactivate
     */
    @PostMapping("/api/v1/admin/stripe/subscriptions/user/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> adminReactivateSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        UUID adminId = PrincipalUtils.requireInternalAdminActorId(userDetails);
        log.info(
            "POST /api/v1/admin/stripe/subscriptions/user/{}/reactivate - adminId: {}",
            userId,
            adminId
        );

        SubscriptionDto reactivated = subscriptionService.reactivateSubscription(userId);
        return ResponseEntity.ok(reactivated);
    }

    /**
     * Grant a specific user's subscription access via the trusted backend-admin bridge.
     * POST /api/v1/admin/stripe/subscriptions/user/{userId}/grant
     */
    @PostMapping("/api/v1/admin/stripe/subscriptions/user/{userId}/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> adminGrantSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminGrantSubscriptionRequest request) {
        UUID adminId = PrincipalUtils.requireInternalAdminActorId(userDetails);
        log.info(
            "POST /api/v1/admin/stripe/subscriptions/user/{}/grant - adminId: {}, productId: {}",
            userId,
            adminId,
            request.productId()
        );

        SubscriptionDto granted = subscriptionService.grantSubscription(userId, request);
        return ResponseEntity.ok(granted);
    }

    /**
     * Revoke a specific user's subscription access via the trusted backend-admin bridge.
     * POST /api/v1/admin/stripe/subscriptions/user/{userId}/revoke
     */
    @PostMapping("/api/v1/admin/stripe/subscriptions/user/{userId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDto> adminRevokeSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminRevokeSubscriptionRequest request) {
        UUID adminId = PrincipalUtils.requireInternalAdminActorId(userDetails);
        log.info(
            "POST /api/v1/admin/stripe/subscriptions/user/{}/revoke - adminId: {}",
            userId,
            adminId
        );

        SubscriptionDto revoked = subscriptionService.revokeSubscription(userId, request);
        return ResponseEntity.ok(revoked);
    }
    
    // ============================================
    // Response DTOs
    // ============================================
    
    /**
     * Subscription status response with detailed state information.
     */
    public record SubscriptionStatusResponse(
        boolean hasActiveSubscription,
        String status,                    // "ACTIVE", "TRIALING", "PAST_DUE", "INCOMPLETE", "PAUSED", "CANCELED", or null
        boolean hasAccess,                // Whether user currently has access (considering grace period)
        Integer gracePeriodDaysRemaining, // Days remaining in grace period for PAST_DUE (null if not applicable)
        SubscriptionFeatureAccessDto featureAccess
    ) {}
    
    // ============================================
    // Exception Handlers
    // ============================================
    
    @ExceptionHandler(SubscriptionService.SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionService.SubscriptionNotFoundException ex) {
        log.warn("Subscription not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("SUBSCRIPTION_NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(SubscriptionService.UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(SubscriptionService.UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(SubscriptionService.ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(SubscriptionService.ProductNotFoundException ex) {
        log.warn("Product not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(SubscriptionService.ProductNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleProductNotAvailable(SubscriptionService.ProductNotAvailableException ex) {
        log.warn("Product not available: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("PRODUCT_NOT_AVAILABLE", ex.getMessage()));
    }
    
    @ExceptionHandler(SubscriptionService.ExistingSubscriptionException.class)
    public ResponseEntity<ErrorResponse> handleExistingSubscription(SubscriptionService.ExistingSubscriptionException ex) {
        log.warn("Existing subscription: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("EXISTING_SUBSCRIPTION", ex.getMessage()));
    }
    
    @ExceptionHandler(SubscriptionService.InvalidUpgradeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUpgrade(SubscriptionService.InvalidUpgradeException ex) {
        log.warn("Invalid upgrade: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_UPGRADE", ex.getMessage()));
    }
    
    @ExceptionHandler(SubscriptionService.InvalidDowngradeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDowngrade(SubscriptionService.InvalidDowngradeException ex) {
        log.warn("Invalid downgrade: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_DOWNGRADE", ex.getMessage()));
    }

    @ExceptionHandler(SubscriptionService.InvalidSubscriptionActionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubscriptionAction(
            SubscriptionService.InvalidSubscriptionActionException ex) {
        log.warn("Invalid subscription action: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_SUBSCRIPTION_ACTION", ex.getMessage()));
    }
    
    /**
     * Standard error response format.
     */
    public record ErrorResponse(String code, String message) {}
}
