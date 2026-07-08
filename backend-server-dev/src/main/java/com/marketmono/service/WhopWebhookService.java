package com.marketmono.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.BillingWebhookEvent;
import com.marketmono.entity.PaymentHistory;
import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.WhopCheckoutAttemptStatus;
import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.BillingWebhookEventRepository;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.WhopCheckoutAttemptRepository;
import com.marketmono.repository.WhopMembershipCorrelationRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies and syncs Whop webhook deliveries into the local billing model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhopWebhookService {

    private static final String WHOP_PAYMENT_KEY_PREFIX = "whop:";
    private static final String DEFAULT_CURRENCY = "USD";

    private final WhopWebhookVerifier verifier;
    private final ObjectMapper objectMapper;
    private final BillingWebhookEventRepository billingWebhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final UserRepository userRepository;
    private final WhopCheckoutAttemptRepository whopCheckoutAttemptRepository;
    private final WhopMembershipCorrelationRepository whopMembershipCorrelationRepository;
    private final SubscriberCapService subscriberCapService;
    private final BetaWhopCheckoutService betaWhopCheckoutService;

    @Transactional
    public void handleWebhook(
            String webhookId,
            String webhookTimestamp,
            String webhookSignature,
            String payload) {
        verifier.verify(webhookId, webhookTimestamp, webhookSignature, payload);

        JsonNode root = parsePayload(payload);
        String eventType = resolveEventType(root);
        if (eventType == null) {
            log.info("Received verified Whop webhook {} with no event type", webhookId);
            return;
        }

        BillingWebhookEvent ledgerEvent = startLedgerEvent(webhookId, eventType, payload);
        if (ledgerEvent == null) {
            log.debug("Ignoring duplicate Whop webhook {} ({})", webhookId, eventType);
            return;
        }

        try {
            JsonNode data = eventData(root);
            WebhookProcessingResult result = dispatch(eventType, data, root, ledgerEvent);
            if (result.needsReview()) {
                ledgerEvent.markNeedsReview(result.message());
            } else {
                ledgerEvent.markProcessed();
            }
            billingWebhookEventRepository.save(ledgerEvent);
            log.info("Processed Whop webhook {} ({})", webhookId, eventType);
        } catch (RuntimeException e) {
            ledgerEvent.markFailed(e.getMessage());
            billingWebhookEventRepository.save(ledgerEvent);
            throw e;
        }
    }

    private BillingWebhookEvent startLedgerEvent(String webhookId, String eventType, String payload) {
        Optional<BillingWebhookEvent> existing = billingWebhookEventRepository
            .findByBillingProviderAndExternalEventId(BillingProvider.WHOP, webhookId);
        if (existing.isPresent() && !existing.get().isFailed()) {
            return null;
        }

        BillingWebhookEvent ledgerEvent = existing.orElseGet(() -> BillingWebhookEvent.builder()
            .billingProvider(BillingProvider.WHOP)
            .externalEventId(webhookId)
            .build());
        ledgerEvent.setEventType(eventType);
        ledgerEvent.setProcessingStatus(BillingWebhookEvent.STATUS_RECEIVED);
        ledgerEvent.setPayloadSha256(sha256(payload == null ? "" : payload));
        ledgerEvent.setErrorMessage(null);

        try {
            return billingWebhookEventRepository.save(ledgerEvent);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    private WebhookProcessingResult dispatch(
            String eventType,
            JsonNode data,
            JsonNode root,
            BillingWebhookEvent ledgerEvent) {
        return switch (eventType) {
            case "membership_activated" -> handleMembershipActivated(data, root, ledgerEvent);
            case "membership_deactivated" -> {
                handleMembershipDeactivated(eventType, data);
                yield WebhookProcessingResult.processed();
            }
            case "membership_cancel_at_period_end", "membership_cancel_at_period_end_changed" ->
                {
                    handleMembershipCancelAtPeriodEnd(data);
                    yield WebhookProcessingResult.processed();
                }
            case "payment_succeeded", "invoice_paid" -> {
                handlePayment(data, PaymentStatus.PAID);
                yield WebhookProcessingResult.processed();
            }
            case "payment_failed" -> {
                handlePaymentFailure(data);
                yield WebhookProcessingResult.processed();
            }
            case "payment_pending" -> {
                handlePayment(data, PaymentStatus.PENDING);
                yield WebhookProcessingResult.processed();
            }
            case "invoice_past_due", "invoice_marked_uncollectible" -> {
                markSubscriptionPastDue(data, eventType);
                yield WebhookProcessingResult.processed();
            }
            case "invoice_voided" -> {
                handleMembershipDeactivated(eventType, data);
                yield WebhookProcessingResult.processed();
            }
            case "refund_created", "refund_updated" -> {
                handlePayment(data, PaymentStatus.REFUNDED);
                yield WebhookProcessingResult.processed();
            }
            case "dispute_created", "dispute_updated" -> {
                markSubscriptionPastDue(data, eventType);
                yield WebhookProcessingResult.processed();
            }
            default -> {
                log.info("Ignoring unsupported Whop webhook event type {}", eventType);
                yield WebhookProcessingResult.processed();
            }
        };
    }

    private WebhookProcessingResult handleMembershipActivated(
            JsonNode data,
            JsonNode root,
            BillingWebhookEvent ledgerEvent) {
        String membershipId = membershipId(data);
        if (membershipId == null) {
            log.warn("Ignoring Whop membership activation without membership id");
            return WebhookProcessingResult.processed();
        }

        Optional<Subscription> existingSubscription = subscriptionRepository
            .findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, membershipId);
        Optional<WhopCheckoutAttempt> checkoutAttempt = checkoutAttempt(data);
        Optional<Product> resolvedProduct = checkoutAttempt
            .flatMap(this::productFromAttempt)
            .or(() -> product(data));
        Product product = resolvedProduct
            .or(() -> existingSubscription.map(Subscription::getProduct))
            .orElse(null);
        if (product == null) {
            String message = "Whop product/plan mapping was not found";
            recordMembershipCorrelation(
                data,
                root,
                ledgerEvent,
                product,
                null,
                existingSubscription.orElse(null),
                WhopMembershipCorrelationStatus.UNMAPPED_PRODUCT,
                message
            );
            log.warn("Ignoring Whop membership {} because {}", membershipId, message);
            return WebhookProcessingResult.needsReview(message);
        }

        String planId = planId(data);
        WebhookProcessingResult productPolicy = validateActivationProduct(
            product,
            checkoutAttempt.isPresent(),
            existingSubscription.isPresent(),
            planId
        );
        if (productPolicy.needsReview()) {
            recordMembershipCorrelation(
                data,
                root,
                ledgerEvent,
                product,
                null,
                existingSubscription.orElse(null),
                productPolicy.status(),
                productPolicy.message()
            );
            log.warn("Ignoring Whop membership {} because {}", membershipId, productPolicy.message());
            return productPolicy;
        }

        String buyerEmail = buyerEmail(data, root);
        WebhookProcessingResult buyerIdentityPolicy = validateCheckoutAttemptBuyerIdentity(
            checkoutAttempt,
            buyerEmail,
            data,
            root,
            ledgerEvent,
            product,
            existingSubscription.orElse(null)
        );
        if (buyerIdentityPolicy.needsReview()) {
            log.warn("Ignoring Whop membership {} because {}", membershipId, buyerIdentityPolicy.message());
            return buyerIdentityPolicy;
        }

        Optional<User> user = checkoutAttempt.map(WhopCheckoutAttempt::getUser).or(() -> user(data, root));
        if (user.isEmpty()) {
            String message = buyerEmail == null
                ? "Whop membership did not include buyer email"
                : "No MarketMono user matched Whop membership email";
            recordMembershipCorrelation(
                data,
                root,
                ledgerEvent,
                product,
                null,
                existingSubscription.orElse(null),
                buyerEmail == null
                    ? WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE
                    : WhopMembershipCorrelationStatus.PENDING_NO_USER,
                message
            );
            log.warn("Ignoring Whop membership {} because {}", membershipId, message);
            return betaWhopCheckoutService.isDirectLinkOnlyProduct(product)
                ? WebhookProcessingResult.needsReview(message)
                : WebhookProcessingResult.processed();
        }
        if (betaWhopCheckoutService.isDirectLinkOnlyProduct(product)
            && checkoutAttempt.isEmpty()
            && !betaWhopCheckoutService.canGrantDirectLinkOnlyPurchase(user.get())) {
            String message = "Direct-link Whop product user did not match active Beta invite state";
            recordMembershipCorrelation(
                data,
                root,
                ledgerEvent,
                product,
                user.get(),
                existingSubscription.orElse(null),
                WhopMembershipCorrelationStatus.UNAUTHORIZED_EMAIL,
                message
            );
            log.warn("Ignoring Whop membership {} because {}", membershipId, message);
            return WebhookProcessingResult.needsReview(message);
        }

        Subscription subscription = existingSubscription.orElseGet(Subscription::new);
        String productId = productId(data);
        LocalDateTime periodStart = firstDate(data, "current_period_start", "period_start", "renewal_period_start");
        LocalDateTime periodEnd = firstDate(data, "current_period_end", "period_end", "renewal_period_end", "expires_at");

        subscription.setBillingProvider(BillingProvider.WHOP);
        subscription.setProviderSubscriptionId(membershipId);
        subscription.setProviderCustomerId(firstText(data, "member.id", "member_id", "customer.id", "customer_id"));
        subscription.setProviderUserId(firstText(data, "user.id", "user_id"));
        subscription.setProviderProductId(productId);
        subscription.setProviderPlanId(planId);
        subscription.setProviderStatus(firstText(data, "status"));
        subscription.setProviderManageUrl(firstText(data, "manage_url", "manageUrl", "membership_manage_url"));
        subscription.setProviderMetadata(metadataJson(data));
        subscription.setUser(user.get());
        subscription.setProduct(product);
        subscription.setStripeMode(StripeMode.DEFAULT);
        subscription.setStatus(membershipStatus(data));
        subscription.setBillingPeriod(resolveBillingPeriod(planId, product, subscription.getBillingPeriod()));
        subscription.setCurrency(currency(data));
        subscription.setPriceAtSubscription(amount(data).orElseGet(() -> priceForPlan(product, planId)));
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setNextBillingDate(periodEnd);
        subscription.setTrialEnd(firstDate(data, "trial_end", "trialEndsAt"));
        subscription.setCancelAtPeriodEnd(firstBoolean(data, "cancel_at_period_end", "cancelAtPeriodEnd").orElse(false));
        subscription.setCanceledAt(null);
        subscription.setCancellationReason(null);

        subscriptionRepository.save(subscription);
        if (betaWhopCheckoutService.isDirectLinkOnlyProduct(product) && checkoutAttempt.isEmpty()) {
            recordMembershipCorrelation(
                data,
                root,
                ledgerEvent,
                product,
                user.get(),
                subscription,
                WhopMembershipCorrelationStatus.MATCHED,
                null
            );
        }
        checkoutAttempt.ifPresent(attempt -> completeCheckoutAttempt(attempt, membershipId));
        return WebhookProcessingResult.processed();
    }

    private WebhookProcessingResult validateCheckoutAttemptBuyerIdentity(
            Optional<WhopCheckoutAttempt> checkoutAttempt,
            String buyerEmail,
            JsonNode data,
            JsonNode root,
            BillingWebhookEvent ledgerEvent,
            Product product,
            Subscription existingSubscription) {
        if (checkoutAttempt.isEmpty()) {
            return WebhookProcessingResult.processed();
        }

        WhopCheckoutAttempt attempt = checkoutAttempt.get();
        User attemptUser = attempt.getUser();
        String expectedEmail = attemptUser == null ? null : canonicalEmail(attemptUser);
        if (buyerEmail != null) {
            if (expectedEmail != null && !normalizedEmailsMatch(expectedEmail, buyerEmail)) {
                String message = "buyer-email-mismatch: expected MarketMono checkout email "
                    + expectedEmail
                    + " but Whop buyer email was "
                    + buyerEmail.trim();
                recordMembershipCorrelation(
                    data,
                    root,
                    ledgerEvent,
                    product,
                    attemptUser,
                    existingSubscription,
                    WhopMembershipCorrelationStatus.BUYER_EMAIL_MISMATCH,
                    message
                );
                return WebhookProcessingResult.needsReview(
                    WhopMembershipCorrelationStatus.BUYER_EMAIL_MISMATCH,
                    message
                );
            }
            return WebhookProcessingResult.processed();
        }

        if (!attempt.isBuyerIdentityLocked()) {
            String message = "Whop membership did not include buyer email and checkout attempt was not identity locked";
            recordMembershipCorrelation(
                data,
                root,
                ledgerEvent,
                product,
                attemptUser,
                existingSubscription,
                WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE,
                message
            );
            return WebhookProcessingResult.needsReview(
                WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE,
                message
            );
        }

        return WebhookProcessingResult.processed();
    }

    private WebhookProcessingResult validateActivationProduct(
            Product product,
            boolean hasCheckoutAttempt,
            boolean hasExistingSubscription,
            String planId) {
        if (hasExistingSubscription) {
            return WebhookProcessingResult.processed();
        }
        if (!isActiveProduct(product)) {
            return WebhookProcessingResult.needsReview(
                WhopMembershipCorrelationStatus.REJECTED_PRODUCT,
                "Whop product is inactive or archived"
            );
        }

        ProductVisibility visibility = product.getVisibility() != null
            ? product.getVisibility()
            : ProductVisibility.PUBLIC;
        if (visibility == ProductVisibility.HIDDEN) {
            return WebhookProcessingResult.needsReview(
                WhopMembershipCorrelationStatus.REJECTED_PRODUCT,
                "Hidden Whop product cannot grant access"
            );
        }
        if (visibility == ProductVisibility.DIRECT_LINK_ONLY) {
            if (!productHasWhopPlan(product, planId)) {
                return WebhookProcessingResult.needsReview(
                    WhopMembershipCorrelationStatus.UNMAPPED_PLAN,
                    "Direct-link Whop product did not include a mapped plan"
                );
            }
            return WebhookProcessingResult.processed();
        }
        if (!hasCheckoutAttempt) {
            return WebhookProcessingResult.needsReview(
                WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT,
                "Public Whop membership did not originate from a MarketMono checkout attempt"
            );
        }
        return WebhookProcessingResult.processed();
    }

    private void handleMembershipDeactivated(String eventType, JsonNode data) {
        membership(data).ifPresentOrElse(subscription -> {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setCanceledAt(LocalDateTime.now());
            subscription.setCancelAtPeriodEnd(false);
            subscription.setCancellationReason(firstText(
                data,
                "deactivation_reason",
                "cancellation_reason",
                "reason"
            ));
            if (subscription.getCancellationReason() == null) {
                subscription.setCancellationReason(eventType);
            }
            subscriptionRepository.save(subscription);
        }, () -> {
            markUnmatchedMembershipRevoked(data, eventType);
            log.warn("Ignoring Whop {} event for unknown membership {}", eventType, membershipId(data));
        });
    }

    private void handleMembershipCancelAtPeriodEnd(JsonNode data) {
        membership(data).ifPresentOrElse(subscription -> {
            subscription.setCancelAtPeriodEnd(firstBoolean(data, "cancel_at_period_end", "cancelAtPeriodEnd").orElse(true));
            subscriptionRepository.save(subscription);
        }, () -> log.warn("Ignoring Whop cancel-at-period-end event for unknown membership {}", membershipId(data)));
    }

    private void handlePaymentFailure(JsonNode data) {
        handlePayment(data, PaymentStatus.FAILED);
        markSubscriptionPastDue(data, "payment_failed");
    }

    private void markSubscriptionPastDue(JsonNode data, String eventType) {
        membership(data).ifPresentOrElse(subscription -> {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            subscription.setProviderStatus(eventType);
            subscriptionRepository.save(subscription);
        }, () -> log.warn("Ignoring Whop {} event for unknown membership {}", eventType, membershipId(data)));
    }

    private void handlePayment(JsonNode data, PaymentStatus status) {
        String paymentId = firstText(data, "id", "payment.id", "payment_id", "invoice.id", "invoice_id");
        if (paymentId == null) {
            log.warn("Ignoring Whop payment event without payment id");
            return;
        }
        if (paymentHistoryRepository.existsByBillingProviderAndProviderPaymentId(BillingProvider.WHOP, paymentId)) {
            return;
        }

        Optional<Subscription> subscription = membership(data);
        if (subscription.isEmpty()) {
            log.warn("Ignoring Whop payment {} because membership {} is unknown", paymentId, membershipId(data));
            return;
        }

        User user = subscription.get().getUser();
        if (user == null) {
            user = user(data, data).orElse(null);
        }
        if (user == null) {
            log.warn("Ignoring Whop payment {} because user could not be resolved", paymentId);
            return;
        }

        LocalDateTime paidAt = status == PaymentStatus.PAID
            ? firstDate(data, "paid_at", "paidAt", "created_at", "createdAt")
            : null;
        LocalDateTime periodStart = firstDate(data, "period_start", "current_period_start");
        LocalDateTime periodEnd = firstDate(data, "period_end", "current_period_end");

        PaymentHistory paymentHistory = PaymentHistory.builder()
            .stripeInvoiceId(WHOP_PAYMENT_KEY_PREFIX + paymentId)
            .user(user)
            .subscription(subscription.get())
            .billingProvider(BillingProvider.WHOP)
            .providerPaymentId(paymentId)
            .providerSubscriptionId(membershipId(data))
            .providerCustomerId(firstText(data, "member.id", "member_id", "customer.id", "customer_id"))
            .providerStatus(status.getValue())
            .providerMetadata(metadataJson(data))
            .amount(amount(data).orElse(BigDecimal.ZERO))
            .currency(currency(data))
            .status(status)
            .paidAt(paidAt)
            .periodStart(periodStart != null ? periodStart : fallbackPeriodStart(subscription.get()))
            .periodEnd(periodEnd != null ? periodEnd : fallbackPeriodEnd(subscription.get()))
            .stripeMode(StripeMode.DEFAULT)
            .build();

        paymentHistoryRepository.save(paymentHistory);
    }

    private Optional<Subscription> membership(JsonNode data) {
        String membershipId = membershipId(data);
        if (membershipId == null) {
            return Optional.empty();
        }
        return subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, membershipId);
    }

    private Optional<User> user(JsonNode data, JsonNode root) {
        String email = buyerEmail(data, root);
        return email == null ? Optional.empty() : userRepository.findByEmail(email);
    }

    private String buyerEmail(JsonNode data, JsonNode root) {
        String email = firstText(data, "user.email", "email", "member.email", "customer.email", "user_email");
        if (email == null && root != data) {
            email = firstText(root, "user.email", "email", "member.email", "customer.email", "user_email");
        }
        return email;
    }

    private boolean normalizedEmailsMatch(String expectedEmail, String actualEmail) {
        String expected = normalizeEmail(expectedEmail);
        String actual = normalizeEmail(actualEmail);
        return expected != null && expected.equals(actual);
    }

    private String canonicalEmail(User user) {
        if (user == null) {
            return null;
        }
        String email = user.getEmailLowercase();
        if (email == null || email.isBlank()) {
            email = user.getEmail();
        }
        return normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Optional<Product> product(JsonNode data) {
        String planId = planId(data);
        if (planId != null) {
            Optional<Product> byPlan = productRepository.findByWhopPlanId(planId);
            if (byPlan.isPresent()) {
                return byPlan;
            }
        }

        String productId = productId(data);
        return productId == null ? Optional.empty() : productRepository.findByWhopProductId(productId);
    }

    private Optional<WhopCheckoutAttempt> checkoutAttempt(JsonNode data) {
        String rawAttemptId = firstText(
            data,
            "metadata.marketmono_checkout_attempt_id",
            "metadata.checkout_attempt_id"
        );
        if (rawAttemptId == null) {
            return Optional.empty();
        }

        UUID attemptId;
        try {
            attemptId = UUID.fromString(rawAttemptId);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring malformed Whop checkout attempt id {}", rawAttemptId);
            return Optional.empty();
        }

        Optional<WhopCheckoutAttempt> attempt =
            whopCheckoutAttemptRepository.findByIdAndStatus(attemptId, WhopCheckoutAttemptStatus.CREATED);
        if (attempt.isEmpty()) {
            log.warn("Ignoring Whop checkout attempt {} because it is not in CREATED state", attemptId);
            return Optional.empty();
        }

        WhopCheckoutAttempt value = attempt.get();
        if (value.getExpiresAt() != null && value.isExpired(LocalDateTime.now())) {
            value.setStatus(WhopCheckoutAttemptStatus.EXPIRED);
            whopCheckoutAttemptRepository.save(value);
            log.warn("Ignoring expired Whop checkout attempt {}", attemptId);
            return Optional.empty();
        }

        String planId = planId(data);
        if (planId != null && value.getWhopPlanId() != null && !planId.equals(value.getWhopPlanId())) {
            log.warn("Ignoring Whop checkout attempt {} because plan {} does not match payload plan {}",
                attemptId,
                value.getWhopPlanId(),
                planId);
            return Optional.empty();
        }
        return attempt;
    }

    private Optional<Product> productFromAttempt(WhopCheckoutAttempt attempt) {
        if (attempt == null || attempt.getProductPrice() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(attempt.getProductPrice().getProduct());
    }

    private void completeCheckoutAttempt(WhopCheckoutAttempt attempt, String membershipId) {
        attempt.markCompleted(membershipId);
        whopCheckoutAttemptRepository.save(attempt);
        if (attempt.getCapReservation() != null) {
            subscriberCapService.consumeWhopReservation(attempt.getCapReservation(), membershipId);
        }
    }

    private void markUnmatchedMembershipRevoked(JsonNode data, String eventType) {
        String membershipId = membershipId(data);
        if (membershipId == null) {
            return;
        }

        whopMembershipCorrelationRepository.findByWhopMembershipId(membershipId)
            .filter(correlation -> correlation.getSubscription() == null)
            .filter(correlation -> correlation.getStatus() == WhopMembershipCorrelationStatus.PENDING_NO_USER
                || correlation.getStatus() == WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE)
            .ifPresent(correlation -> {
                correlation.setStatus(WhopMembershipCorrelationStatus.REVOKED);
                correlation.setReviewReason(truncate(eventType, 500));
                whopMembershipCorrelationRepository.save(correlation);
            });
    }

    private void recordMembershipCorrelation(
            JsonNode data,
            JsonNode root,
            BillingWebhookEvent ledgerEvent,
            Product product,
            User user,
            Subscription subscription,
            WhopMembershipCorrelationStatus status,
            String reason) {
        String membershipId = membershipId(data);
        if (membershipId == null || status == null) {
            return;
        }

        Optional<WhopMembershipCorrelation> existing =
            whopMembershipCorrelationRepository.findByWhopMembershipId(membershipId);
        WhopMembershipCorrelation correlation = existing.orElseGet(WhopMembershipCorrelation::new);
        correlation.setWhopMembershipId(membershipId);
        correlation.setWhopUserId(firstText(data, "user.id", "user_id"));
        correlation.setBuyerEmail(buyerEmail(data, root));
        correlation.setWhopProductId(productId(data));
        correlation.setWhopPlanId(planId(data));
        correlation.setProduct(product);
        correlation.setUser(user);
        correlation.setSubscription(subscription);
        correlation.setStatus(status);
        correlation.setReviewReason(truncate(reason, 500));
        correlation.setLastWebhookEventId(ledgerEvent.getExternalEventId());
        correlation.setLastPayloadSha256(ledgerEvent.getPayloadSha256());
        whopMembershipCorrelationRepository.save(correlation);
    }

    private SubscriptionStatus membershipStatus(JsonNode data) {
        String status = firstText(data, "status");
        if (status == null) {
            return SubscriptionStatus.ACTIVE;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "trialing", "trial" -> SubscriptionStatus.TRIALING;
            case "past_due", "failed", "unpaid" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "cancelled", "expired", "inactive" -> SubscriptionStatus.CANCELED;
            case "paused" -> SubscriptionStatus.PAUSED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private BillingPeriod resolveBillingPeriod(String planId, Product product, BillingPeriod fallback) {
        if (product != null && planId != null && product.getPrices() != null) {
            for (ProductPrice price : product.getPrices()) {
                if (planId.equals(price.getWhopPlanId()) && price.getBillingPeriod() != null) {
                    try {
                        return BillingPeriod.valueOf(price.getBillingPeriod().name());
                    } catch (IllegalArgumentException e) {
                        return fallback != null ? fallback : BillingPeriod.MONTHLY;
                    }
                }
            }
        }
        return fallback != null ? fallback : BillingPeriod.MONTHLY;
    }

    private BigDecimal priceForPlan(Product product, String planId) {
        if (product == null || product.getPrices() == null) {
            return null;
        }
        for (ProductPrice price : product.getPrices()) {
            if ((planId == null || planId.equals(price.getWhopPlanId()))
                && Boolean.TRUE.equals(price.getIsActive())
                && price.getPriceCents() != null) {
                return BigDecimal.valueOf(price.getPriceCents()).movePointLeft(2);
            }
        }
        return null;
    }

    private boolean productHasWhopPlan(Product product, String planId) {
        if (product == null || product.getPrices() == null || planId == null || planId.isBlank()) {
            return false;
        }
        return product.getPrices().stream()
            .anyMatch(price -> Boolean.TRUE.equals(price.getIsActive()) && planId.equals(price.getWhopPlanId()));
    }

    private boolean isActiveProduct(Product product) {
        return product != null
            && Boolean.TRUE.equals(product.getIsActive())
            && !Boolean.TRUE.equals(product.getIsArchived());
    }

    private String membershipId(JsonNode data) {
        return firstText(data, "membership.id", "membership_id", "membershipId", "membership", "id");
    }

    private String productId(JsonNode data) {
        return firstText(data, "product.id", "product_id", "productId", "product");
    }

    private String planId(JsonNode data) {
        return firstText(data, "plan.id", "plan_id", "planId", "plan");
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Invalid Whop webhook payload");
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Whop webhook payload", e);
        }
    }

    private String resolveEventType(JsonNode root) {
        String rawType = firstText(root, "type", "event_type", "eventType", "event");
        if (rawType == null) {
            return null;
        }
        String normalized = rawType.trim()
            .toLowerCase(Locale.ROOT)
            .replace('.', '_')
            .replace('-', '_');
        while (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private JsonNode eventData(JsonNode root) {
        for (String field : new String[]{"data", "resource", "object"}) {
            JsonNode value = root.path(field);
            if (value.isObject()) {
                JsonNode nestedObject = value.path("object");
                return nestedObject.isObject() ? nestedObject : value;
            }
        }
        return root;
    }

    private String firstText(JsonNode root, String... paths) {
        JsonNode value = firstValue(root, paths);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.isTextual() ? value.asText() : value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private Optional<Boolean> firstBoolean(JsonNode root, String... paths) {
        JsonNode value = firstValue(root, paths);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (value.isBoolean()) {
            return Optional.of(value.asBoolean());
        }
        if (value.isTextual()) {
            return Optional.of(Boolean.parseBoolean(value.asText()));
        }
        return Optional.empty();
    }

    private LocalDateTime firstDate(JsonNode root, String... paths) {
        JsonNode value = firstValue(root, paths);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long epoch = value.asLong();
            return epoch > 9_999_999_999L
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        String text = value.asText();
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    private Optional<BigDecimal> amount(JsonNode root) {
        JsonNode centsValue = firstValue(root, "amount_cents", "total_cents", "subtotal_cents");
        if (centsValue != null && !centsValue.isMissingNode() && !centsValue.isNull()) {
            return decimal(centsValue).map(value -> value.movePointLeft(2));
        }

        return decimal(firstValue(root, "amount", "total", "subtotal"))
            .map(value -> value.scale() == 0 ? value.movePointLeft(2) : value)
            .map(value -> value.setScale(2, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> decimal(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        try {
            if (value.isNumber()) {
                return Optional.of(value.decimalValue());
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Optional.of(new BigDecimal(value.asText()));
            }
            return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String currency(JsonNode root) {
        String currency = firstText(root, "currency");
        return currency == null ? DEFAULT_CURRENCY : currency.toUpperCase(Locale.ROOT);
    }

    private LocalDateTime fallbackPeriodStart(Subscription subscription) {
        return subscription.getCurrentPeriodStart() != null
            ? subscription.getCurrentPeriodStart()
            : LocalDateTime.now();
    }

    private LocalDateTime fallbackPeriodEnd(Subscription subscription) {
        return subscription.getCurrentPeriodEnd() != null
            ? subscription.getCurrentPeriodEnd()
            : fallbackPeriodStart(subscription).plusMonths(1);
    }

    private JsonNode firstValue(JsonNode root, String... paths) {
        if (root == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode value = valueAt(root, path);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode valueAt(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.path(segment);
        }
        return current;
    }

    private String metadataJson(JsonNode data) {
        JsonNode metadata = data.path("metadata");
        if (!metadata.isObject()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash Whop webhook payload", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record WebhookProcessingResult(
        boolean needsReview,
        WhopMembershipCorrelationStatus status,
        String message
    ) {

        private static WebhookProcessingResult processed() {
            return new WebhookProcessingResult(false, null, null);
        }

        private static WebhookProcessingResult needsReview(String message) {
            return needsReview(null, message);
        }

        private static WebhookProcessingResult needsReview(
                WhopMembershipCorrelationStatus status,
                String message) {
            return new WebhookProcessingResult(true, status, message);
        }
    }
}
