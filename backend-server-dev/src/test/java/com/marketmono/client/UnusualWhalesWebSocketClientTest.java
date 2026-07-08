package com.marketmono.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.service.spotgex.SpotGexStreamEvent;
import com.marketmono.service.spotgex.SpotGexWebSocketPayloadParser;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UnusualWhalesWebSocketClientTest {

    @Test
    void shouldRouteSubscribedSpotGexChannelsToSpotGexHandler() {
        UnusualWhalesWebSocketClient client = client();
        AtomicReference<SpotGexStreamEvent> received = new AtomicReference<>();
        client.onSpotGexStreamEvent(received::set);
        client.subscribeSpotGexTicker("spy");

        client.routeMessage(capturedSpyFrame());

        assertThat(received.get()).isNotNull();
        assertThat(received.get().ticker()).isEqualTo("SPY");
        assertThat(received.get().bucketKey()).isEqualTo("SPY|2026-06-11|72500|1781198340000");
    }

    @Test
    void shouldIgnoreUnsubscribedSpotGexChannels() {
        UnusualWhalesWebSocketClient client = client();
        AtomicReference<SpotGexStreamEvent> received = new AtomicReference<>();
        client.onSpotGexStreamEvent(received::set);

        client.routeMessage(capturedSpyFrame());

        assertThat(received.get()).isNull();
    }

    @Test
    void shouldContinueRoutingExistingFlowAndOffLitChannels() {
        UnusualWhalesWebSocketClient client = client();
        AtomicReference<UnusualWhalesFlowAlertDto> flowAlert = new AtomicReference<>();
        AtomicReference<DarkPoolRecentTradeDto> offLitTrade = new AtomicReference<>();
        client.onFlowAlert(flowAlert::set);
        client.onOffLitTrade(offLitTrade::set);

        client.routeMessage("""
            ["flow-alerts",{"ticker":"SPY","option_chain":"SPY260619C00500000","type":"CALL"}]
            """);
        client.routeMessage("""
            ["off_lit_trades",{"ticker":"AAPL","price":"150.25","size":100,"premium":"15025.00"}]
            """);

        assertThat(flowAlert.get()).isNotNull();
        assertThat(flowAlert.get().ticker()).isEqualTo("SPY");
        assertThat(offLitTrade.get()).isNotNull();
        assertThat(offLitTrade.get().ticker()).isEqualTo("AAPL");
    }

    @Test
    void shouldRouteNewsChannelToNewsHeadlineHandler() {
        UnusualWhalesWebSocketClient client = client();
        AtomicReference<NewsHeadlineDto> headline = new AtomicReference<>();
        client.onNewsHeadline(headline::set);

        client.routeMessage("""
            [
              "news",
              {
                "created_at": "2026-06-24T10:15:30Z",
                "headline": "NVDA announces new AI platform",
                "source": "Unusual Whales",
                "tickers": ["NVDA", "SMH"],
                "sentiment": "positive",
                "is_major": true,
                "tags": ["AI", "semiconductors"],
                "meta": {"provider_id": "uw-news-123"},
                "description": "Nvidia announced a new AI platform.",
                "url": "https://example.com/nvda",
                "image_url": "https://example.com/nvda.png"
              }
            ]
            """);

        assertThat(headline.get()).isNotNull();
        assertThat(headline.get().createdAt()).isEqualTo(OffsetDateTime.parse("2026-06-24T10:15:30Z"));
        assertThat(headline.get().headline()).isEqualTo("NVDA announces new AI platform");
        assertThat(headline.get().source()).isEqualTo("Unusual Whales");
        assertThat(headline.get().tickers()).containsExactly("NVDA", "SMH");
        assertThat(headline.get().isMajor()).isTrue();
        assertThat(headline.get().tags()).containsExactly("AI", "semiconductors");
        assertThat(headline.get().meta()).containsEntry("provider_id", "uw-news-123");
        assertThat(headline.get().url()).isEqualTo("https://example.com/nvda");
        assertThat(headline.get().imageUrl()).isEqualTo("https://example.com/nvda.png");
    }

    @Test
    void shouldIgnoreMalformedNewsPayloadWithoutHeadline() {
        UnusualWhalesWebSocketClient client = client();
        AtomicReference<NewsHeadlineDto> headline = new AtomicReference<>();
        client.onNewsHeadline(headline::set);

        client.routeMessage("""
            ["news",{"source":"Unusual Whales","tickers":["NVDA"]}]
            """);

        assertThat(headline.get()).isNull();
    }

    @Test
    void shouldAllowNewsSocketSubscriptionToBeDisabledIndependently() {
        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getApi().getEndpoints().setNews(true);
        config.getWebsocket().getNews().setEnabled(false);
        config.getWebsocket().setMessageBufferSize(32);
        ObjectMapper objectMapper = new ObjectMapper();
        UnusualWhalesWebSocketClient client = new UnusualWhalesWebSocketClient(
            config,
            objectMapper,
            new SpotGexWebSocketPayloadParser(objectMapper)
        );

        assertThat(client.shouldSubscribeToNews()).isFalse();
    }

    private static UnusualWhalesWebSocketClient client() {
        ObjectMapper objectMapper = new ObjectMapper();
        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getWebsocket().setMessageBufferSize(32);
        return new UnusualWhalesWebSocketClient(
            config,
            objectMapper,
            new SpotGexWebSocketPayloadParser(objectMapper)
        );
    }

    private static String capturedSpyFrame() {
        return """
            [
              "gex_strike_expiry:SPY",
              {
                "ticker": "SPY",
                "timestamp": 1781198376000,
                "call_gamma_oi": "61121163.0323106",
                "put_gamma_oi": "-348912158.9329215",
                "strike": "725",
                "expiry": "2026-06-11",
                "price": "725.675"
              }
            ]
            """;
    }
}
