package com.marketmono.service;

import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.SubscriptionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionObservabilityMetrics {

    private static final List<SubscriptionStatus> ACCESS_GRANTING_STATUSES = List.of(
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.TRIALING,
        SubscriptionStatus.PAST_DUE
    );

    private static final List<StripeMode> OBSERVED_STRIPE_MODES = List.of(StripeMode.TEST, StripeMode.PROD);
    private static final String TAG_STRIPE_MODE = "stripe_mode";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ACCESS = "access";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        for (StripeMode stripeMode : OBSERVED_STRIPE_MODES) {
            registerSubscriptionCountGauges(stripeMode);
            registerAccessGauges(stripeMode);
            registerPaymentCountGauges(stripeMode);
            registerPaymentAmountGauges(stripeMode);
        }
    }

    private void registerSubscriptionCountGauges(StripeMode stripeMode) {
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            Gauge.builder(
                    "marketmono.subscriptions.count",
                    subscriptionRepository,
                    repository -> metricValue(() -> repository.countByStripeModeAndStatus(stripeMode, status))
                )
                .description("Subscription count by Stripe environment and lifecycle status")
                .tag(TAG_STRIPE_MODE, stripeModeTag(stripeMode))
                .tag(TAG_STATUS, statusTag(status))
                .register(meterRegistry);
        }
    }

    private void registerAccessGauges(StripeMode stripeMode) {
        Gauge.builder(
                "marketmono.subscription.access.count",
                subscriptionRepository,
                repository -> metricValue(() -> repository.countAccessAllowedByStripeMode(stripeMode))
            )
            .description("Subscription count that currently allows application access")
            .tag(TAG_STRIPE_MODE, stripeModeTag(stripeMode))
            .tag(TAG_ACCESS, "allowed")
            .register(meterRegistry);

        Gauge.builder(
                "marketmono.subscription.cancel_scheduled.count",
                subscriptionRepository,
                repository -> metricValue(() -> repository.countByStripeModeAndCancelAtPeriodEndTrueAndStatusIn(
                    stripeMode,
                    ACCESS_GRANTING_STATUSES
                ))
            )
            .description("Access-granting subscriptions scheduled to cancel at period end")
            .tag(TAG_STRIPE_MODE, stripeModeTag(stripeMode))
            .register(meterRegistry);
    }

    private void registerPaymentCountGauges(StripeMode stripeMode) {
        for (PaymentStatus status : PaymentStatus.values()) {
            Gauge.builder(
                    "marketmono.subscription.payments.count",
                    paymentHistoryRepository,
                    repository -> metricValue(() -> repository.countByStripeModeAndStatus(stripeMode, status))
                )
                .description("Payment-history record count by Stripe environment and payment status")
                .tag(TAG_STRIPE_MODE, stripeModeTag(stripeMode))
                .tag(TAG_STATUS, statusTag(status))
                .register(meterRegistry);
        }
    }

    private void registerPaymentAmountGauges(StripeMode stripeMode) {
        Gauge.builder(
                "marketmono.subscription.payments.amount_usd",
                paymentHistoryRepository,
                repository -> metricValue(() -> repository.sumUsdAmountByStripeModeAndStatus(stripeMode, PaymentStatus.PAID))
            )
            .description("Total USD amount from paid payment-history records by Stripe environment")
            .tag(TAG_STRIPE_MODE, stripeModeTag(stripeMode))
            .tag(TAG_STATUS, statusTag(PaymentStatus.PAID))
            .register(meterRegistry);
    }

    private static double metricValue(Supplier<? extends Number> supplier) {
        try {
            Number value = supplier.get();
            return value == null ? 0.0 : value.doubleValue();
        } catch (RuntimeException e) {
            log.debug("Unable to collect subscription observability metric: {}", e.getMessage());
            return Double.NaN;
        }
    }

    private static String stripeModeTag(StripeMode stripeMode) {
        return stripeMode == StripeMode.PROD ? "live" : "test";
    }

    private static String statusTag(Enum<?> status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
