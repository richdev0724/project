package com.marketmono.service.spotgex;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexFakeStreamAdapterTest {

    @Test
    void shouldEmitClosedMinuteCanonicalEventsForSubscribedTickers() {
        SpotGexFakeStreamAdapter adapter = new SpotGexFakeStreamAdapter();
        List<SpotGexStreamEvent> events = new ArrayList<>();

        adapter.onEvent(events::add);
        adapter.subscribe(" spy ");
        adapter.emitOnce();

        assertThat(adapter.health().connected()).isTrue();
        assertThat(adapter.health().subscribedTickers()).containsExactly("SPY");
        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.ticker()).isEqualTo("SPY");
            assertThat(event.expiry()).isNotNull();
            assertThat(event.strikePrice()).isNotNull();
            assertThat(event.strikePriceCents()).isPositive();
            assertThat(event.bucketStartMs()).isEqualTo(event.timestampMs() - (event.timestampMs() % 60_000L));
            assertThat(event.underlyingPrice()).isNotNull();
            assertThat(event.source()).isEqualTo("marketmono_fake_spot_gex");
            assertThat(event.rawChannelName()).isEqualTo("fake:gex-strike-expiry-v2");
        });
    }

    @Test
    void shouldStopEmittingForUnsubscribedTicker() {
        SpotGexFakeStreamAdapter adapter = new SpotGexFakeStreamAdapter();
        List<SpotGexStreamEvent> events = new ArrayList<>();

        adapter.onEvent(events::add);
        adapter.subscribe("SPY");
        adapter.unsubscribe("SPY");
        adapter.emitOnce();

        assertThat(adapter.health().activeTickerCount()).isZero();
        assertThat(events).isEmpty();
    }
}
