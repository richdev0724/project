package com.marketmono.controller;

import com.marketmono.entity.auth.User;
import com.marketmono.exception.SubscriberCapReachedException;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.StripeCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Stripe Checkout session management.
 * Checkout sessions are used for new subscription purchases (not for managing existing subscriptions).
 * For managing existing subscriptions, use StripePortalController instead.
 */
@RestController
@RequestMapping("/api/v1/stripe/checkout")
@RequiredArgsConstructor
@Slf4j
public class StripeCheckoutController {

    private final StripeCheckoutService checkoutService;
    private final UserRepository userRepository;

    /**
     * Create a Stripe Checkout session for subscription purchase.
     * Returns a URL that the frontend should redirect the user to.
     *
     * @param authentication Current authenticated user
     * @param priceId Stripe price ID for the subscription product
     * @param successUrl Optional URL to return to after payment (defaults to /dashboard)
     * @param cancelUrl Optional URL to return to if cancelled (defaults to /subscribe)
     * @return Checkout session URL
     */
    @PostMapping("/create-session")
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            Authentication authentication,
            @RequestParam String priceId,
            @RequestParam(required = false) String successUrl,
            @RequestParam(required = false) String cancelUrl,
            @RequestParam(required = false) String promoCode) {
        
        log.info("POST /api/v1/stripe/checkout/create-session - priceId: {}", priceId);
        
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        try {
            String sessionUrl = checkoutService.createCheckoutSession(
                user.getId(), 
                priceId, 
                successUrl, 
                cancelUrl,
                promoCode
            );
            
            return ResponseEntity.ok(Map.of("url", sessionUrl));
            
        } catch (StripeCheckoutService.StripeCheckoutNotEnabledException e) {
            log.info("Stripe checkout blocked for user {}: {}", userEmail, e.getMessage());
            return ResponseEntity.status(403)
                .body(Map.of(
                    "error", "stripe_checkout_not_enabled",
                    "message", "Stripe checkout is available only for legacy Stripe support"
                ));
        } catch (SubscriberCapReachedException e) {
            log.info("Checkout blocked by paid subscriber cap for user {}: limit={}, paid={}, pending={}",
                userEmail,
                e.capLimit(),
                e.paidSubscriberCount(),
                e.pendingReservationCount());
            return ResponseEntity.status(409)
                .body(Map.of(
                    "error", "paid_subscriber_cap_reached",
                    "message", "The paid subscriber cap has been reached"
                ));
        } catch (Exception e) {
            log.error("Failed to create checkout session for user {}: {}", userEmail, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to create checkout session"));
        }
    }

    /**
     * Health check endpoint for checkout service.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
