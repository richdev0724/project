package com.marketmono.controller;

import com.marketmono.dto.WhopCheckoutPendingResponse;
import com.marketmono.dto.WhopCheckoutSessionResponse;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.SubscriberCapReachedException;
import com.marketmono.exception.WhopCheckoutPendingException;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.WhopCheckoutService;
import com.marketmono.util.PrincipalUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whop/checkout")
@RequiredArgsConstructor
@Slf4j
public class WhopCheckoutController {

    private final WhopCheckoutService whopCheckoutService;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<WhopCheckoutPendingResponse> getPendingCheckout(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        return ResponseEntity.ok(whopCheckoutService.getPendingCheckout(userId));
    }

    @PostMapping("/create-session")
    public ResponseEntity<?> createCheckoutSession(
            Authentication authentication,
            @RequestParam UUID productPriceId,
            @RequestParam(required = false) String successUrl,
            @RequestParam(required = false) String cancelUrl) {
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        try {
            WhopCheckoutSessionResponse response = whopCheckoutService.createCheckoutSession(
                user.getId(),
                productPriceId,
                successUrl,
                cancelUrl
            );
            return ResponseEntity.ok(response);
        } catch (SubscriberCapReachedException e) {
            log.info("Whop checkout blocked by paid subscriber cap for user {}: limit={}, paid={}, pending={}",
                userEmail,
                e.capLimit(),
                e.paidSubscriberCount(),
                e.pendingReservationCount());
            return ResponseEntity.status(409)
                .body(Map.of(
                    "error", "paid_subscriber_cap_reached",
                    "message", "The paid subscriber cap has been reached"
                ));
        } catch (WhopCheckoutPendingException e) {
            return ResponseEntity.status(409)
                .body(Map.of(
                    "error", "whop_checkout_pending",
                    "message", e.getMessage(),
                    "pending", e.getPending()
                ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Failed to create Whop checkout session for user {}: {}", userEmail, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to create Whop checkout session"));
        }
    }

    @PostMapping("/legacy-beta")
    public ResponseEntity<?> legacyBetaCheckout(
            Authentication authentication,
            @RequestParam(required = false) String successUrl,
            @RequestParam(required = false) String cancelUrl,
            @RequestParam String promoCode) {
        return createBetaCheckoutSession(authentication, promoCode, successUrl, cancelUrl);
    }

    @PostMapping("/beta/create-session")
    public ResponseEntity<?> createBetaCheckoutSession(
            Authentication authentication,
            @RequestParam String promoCode,
            @RequestParam(required = false) String successUrl,
            @RequestParam(required = false) String cancelUrl) {
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        try {
            return ResponseEntity.ok(whopCheckoutService.createBetaCheckoutSession(
                user.getId(),
                promoCode,
                successUrl,
                cancelUrl
            ));
        } catch (WhopCheckoutPendingException e) {
            return ResponseEntity.status(409)
                .body(Map.of(
                    "error", "whop_checkout_pending",
                    "message", e.getMessage(),
                    "pending", e.getPending()
                ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Failed to create Whop beta checkout session for user {}: {}", userEmail, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to create Whop checkout session"));
        }
    }
}
