package com.marketmono.service;

import com.marketmono.entity.CheckoutProviderOverride;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.StripeBillingException;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating Stripe Checkout sessions.
 * Checkout sessions are used for new subscription purchases and one-time payments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeCheckoutService {

    private static final String DEFAULT_BETA_PROMO_CODE = "BETAMM2026";
    private static final int DEFAULT_BETA_TRIAL_DAYS = 3;

    private final UserRepository userRepository;
    private final StripeConfigService stripeConfigService;
    private final StripeCustomerService stripeCustomerService;
    private final SubscriberCapService subscriberCapService;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Create a Stripe Checkout session for subscription purchase.
     *
     * @param userId User ID
     * @param priceId Stripe price ID for the subscription
     * @param successUrl Optional URL to return to after successful payment (defaults to /dashboard)
     * @param cancelUrl Optional URL to return to if payment is cancelled (defaults to /subscribe)
     * @return Checkout session URL to redirect user to
     */
    public String createCheckoutSession(UUID userId, String priceId, String successUrl, String cancelUrl) {
        return createCheckoutSession(userId, priceId, successUrl, cancelUrl, null);
    }

    public String createCheckoutSession(
            UUID userId,
            String priceId,
            String successUrl,
            String cancelUrl,
            String promoCode) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        requireStripeCheckoutEnabled(user);

        // Determine which mode to use and ensure customer exists
        StripeMode mode = resolveEffectiveMode(user);
        Optional<SubscriberCapReservation> capReservation =
            subscriberCapService.reserveCheckoutSlot(user, mode, priceId);

        try {
            String customerId = stripeCustomerService.ensureCustomerExists(user);

            if (customerId == null || customerId.isBlank()) {
                throw new IllegalStateException("Failed to get or create Stripe customer for user: " + user.getEmail());
            }

            // Build request options with API key scoped to this request (thread-safe)
            String secretKey = stripeConfigService.getSecretKey(mode);
            RequestOptions requestOptions = RequestOptions.builder().setApiKey(secretKey).build();

            // Use provided URLs or defaults
            // Note: Main dashboard is at "/" not "/dashboard"
            String effectiveSuccessUrl = (successUrl != null && !successUrl.isBlank())
                ? successUrl
                : frontendUrl + "/?session_id={CHECKOUT_SESSION_ID}";

            String effectiveCancelUrl = (cancelUrl != null && !cancelUrl.isBlank())
                ? cancelUrl
                : frontendUrl + "/subscribe";

            BetaCheckoutBenefit betaBenefit = resolveBetaCheckoutBenefit(user, promoCode);
            SessionCreateParams.SubscriptionData.Builder subscriptionDataBuilder =
                SessionCreateParams.SubscriptionData.builder();
            boolean hasSubscriptionData = false;
            if (betaBenefit.applies()) {
                subscriptionDataBuilder
                    .setTrialPeriodDays((long) betaBenefit.trialDays())
                    .putMetadata("user_id", userId.toString())
                    .putMetadata("mode", mode.name())
                    .putMetadata("beta_user", "true")
                    .putMetadata("beta_promo_code", betaBenefit.publicPromoCode());
                hasSubscriptionData = true;
            }

            // Create checkout session with Stripe
            SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setSuccessUrl(effectiveSuccessUrl)
                .setCancelUrl(effectiveCancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("user_id", userId.toString())
                .putMetadata("mode", mode.name());

            if (betaBenefit.applies()) {
                sessionBuilder
                    .addDiscount(SessionCreateParams.Discount.builder()
                        .setPromotionCode(betaBenefit.promotionCodeId())
                        .build())
                    .putMetadata("beta_user", "true")
                    .putMetadata("beta_promo_code", betaBenefit.publicPromoCode());
            } else {
                sessionBuilder.setAllowPromotionCodes(false);
            }

            final boolean[] subscriptionDataPresent = {hasSubscriptionData};
            capReservation.ifPresent(reservation -> {
                String reservationId = reservation.getId().toString();
                sessionBuilder.putMetadata(SubscriberCapService.RESERVATION_METADATA_KEY, reservationId);
                subscriptionDataBuilder.putMetadata(SubscriberCapService.RESERVATION_METADATA_KEY, reservationId);
                subscriptionDataPresent[0] = true;
            });

            if (subscriptionDataPresent[0]) {
                sessionBuilder.setSubscriptionData(subscriptionDataBuilder.build());
            }

            SessionCreateParams params = sessionBuilder.build();

            Session checkoutSession = Session.create(params, requestOptions);
            capReservation.ifPresent(reservation ->
            subscriberCapService.markStripeSessionCreated(reservation, checkoutSession.getId()));
            
            log.info("Created checkout session for user {} (customer: {}, price: {})", 
                user.getEmail(), customerId, priceId);

            return checkoutSession.getUrl();

        } catch (StripeException e) {
            capReservation.ifPresent(subscriberCapService::releaseReservation);
            log.error("Failed to create checkout session for user {}: {}", 
                user.getEmail(), e.getMessage());
            throw new StripeBillingException("Failed to create Stripe checkout session", e);
        } catch (RuntimeException e) {
            capReservation.ifPresent(subscriberCapService::releaseReservation);
            throw e;
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

    private void requireStripeCheckoutEnabled(User user) {
        if (user.getCheckoutProviderOverride() != CheckoutProviderOverride.STRIPE) {
            throw new StripeCheckoutNotEnabledException(
                "Stripe checkout is not enabled for this user. Default checkout is Whop."
            );
        }
    }

    private BetaCheckoutBenefit resolveBetaCheckoutBenefit(User user, String requestedPromoCode) {
        String publicPromoCode = stripeConfigService.getValue(StripeConfig.BETA_PROMO_CODE, DEFAULT_BETA_PROMO_CODE);
        boolean noVisibleCode = requestedPromoCode == null || requestedPromoCode.isBlank();
        boolean requestedBetaCode = !noVisibleCode && publicPromoCode.equalsIgnoreCase(requestedPromoCode.trim());
        boolean betaEligible = Boolean.TRUE.equals(user.getBetaUser()) || requestedBetaCode;
        if (!betaEligible) {
            return BetaCheckoutBenefit.notApplied();
        }

        if (!noVisibleCode && !requestedBetaCode) {
            return BetaCheckoutBenefit.notApplied();
        }

        String promotionCodeId = stripeConfigService.getValue(StripeConfig.BETA_PROMOTION_CODE_ID)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException("Beta Stripe promotion code ID is not configured"));
        int trialDays = stripeConfigService.getValueAsInt(StripeConfig.BETA_TRIAL_DAYS, DEFAULT_BETA_TRIAL_DAYS);
        return new BetaCheckoutBenefit(true, publicPromoCode, promotionCodeId, trialDays);
    }

    private record BetaCheckoutBenefit(
        boolean applies,
        String publicPromoCode,
        String promotionCodeId,
        int trialDays
    ) {
        private static BetaCheckoutBenefit notApplied() {
            return new BetaCheckoutBenefit(false, null, null, 0);
        }
    }

    public static class StripeCheckoutNotEnabledException extends IllegalStateException {
        public StripeCheckoutNotEnabledException(String message) {
            super(message);
        }
    }
}
