package com.marketmono.controller;

import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.StripePortalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Stripe Customer Portal session management.
 * Allows users to manage their subscriptions, payment methods, and billing history
 * through Stripe's hosted portal interface.
 */
@RestController
@RequestMapping("/api/v1/stripe/portal")
@RequiredArgsConstructor
@Slf4j
public class StripePortalController {

    private final StripePortalService portalService;
    private final UserRepository userRepository;

    /**
     * Create a Stripe Customer Portal session for the authenticated user.
     * Returns a URL that the frontend should redirect the user to.
     *
     * @param authentication Current authenticated user
     * @param returnUrl URL to return to after portal session (optional)
     * @return Portal session URL
     */
    @PostMapping("/create-session")
    public ResponseEntity<Map<String, String>> createPortalSession(
            Authentication authentication,
            @RequestParam(required = false) String returnUrl) {

        log.info("Creating portal session for user: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("User not found: " + authentication.getName()));

        String portalUrl;
        try {
            portalUrl = portalService.createPortalSession(user.getId(), returnUrl);
        } catch (StripePortalService.StripePortalNotAvailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "stripe_portal_not_available",
                "message", e.getMessage()
            ));
        }

        return ResponseEntity.ok(Map.of(
            "url", portalUrl,
            "message", "Redirect user to this URL to access Stripe Customer Portal"
        ));
    }

    /**
     * Health check endpoint for portal service.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Stripe Customer Portal"
        ));
    }
}
