package com.marketmono.service;

import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.SubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionObservabilityMetricsTest {

    @Test
    void registersSubscriptionAndPaymentGaugesByStripeEnvironment() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        PaymentHistoryRepository paymentHistoryRepository = mock(PaymentHistoryRepository.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(subscriptionRepository.countByStripeModeAndStatus(StripeMode.TEST, SubscriptionStatus.ACTIVE))
            .thenReturn(3L);
        when(subscriptionRepository.countByStripeModeAndStatus(StripeMode.PROD, SubscriptionStatus.ACTIVE))
            .thenReturn(7L);
        when(subscriptionRepository.countAccessAllowedByStripeMode(StripeMode.TEST)).thenReturn(4L);
        when(subscriptionRepository.countAccessAllowedByStripeMode(StripeMode.PROD)).thenReturn(9L);
        when(subscriptionRepository.countByStripeModeAndCancelAtPeriodEndTrueAndStatusIn(
            eq(StripeMode.PROD),
            anyCollection()
        )).thenReturn(2L);
        when(paymentHistoryRepository.countByStripeModeAndStatus(StripeMode.PROD, PaymentStatus.PAID))
            .thenReturn(11L);
        when(paymentHistoryRepository.sumUsdAmountByStripeModeAndStatus(StripeMode.PROD, PaymentStatus.PAID))
            .thenReturn(new BigDecimal("120.50"));

        new SubscriptionObservabilityMetrics(
            subscriptionRepository,
            paymentHistoryRepository,
            meterRegistry
        ).registerMetrics();

        assertThat(gauge(meterRegistry, "marketmono.subscriptions.count", "stripe_mode", "test", "status", "active"))
            .isEqualTo(3.0);
        assertThat(gauge(meterRegistry, "marketmono.subscriptions.count", "stripe_mode", "live", "status", "active"))
            .isEqualTo(7.0);
        assertThat(gauge(
            meterRegistry,
            "marketmono.subscription.access.count",
            "stripe_mode",
            "test",
            "access",
            "allowed"
        ))
            .isEqualTo(4.0);
        assertThat(gauge(
            meterRegistry,
            "marketmono.subscription.access.count",
            "stripe_mode",
            "live",
            "access",
            "allowed"
        ))
            .isEqualTo(9.0);
        assertThat(gauge(meterRegistry, "marketmono.subscription.cancel_scheduled.count", "stripe_mode", "live"))
            .isEqualTo(2.0);
        assertThat(gauge(
            meterRegistry,
            "marketmono.subscription.payments.count",
            "stripe_mode",
            "live",
            "status",
            "paid"
        ))
            .isEqualTo(11.0);
        assertThat(gauge(
            meterRegistry,
            "marketmono.subscription.payments.amount_usd",
            "stripe_mode",
            "live",
            "status",
            "paid"
        ))
            .isEqualTo(120.50);
    }

    private static double gauge(SimpleMeterRegistry meterRegistry, String name, String... tags) {
        return meterRegistry.find(name).tags(tags).gauge().value();
    }
}
