package com.marketmono.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.Product;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSyncService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SubscriberCapService subscriberCapService;

    @Transactional
    public void syncSubscription(com.stripe.model.Subscription stripeSub) {
        com.marketmono.entity.Subscription subscription = subscriptionRepository
            .findByStripeSubscriptionId(stripeSub.getId())
            .orElse(new com.marketmono.entity.Subscription());

        boolean isNew = subscription.getId() == null;

        String customerId = stripeSub.getCustomer();
        User user = userRepository
            .findByStripeCustomerIdTestOrStripeCustomerIdProd(customerId, customerId)
            .orElseThrow(() -> new IllegalStateException("User not found for customer: " + customerId));

        String priceId = extractSubscriptionPriceId(stripeSub);
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalStateException("Price not found for subscription: " + stripeSub.getId());
        }

        Product product = productRepository
            .findByStripePriceId(priceId)
            .orElseThrow(() -> new IllegalStateException("Product not found for price: " + priceId));

        if (!subscriberCapService.canSyncStripeSubscription(stripeSub, user)) {
            log.warn("Skipping local subscription grant for Stripe subscription {} because paid subscriber cap is full",
                stripeSub.getId());
            return;
        }

        subscription.setUser(user);
        subscription.setProduct(product);
        subscription.setStripeSubscriptionId(stripeSub.getId());
        subscription.setStripeCustomerId(customerId);
        subscription.setStripeMode(Boolean.TRUE.equals(stripeSub.getLivemode())
            ? com.marketmono.entity.StripeMode.PROD
            : com.marketmono.entity.StripeMode.TEST);
        subscription.setStatus(mapStripeStatus(stripeSub.getStatus()));
        subscription.setCancelAtPeriodEnd(isScheduledForCancellation(stripeSub));
        subscription.setCancellationReason(extractCancellationReason(stripeSub));

        BillingPeriod resolvedBillingPeriod = resolveBillingPeriod(stripeSub, subscription.getBillingPeriod());
        subscription.setBillingPeriod(resolvedBillingPeriod);

        String currency = extractSubscriptionCurrency(stripeSub);
        if (currency != null && !currency.isBlank()) {
            subscription.setCurrency(currency);
        }

        BigDecimal unitAmount = extractSubscriptionUnitAmount(stripeSub);
        if (unitAmount != null) {
            subscription.setPriceAtSubscription(unitAmount);
        }

        LocalDateTime currentPeriodStart = extractSubscriptionPeriodBoundary(stripeSub, "current_period_start");
        LocalDateTime currentPeriodEnd = extractSubscriptionPeriodBoundary(stripeSub, "current_period_end");

        subscription.setCurrentPeriodStart(
            currentPeriodStart != null ? currentPeriodStart : toLocalDateTime(stripeSub.getStartDate())
        );
        subscription.setCurrentPeriodEnd(currentPeriodEnd);

        if (currentPeriodEnd != null) {
            subscription.setNextBillingDate(currentPeriodEnd);
        } else {
            // Calculate next billing date from billing cycle anchor and interval
            Long billingAnchor = stripeSub.getBillingCycleAnchor();
            if (billingAnchor != null) {
                String interval = extractSubscriptionInterval(stripeSub);
                Long intervalCount = extractSubscriptionIntervalCount(stripeSub);
                subscription.setNextBillingDate(calculateNextBillingDate(billingAnchor, interval, intervalCount));
            }
        }

        if (stripeSub.getTrialStart() != null) {
            // We don't persist trialStart currently; keeping startDate above.
        }
        if (stripeSub.getTrialEnd() != null) {
            subscription.setTrialEnd(toLocalDateTime(stripeSub.getTrialEnd()));
        } else {
            subscription.setTrialEnd(null);
        }
        if (stripeSub.getEndedAt() != null) {
            subscription.setCanceledAt(toLocalDateTime(stripeSub.getEndedAt()));
        } else {
            subscription.setCanceledAt(null);
        }

        subscriptionRepository.save(subscription);
        markBetaPromoUsedIfConfirmed(stripeSub, user);
        subscriberCapService.consumeReservationFromStripeSubscription(stripeSub, user);

        log.info("{} subscription {} for user {} (status={})",
            isNew ? "Created" : "Updated",
            subscription.getStripeSubscriptionId(),
            user.getEmail(),
            subscription.getStatus());
    }

    @Transactional
    public void cancelSubscription(String stripeSubscriptionId) {
        var subscriptionOpt = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
        if (subscriptionOpt.isEmpty()) {
            log.warn("Ignoring cancellation for unknown Stripe subscription {}", stripeSubscriptionId);
            return;
        }

        com.marketmono.entity.Subscription subscription = subscriptionOpt.get();

        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        log.info("Canceled subscription {}", stripeSubscriptionId);
    }

    @Transactional
    public void updateInvoiceStatus(com.stripe.model.Invoice invoice) {
        String subId = resolveSubscriptionIdFromInvoice(invoice);
        if (subId == null || subId.isBlank()) {
            log.warn("Invoice {} has no subscription line item", invoice.getId());
            return;
        }

        com.marketmono.entity.Subscription subscription = subscriptionRepository
            .findByStripeSubscriptionId(subId)
            .orElseThrow(() -> new IllegalStateException("Subscription not found: " + subId));

        InvoiceCoveragePeriod coveragePeriod = resolveInvoiceCoveragePeriod(invoice);
        if (coveragePeriod != null) {
            subscription.setCurrentPeriodStart(coveragePeriod.start());
            subscription.setCurrentPeriodEnd(coveragePeriod.end());
            subscription.setNextBillingDate(coveragePeriod.end());
        }

        String invoiceStatus = invoice.getStatus();
        if ("paid".equalsIgnoreCase(invoiceStatus)) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        } else if ("open".equalsIgnoreCase(invoiceStatus) || "uncollectible".equalsIgnoreCase(invoiceStatus)) {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
        }

        subscriptionRepository.save(subscription);

        log.info("Updated subscription {} from invoice {} (invoiceStatus={})",
            subscription.getStripeSubscriptionId(),
            invoice.getId(),
            invoiceStatus);
    }

    String resolveSubscriptionIdFromInvoice(com.stripe.model.Invoice invoice) {
        if (invoice.getLines() == null || invoice.getLines().getData() == null) {
            return extractSubscriptionIdFromInvoiceJson(invoice.getRawJsonObject());
        }

        for (com.stripe.model.InvoiceLineItem item : invoice.getLines().getData()) {
            String subId = item.getSubscription();
            if (subId != null && !subId.isBlank()) {
                return subId;
            }
        }

        return extractSubscriptionIdFromInvoiceJson(invoice.getRawJsonObject());
    }

    InvoiceCoveragePeriod resolveInvoiceCoveragePeriod(com.stripe.model.Invoice invoice) {
        JsonObject firstLineJson = getFirstInvoiceLineJson(invoice != null ? invoice.getRawJsonObject() : null);
        Long linePeriodStart = getNestedLong(firstLineJson, "period", "start");
        Long linePeriodEnd = getNestedLong(firstLineJson, "period", "end");

        if (linePeriodStart != null && linePeriodEnd != null) {
            return new InvoiceCoveragePeriod(
                toLocalDateTime(linePeriodStart),
                toLocalDateTime(linePeriodEnd)
            );
        }

        if (invoice == null || invoice.getPeriodStart() == null || invoice.getPeriodEnd() == null) {
            return null;
        }

        return new InvoiceCoveragePeriod(
            toLocalDateTime(invoice.getPeriodStart()),
            toLocalDateTime(invoice.getPeriodEnd())
        );
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus == null ? "" : stripeStatus.toLowerCase()) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.INCOMPLETE;
            case "unpaid" -> SubscriptionStatus.PAST_DUE;
            case "paused" -> SubscriptionStatus.PAUSED;
            default -> SubscriptionStatus.INCOMPLETE;
        };
    }

    private void markBetaPromoUsedIfConfirmed(com.stripe.model.Subscription stripeSub, User user) {
        Map<String, String> metadata = stripeSub.getMetadata();
        if (metadata == null || !"true".equalsIgnoreCase(metadata.get("beta_user"))) {
            return;
        }

        String betaPromoCode = metadata.get("beta_promo_code");
        if (betaPromoCode == null || betaPromoCode.isBlank()) {
            return;
        }

        UserBetaAccess betaAccess = user.ensureBetaAccess();
        if (!Boolean.TRUE.equals(betaAccess.getUsedBetaPromo())) {
            betaAccess.setUsedBetaPromo(true);
            betaAccess.setBetaPromoCode(betaPromoCode);
            betaAccess.setBetaPromoAppliedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String extractSubscriptionPriceId(com.stripe.model.Subscription stripeSub) {
        JsonObject firstItemJson = getFirstSubscriptionItemJson(stripeSub);

        String priceId = getNestedString(firstItemJson, "price", "id");
        if (priceId != null && !priceId.isBlank()) {
            return priceId;
        }

        return getNestedString(firstItemJson, "plan", "id");
    }

    private String extractSubscriptionCurrency(com.stripe.model.Subscription stripeSub) {
        JsonObject firstItemJson = getFirstSubscriptionItemJson(stripeSub);

        String currency = getNestedString(firstItemJson, "price", "currency");
        if (currency != null && !currency.isBlank()) {
            return currency.toUpperCase();
        }

        currency = getNestedString(firstItemJson, "plan", "currency");
        return currency != null && !currency.isBlank() ? currency.toUpperCase() : null;
    }

    private BigDecimal extractSubscriptionUnitAmount(com.stripe.model.Subscription stripeSub) {
        JsonObject firstItemJson = getFirstSubscriptionItemJson(stripeSub);

        Long unitAmount = getNestedLong(firstItemJson, "price", "unit_amount");
        if (unitAmount == null) {
            unitAmount = getNestedLong(firstItemJson, "plan", "amount");
        }

        return unitAmount == null
            ? null
            : BigDecimal.valueOf(unitAmount).movePointLeft(2);
    }

    private BillingPeriod resolveBillingPeriod(com.stripe.model.Subscription stripeSub, BillingPeriod fallback) {
        String interval = extractSubscriptionInterval(stripeSub);
        Long intervalCount = extractSubscriptionIntervalCount(stripeSub);

        if (interval == null || interval.isBlank()) {
            return fallback != null ? fallback : BillingPeriod.MONTHLY;
        }

        long count = intervalCount != null && intervalCount > 0 ? intervalCount : 1L;
        return switch (interval.toLowerCase()) {
            case "year" -> BillingPeriod.ANNUAL;
            case "month" -> count >= 12 ? BillingPeriod.ANNUAL : BillingPeriod.MONTHLY;
            default -> fallback != null ? fallback : BillingPeriod.MONTHLY;
        };
    }

    private String extractSubscriptionInterval(com.stripe.model.Subscription stripeSub) {
        JsonObject firstItemJson = getFirstSubscriptionItemJson(stripeSub);

        String interval = getNestedString(firstItemJson, "price", "recurring", "interval");
        if (interval != null && !interval.isBlank()) {
            return interval;
        }

        return getNestedString(firstItemJson, "plan", "interval");
    }

    private Long extractSubscriptionIntervalCount(com.stripe.model.Subscription stripeSub) {
        JsonObject firstItemJson = getFirstSubscriptionItemJson(stripeSub);

        Long intervalCount = getNestedLong(firstItemJson, "price", "recurring", "interval_count");
        if (intervalCount != null) {
            return intervalCount;
        }

        return getNestedLong(firstItemJson, "plan", "interval_count");
    }

    private LocalDateTime extractSubscriptionPeriodBoundary(com.stripe.model.Subscription stripeSub, String boundaryField) {
        JsonObject firstItemJson = getFirstSubscriptionItemJson(stripeSub);
        Long epochSeconds = getNestedLong(firstItemJson, boundaryField);
        return toLocalDateTime(epochSeconds);
    }

    private boolean isScheduledForCancellation(com.stripe.model.Subscription stripeSub) {
        if (Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd())) {
            return true;
        }

        Long cancelAt = getNestedLong(stripeSub.getRawJsonObject(), "cancel_at");
        return cancelAt != null && cancelAt > Instant.now().getEpochSecond();
    }

    private String extractCancellationReason(com.stripe.model.Subscription stripeSub) {
        JsonObject rawJson = stripeSub.getRawJsonObject();
        String comment = getNestedString(rawJson, "cancellation_details", "comment");
        if (comment != null && !comment.isBlank()) {
            return comment;
        }

        String feedback = getNestedString(rawJson, "cancellation_details", "feedback");
        if (feedback != null && !feedback.isBlank()) {
            return feedback;
        }

        String reason = getNestedString(rawJson, "cancellation_details", "reason");
        return reason != null && !reason.isBlank() ? reason : null;
    }

    private String extractSubscriptionIdFromInvoiceJson(JsonObject invoiceJson) {
        String subscriptionId = getNestedString(invoiceJson, "subscription");
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            return subscriptionId;
        }

        subscriptionId = getNestedString(invoiceJson, "parent", "subscription_details", "subscription");
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            return subscriptionId;
        }

        JsonObject firstLineJson = getFirstInvoiceLineJson(invoiceJson);
        return getNestedString(firstLineJson, "parent", "subscription_item_details", "subscription");
    }

    private JsonObject getFirstSubscriptionItemJson(com.stripe.model.Subscription stripeSub) {
        return getFirstElementFromArray(stripeSub != null ? stripeSub.getRawJsonObject() : null, "items", "data");
    }

    private JsonObject getFirstInvoiceLineJson(JsonObject invoiceJson) {
        return getFirstElementFromArray(invoiceJson, "lines", "data");
    }

    private JsonObject getFirstElementFromArray(JsonObject root, String containerField, String arrayField) {
        if (root == null || !root.has(containerField) || root.get(containerField).isJsonNull()) {
            return null;
        }

        JsonObject container = root.getAsJsonObject(containerField);
        if (!container.has(arrayField) || container.get(arrayField).isJsonNull()) {
            return null;
        }

        JsonArray array = container.getAsJsonArray(arrayField);
        if (array == null || array.isEmpty()) {
            return null;
        }

        JsonElement firstElement = array.get(0);
        return firstElement != null && firstElement.isJsonObject() ? firstElement.getAsJsonObject() : null;
    }

    private String getNestedString(JsonObject root, String... path) {
        JsonElement element = getNestedElement(root, path);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    private Long getNestedLong(JsonObject root, String... path) {
        JsonElement element = getNestedElement(root, path);
        return element != null && !element.isJsonNull() ? element.getAsLong() : null;
    }

    private JsonElement getNestedElement(JsonObject root, String... path) {
        if (root == null || path == null || path.length == 0) {
            return null;
        }

        JsonElement current = root;
        for (String segment : path) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }

            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(segment) || currentObject.get(segment).isJsonNull()) {
                return null;
            }

            current = currentObject.get(segment);
        }

        return current;
    }

    /**
     * Calculate next billing date from billing cycle anchor and subscription interval.
     * This provides proactive tracking of upcoming charges.
     */
    private LocalDateTime calculateNextBillingDate(Long billingAnchor, String interval, Long intervalCount) {
        if (billingAnchor == null || interval == null) {
            return null;
        }

        LocalDateTime anchorDate = toLocalDateTime(billingAnchor);
        if (anchorDate == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        long count = (intervalCount != null && intervalCount > 0) ? intervalCount : 1;

        // Calculate next billing date based on interval
        LocalDateTime nextBilling = anchorDate;
        while (nextBilling.isBefore(now)) {
            nextBilling = switch (interval.toLowerCase()) {
                case "day" -> nextBilling.plusDays(count);
                case "week" -> nextBilling.plusWeeks(count);
                case "month" -> nextBilling.plusMonths(count);
                case "year" -> nextBilling.plusYears(count);
                default -> nextBilling.plusMonths(1); // default to monthly
            };
        }

        return nextBilling;
    }

    record InvoiceCoveragePeriod(LocalDateTime start, LocalDateTime end) {
    }
}
