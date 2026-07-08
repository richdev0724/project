package com.marketmono.service.spotgex;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "spot-gex.collection", name = "stream-transport", havingValue = "websocket", matchIfMissing = true)
public class SpotGexWebSocketStreamAdapter implements SpotGexStreamAdapter {

    private static final String SOURCE = "options_feed_ws";

    private final UnusualWhalesWebSocketClient webSocketClient;
    private final Set<String> subscribedTickers = new ConcurrentSkipListSet<>();
    private volatile Consumer<SpotGexStreamEvent> eventHandler;

    public SpotGexWebSocketStreamAdapter(UnusualWhalesWebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        this.webSocketClient.onSpotGexStreamEvent(this::dispatch);
    }

    @Override
    public void subscribe(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        subscribedTickers.add(normalizedTicker);
        webSocketClient.subscribeSpotGexTicker(normalizedTicker);
    }

    @Override
    public void unsubscribe(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        subscribedTickers.remove(normalizedTicker);
        webSocketClient.unsubscribeSpotGexTicker(normalizedTicker);
    }

    @Override
    public void onEvent(Consumer<SpotGexStreamEvent> handler) {
        this.eventHandler = handler;
    }

    @Override
    public SpotGexStreamHealth health() {
        List<String> tickerSnapshot = List.copyOf(subscribedTickers);
        return new SpotGexStreamHealth(
            SOURCE,
            webSocketClient.isConnected(),
            tickerSnapshot.size(),
            tickerSnapshot
        );
    }

    private void dispatch(SpotGexStreamEvent event) {
        Consumer<SpotGexStreamEvent> handler = eventHandler;
        if (handler != null) {
            handler.accept(event);
        }
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }
}
