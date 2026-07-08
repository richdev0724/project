package com.marketmono.service;

import com.marketmono.client.WhopCheckoutClient;
import com.marketmono.client.WhopCheckoutConfigurationRequest;
import com.marketmono.client.WhopCheckoutConfigurationResponse;
import com.marketmono.config.WhopCheckoutProperties;
import com.marketmono.dto.UserSubscriptionDto;
import com.marketmono.dto.WhopCheckoutPendingResponse;
import com.marketmono.dto.WhopCheckoutSessionResponse;
import com.marketmono.entity.CheckoutProviderOverride;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.WhopCheckoutAttemptStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.WhopCheckoutException;
import com.marketmono.exception.WhopCheckoutPendingException;
import com.marketmono.repository.ProductPriceRepository;
import com.marketmono.repository.WhopCheckoutAttemptRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhopCheckoutService {

    private static final String ATTEMPT_METADATA_KEY = "marketmono_checkout_attempt_id";
    private static final String USER_METADATA_KEY = "marketmono_user_id";
    private static final String PRODUCT_PRICE_METADATA_KEY = "marketmono_product_price_id";
    private static final String WHOP_PLAN_METADATA_KEY = "marketmono_whop_plan_id";
    public static final String HOSTED_URL_EMAIL_HIDDEN_LOCK_METHOD = "HOSTED_URL_EMAIL_HIDDEN";
    private static final int ACTIVATION_GRACE_MINUTES = 15;

    private final UserRepository userRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SubscriberCapService subscriberCapService;
    private final WhopCheckoutAttemptRepository whopCheckoutAttemptRepository;
    private final WhopCheckoutClient whopCheckoutClient;
    private final WhopCheckoutProperties properties;
    private final BetaWhopCheckoutService betaWhopCheckoutService;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public WhopCheckoutPendingResponse getPendingCheckout(UUID userId) {
        LocalDateTime now = LocalDateTime.now();

        Optional<WhopCheckoutAttempt> createdAttempt = whopCheckoutAttemptRepository
            .findFirstByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                userId,
                WhopCheckoutAttemptStatus.CREATED,
                now
            );
        if (createdAttempt.isPresent()) {
            WhopCheckoutAttempt attempt = createdAttempt.get();
            return WhopCheckoutPendingResponse.created(
                attempt.getId(),
                attempt.getLockedBuyerEmail(),
                attempt.getWhopPurchaseUrl(),
                attempt.getCreatedAt(),
                attempt.getExpiresAt()
            );
        }

        UserSubscriptionDto subscription = subscriptionService.getUserActiveSubscription(userId);
        if (Boolean.TRUE.equals(subscription.hasAccess())) {
            return WhopCheckoutPendingResponse.none();
        }

        LocalDateTime activationSince = now.minusMinutes(ACTIVATION_GRACE_MINUTES);
        Optional<WhopCheckoutAttempt> recentCompleted = whopCheckoutAttemptRepository
            .findFirstByUser_IdAndStatusAndCompletedAtAfterOrderByCompletedAtDesc(
                userId,
                WhopCheckoutAttemptStatus.COMPLETED,
                activationSince
            );
        if (recentCompleted.isPresent()) {
            WhopCheckoutAttempt attempt = recentCompleted.get();
            return WhopCheckoutPendingResponse.activating(
                attempt.getId(),
                attempt.getLockedBuyerEmail(),
                attempt.getCreatedAt(),
                attempt.getCompletedAt()
            );
        }

        return WhopCheckoutPendingResponse.none();
    }

    private void assertNoPendingCheckout(UUID userId) {
        WhopCheckoutPendingResponse pending = getPendingCheckout(userId);
        if (pending.hasPendingCheckout()
            && WhopCheckoutPendingResponse.STATE_CREATED.equals(pending.state())) {
            throw new WhopCheckoutPendingException(pending);
        }
    }

    @Transactional
    public WhopCheckoutSessionResponse createCheckoutSession(
            UUID userId,
            UUID productPriceId,
            String successUrl,
            String cancelUrl) {
        User user = user(userId);
        CheckoutProviderOverride provider = user.getCheckoutProviderOverride();
        if (provider == null) {
            provider = CheckoutProviderOverride.DEFAULT;
        }
        if (provider != CheckoutProviderOverride.WHOP && provider != CheckoutProviderOverride.DEFAULT) {
            throw new IllegalStateException("Whop checkout is not enabled for this user");
        }

        ProductPrice price = productPriceRepository.findActiveByIdWithProduct(productPriceId)
            .orElseThrow(() -> new IllegalArgumentException("Product price is not available: " + productPriceId));
        String whopPlanId = price.getWhopPlanId();
        if (whopPlanId == null || whopPlanId.isBlank()) {
            throw new IllegalArgumentException("Product price is not configured for Whop checkout: " + productPriceId);
        }
        if (price.getProduct() == null || price.getProduct().getVisibility() != ProductVisibility.PUBLIC) {
            throw new IllegalArgumentException("Product price is not available for public Whop checkout: " + productPriceId);
        }

        return createWhopCheckoutSession(user, price, productPriceId, successUrl, cancelUrl);
    }

    @Transactional
    public WhopCheckoutSessionResponse createBetaCheckoutSession(
            UUID userId,
            String promoCode,
            String successUrl,
            String cancelUrl) {
        ProductPrice price = betaWhopCheckoutService.betaCheckoutPriceForPromo(userId, promoCode);
        if (price.getId() == null) {
            throw new IllegalStateException("Beta checkout price is not configured");
        }
        return createWhopCheckoutSession(user(userId), price, price.getId(), successUrl, cancelUrl);
    }

    private WhopCheckoutSessionResponse createWhopCheckoutSession(
            User user,
            ProductPrice price,
            UUID productPriceId,
            String successUrl,
            String cancelUrl) {
        if (!properties.isConfigured()) {
            throw new WhopCheckoutException("Whop checkout is not configured");
        }

        assertNoPendingCheckout(user.getId());

        String whopPlanId = price.getWhopPlanId();
        if (whopPlanId == null || whopPlanId.isBlank()) {
            throw new IllegalArgumentException("Product price is not configured for Whop checkout: " + productPriceId);
        }

        Optional<SubscriberCapReservation> capReservation =
            subscriberCapService.reserveWhopCheckoutSlot(user, productPriceId.toString());
        WhopCheckoutAttempt attempt = whopCheckoutAttemptRepository.save(
            WhopCheckoutAttempt.pending(user, price, whopPlanId, capReservation.orElse(null))
        );

        try {
            WhopCheckoutConfigurationResponse configuration = whopCheckoutClient.createCheckoutConfiguration(
                new WhopCheckoutConfigurationRequest(
                    properties.getCompanyId(),
                    whopPlanId,
                    metadata(user, productPriceId, whopPlanId, attempt, capReservation),
                    firstText(successUrl, properties.getRedirectUrl()),
                    firstText(cancelUrl, properties.getSourceUrl()),
                    properties.isAllowPromoCodes()
                )
            );
            String buyerEmail = checkoutBuyerEmail(user);
            String lockedPurchaseUrl = withBuyerEmailLock(configuration.purchaseUrl(), buyerEmail);
            attempt.markCreated(
                configuration.id(),
                lockedPurchaseUrl,
                buyerEmail,
                HOSTED_URL_EMAIL_HIDDEN_LOCK_METHOD
            );
            whopCheckoutAttemptRepository.save(attempt);
            capReservation.ifPresent(reservation ->
                subscriberCapService.markWhopCheckoutCreated(reservation, configuration.id()));
            log.info("Created Whop checkout attempt {} for user {} price {}",
                attempt.getId(),
                user.getEmail(),
                productPriceId);
            return new WhopCheckoutSessionResponse(
                lockedPurchaseUrl,
                attempt.getId(),
                configuration.id(),
                whopPlanId,
                buyerEmail,
                HOSTED_URL_EMAIL_HIDDEN_LOCK_METHOD
            );
        } catch (RuntimeException e) {
            attempt.markFailed(e.getMessage());
            whopCheckoutAttemptRepository.save(attempt);
            capReservation.ifPresent(subscriberCapService::releaseReservation);
            throw e;
        }
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private Map<String, String> metadata(
            User user,
            UUID productPriceId,
            String whopPlanId,
            WhopCheckoutAttempt attempt,
            Optional<SubscriberCapReservation> capReservation) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(USER_METADATA_KEY, user.getId().toString());
        metadata.put("marketmono_user_email", user.getEmail());
        metadata.put(PRODUCT_PRICE_METADATA_KEY, productPriceId.toString());
        metadata.put(WHOP_PLAN_METADATA_KEY, whopPlanId);
        metadata.put(ATTEMPT_METADATA_KEY, attempt.getId().toString());
        capReservation.ifPresent(reservation ->
            metadata.put(SubscriberCapService.RESERVATION_METADATA_KEY, reservation.getId().toString()));
        return metadata;
    }

    private String firstText(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    private String checkoutBuyerEmail(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("MarketMono user email is required for Whop checkout");
        }
        return user.getEmail().trim();
    }

    private String withBuyerEmailLock(String purchaseUrl, String buyerEmail) {
        if (purchaseUrl == null || purchaseUrl.isBlank()) {
            throw new WhopCheckoutException("Whop checkout purchase URL was not returned");
        }
        String separator = purchaseUrl.contains("?") ? "&" : "?";
        String encodedEmail = URLEncoder.encode(buyerEmail, StandardCharsets.UTF_8);
        return purchaseUrl
            + separator
            + "email="
            + encodedEmail
            + "&email.disabled=1&email.hidden=1";
    }
}
