package com.marketmono.service.spotgex;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "spot-gex.collection", name = "stream-transport", havingValue = "fake")
@Slf4j
public class SpotGexFakeStreamAdapter implements SpotGexStreamAdapter {

    private static final String SOURCE = "marketmono_fake_spot_gex";
    private static final String RAW_CHANNEL = "fake:gex-strike-expiry-v2";
    private static final long ONE_MINUTE_MS = 60_000L;

    private final Set<String> subscribedTickers = new ConcurrentSkipListSet<>();
    private volatile Consumer<SpotGexStreamEvent> eventHandler;
    private volatile Instant lastEmitAt;

    @Override
    public void subscribe(String ticker) {
        subscribedTickers.add(normalizeTicker(ticker));
    }

    @Override
    public void unsubscribe(String ticker) {
        subscribedTickers.remove(normalizeTicker(ticker));
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
            eventHandler != null,
            tickerSnapshot.size(),
            tickerSnapshot
        );
    }

    @Scheduled(fixedDelayString = "${spot-gex.collection.fake-stream-interval-ms:1000}")
    public void emitOnce() {
        Consumer<SpotGexStreamEvent> handler = eventHandler;
        if (handler == null || subscribedTickers.isEmpty()) {
            return;
        }

        long eventTimestampMs = closedMinuteTimestampMs();
        for (String ticker : subscribedTickers) {
            for (SpotGexStreamEvent event : sampleEvents(ticker, eventTimestampMs)) {
                handler.accept(event);
            }
        }
        lastEmitAt = Instant.now();
        log.debug("[SPOT-GEX] Fake stream emitted {} ticker(s)", subscribedTickers.size());
    }

    private List<SpotGexStreamEvent> sampleEvents(String ticker, long timestampMs) {
        BigDecimal centerStrike = centerStrike(ticker);
        LocalDate expiry = LocalDate.now(ZoneOffset.UTC).plusDays(7);
        return List.of(
            sampleEvent(ticker, expiry, centerStrike.subtract(new BigDecimal("5.00")), timestampMs, -1),
            sampleEvent(ticker, expiry, centerStrike, timestampMs, 0),
            sampleEvent(ticker, expiry, centerStrike.add(new BigDecimal("5.00")), timestampMs, 1)
        );
    }

    private SpotGexStreamEvent sampleEvent(
            String ticker,
            LocalDate expiry,
            BigDecimal strike,
            long timestampMs,
            int offset) {
        BigDecimal callGamma = new BigDecimal("250.0000").add(new BigDecimal(offset * 25));
        BigDecimal putGamma = new BigDecimal("-75.0000").subtract(new BigDecimal(offset * 10));
        BigDecimal callDelta = new BigDecimal("100.0000").add(new BigDecimal(offset * 10));
        BigDecimal putDelta = new BigDecimal("-50.0000").subtract(new BigDecimal(offset * 5));
        return SpotGexStreamEvent.builder()
            .ticker(ticker)
            .expiry(expiry)
            .strikePrice(strike)
            .strikePriceCents(strike.movePointRight(2).intValueExact())
            .timestampMs(timestampMs)
            .bucketStartMs(timestampMs - (timestampMs % ONE_MINUTE_MS))
            .ingestedAtMs(System.currentTimeMillis())
            .underlyingPrice(centerStrike(ticker).add(new BigDecimal("0.67")))
            .callDeltaOi(callDelta)
            .putDeltaOi(putDelta)
            .callGammaOi(callGamma)
            .putGammaOi(putGamma)
            .callVannaOi(new BigDecimal("10.0000").add(new BigDecimal(offset)))
            .putVannaOi(new BigDecimal("-4.0000").subtract(new BigDecimal(offset)))
            .source(SOURCE)
            .rawChannelName(RAW_CHANNEL)
            .build();
    }

    private BigDecimal centerStrike(String ticker) {
        int bucket = Math.abs(ticker.hashCode() % 50);
        return new BigDecimal(400 + bucket).setScale(2);
    }

    private long closedMinuteTimestampMs() {
        long nowMs = System.currentTimeMillis();
        long closedBucketStartMs = (nowMs - ONE_MINUTE_MS) - ((nowMs - ONE_MINUTE_MS) % ONE_MINUTE_MS);
        return closedBucketStartMs + 30_000L;
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }
}
