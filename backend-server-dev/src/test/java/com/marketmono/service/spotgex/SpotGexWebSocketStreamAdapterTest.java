package com.marketmono.service.spotgex;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexWebSocketStreamAdapterTest {

    @Mock
    private UnusualWhalesWebSocketClient webSocketClient;

    private SpotGexWebSocketStreamAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpotGexWebSocketStreamAdapter(webSocketClient);
    }

    @Test
    void shouldNormalizeTickerSubscriptionsAndExposeHealth() {
        when(webSocketClient.isConnected()).thenReturn(true);

        adapter.subscribe(" spy ");

        verify(webSocketClient).subscribeSpotGexTicker("SPY");
        SpotGexStreamHealth health = adapter.health();
        assertThat(health.source()).isEqualTo("options_feed_ws");
        assertThat(health.connected()).isTrue();
        assertThat(health.subscribedTickers()).containsExactly("SPY");
        assertThat(health.activeTickerCount()).isEqualTo(1);
    }

    @Test
    void shouldStopRoutingRemovedTickerSubscriptions() {
        adapter.subscribe("SPY");
        adapter.unsubscribe(" spy ");

        verify(webSocketClient).unsubscribeSpotGexTicker("SPY");
        assertThat(adapter.health().subscribedTickers()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchClientEventsThroughAdapterHandler() {
        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(webSocketClient).onSpotGexStreamEvent(handlerCaptor.capture());
        AtomicReference<SpotGexStreamEvent> received = new AtomicReference<>();
        adapter.onEvent(received::set);

        SpotGexStreamEvent event = sampleEvent();
        handlerCaptor.getValue().accept(event);

        assertThat(received.get()).isSameAs(event);
    }

    private static SpotGexStreamEvent sampleEvent() {
        return SpotGexStreamEvent.builder()
            .ticker("SPY")
            .expiry(LocalDate.of(2026, 6, 11))
            .strikePrice(new BigDecimal("725"))
            .strikePriceCents(72_500)
            .timestampMs(1_781_198_376_000L)
            .bucketStartMs(1_781_198_340_000L)
            .ingestedAtMs(1_781_198_376_100L)
            .source("options_feed_ws")
            .rawChannelName("gex_strike_expiry:SPY")
            .build();
    }
}
