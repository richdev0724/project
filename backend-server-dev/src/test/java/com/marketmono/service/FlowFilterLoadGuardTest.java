package com.marketmono.service;

import com.marketmono.config.OptionTradesProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowFilterLoadGuardTest {

    @Test
    void limitsConcurrentExpensiveFilterQueriesPerEndpointAndShape() {
        OptionTradesProperties properties = new OptionTradesProperties();
        properties.getGuard().setMaxConcurrentExpensiveQueries(2);
        properties.getGuard().setRetryAfterSeconds(7);
        FlowFilterLoadGuard guard = new FlowFilterLoadGuard(properties);

        FlowFilterLoadGuard.GuardPermit first = guard.tryAcquire(
            "/api/v1/option-trades",
            "ticker_range_expensive"
        );
        FlowFilterLoadGuard.GuardPermit second = guard.tryAcquire(
            "/api/v1/option-trades",
            "ticker_range_expensive"
        );

        try {
            FlowFilterLoadGuard.GuardPermit busy = guard.tryAcquire(
                "/api/v1/option-trades",
                "ticker_range_expensive"
            );

            assertThat(first.isBusy()).isFalse();
            assertThat(second.isBusy()).isFalse();
            assertThat(busy.isBusy()).isTrue();
            assertThat(busy.retryAfterSeconds()).isEqualTo(7);
        } finally {
            first.close();
            second.close();
        }

        FlowFilterLoadGuard.GuardPermit afterRelease = guard.tryAcquire(
            "/api/v1/option-trades",
            "ticker_range_expensive"
        );

        try {
            assertThat(afterRelease.isBusy()).isFalse();
        } finally {
            afterRelease.close();
        }
    }

    @Test
    void skipsGuardForNonExpensiveFilterShapes() {
        OptionTradesProperties properties = new OptionTradesProperties();
        properties.getGuard().setMaxConcurrentExpensiveQueries(1);
        FlowFilterLoadGuard guard = new FlowFilterLoadGuard(properties);

        FlowFilterLoadGuard.GuardPermit first = guard.tryAcquire("/api/v1/option-trades", "ticker_range");
        FlowFilterLoadGuard.GuardPermit second = guard.tryAcquire("/api/v1/option-trades", "ticker_range");

        assertThat(first.isBusy()).isFalse();
        assertThat(second.isBusy()).isFalse();
    }
}
