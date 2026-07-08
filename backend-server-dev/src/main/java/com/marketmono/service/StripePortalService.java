package com.marketmono.service;

import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.StripeBillingException;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.billingportal.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for creating Stripe Customer Portal sessions.
 * The portal allows customers to manage their subscriptions, payment methods,
 * and view billing history without custom UI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePortalService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeConfigService stripeConfigService;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Create a Stripe Customer Portal session for a user.
     *
     * @param userId User ID
     * @param returnUrl Optional URL to return to after portal session (defaults to frontend home)
     * @return Portal session URL to redirect user to
     */
    public String createPortalSession(UUID userId, String returnUrl) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!hasActiveStripeBackedSubscription(userId)) {
            throw new StripePortalNotAvailableException(
                "Stripe portal requires an active Stripe subscription."
            );
        }

        // Determine which customer ID to use based on Stripe mode
        StripeMode mode = resolveEffectiveMode(user);
        String customerId = switch (mode) {
            case TEST -> user.getStripeCustomerIdTest();
            case PROD -> user.getStripeCustomerIdProd();
            default -> user.getStripeCustomerIdTest() != null ? 
                user.getStripeCustomerIdTest() : user.getStripeCustomerIdProd();
        };

        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("User has no Stripe customer ID for mode: " + mode);
        }

        // Build request options with API key scoped to this request (thread-safe)
        String secretKey = stripeConfigService.getSecretKey(mode);
        RequestOptions requestOptions = RequestOptions.builder().setApiKey(secretKey).build();

        // Use provided return URL or default to frontend home (dashboard at root)
        String effectiveReturnUrl = (returnUrl != null && !returnUrl.isBlank()) 
            ? returnUrl 
            : frontendUrl;

        try {
            // Create portal session with Stripe
            SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(effectiveReturnUrl)
                .build();

            Session portalSession = Session.create(params, requestOptions);

            log.info("Created portal session for user {} (customer: {})", 
                user.getEmail(), customerId);

            return portalSession.getUrl();

        } catch (StripeException e) {
            log.error("Failed to create portal session for user {}: {}", 
                user.getEmail(), e.getMessage());
            throw new StripeBillingException("Failed to create Stripe portal session", e);
        }
    }

    /**
     * Resolve user's effective Stripe mode.
     * DEFAULT → resolves to global default mode from stripe_config.
     */
    private StripeMode resolveEffectiveMode(User user) {
        StripeMode userMode = user.getStripeMode();
        if (userMode == null || userMode == StripeMode.DEFAULT) {
            return stripeConfigService.getDefaultMode();
        }
        return userMode;
    }

    private boolean hasActiveStripeBackedSubscription(UUID userId) {
        return subscriptionRepository.findActiveSubscriptionsByUserId(userId).stream()
            .anyMatch(this::isStripeBackedSubscription);
    }

    private boolean isStripeBackedSubscription(Subscription subscription) {
        BillingProvider billingProvider = subscription.getBillingProvider();
        return (billingProvider == null || billingProvider == BillingProvider.STRIPE)
            && subscription.getStripeSubscriptionId() != null
            && !subscription.getStripeSubscriptionId().isBlank();
    }

    public static class StripePortalNotAvailableException extends IllegalStateException {
        public StripePortalNotAvailableException(String message) {
            super(message);
        }
    }
}
