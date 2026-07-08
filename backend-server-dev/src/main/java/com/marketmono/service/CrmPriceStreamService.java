package com.marketmono.service;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.CrmPriceStreamProperties;
import com.marketmono.dto.CrmPriceStreamSnapshotDto;
import com.marketmono.dto.CrmPriceStreamTickDto;
import com.marketmono.dto.UwPriceTickDto;
import com.marketmono.entity.CrmPriceBaseline;
import com.marketmono.entity.CrmPriceSnapshot;
import com.marketmono.repository.CrmPriceBaselineRepository;
import com.marketmono.repository.CrmPriceSnapshotRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves live prices for tickers saved in CRM filters from the Unusual Whales
 * {@code price:<TICKER>} websocket instead of stock-state REST polling.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Reconcile UW price channel subscriptions with the CRM ticker set</li>
 *   <li>Keep the latest tick per ticker in memory (persisted on a flush interval)</li>
 *   <li>Relay ticks to the Strata frontend on {@code /topic/strata-prices/{ticker}},
 *       with % gain/loss computed against the daily 9:30 AM ET baseline</li>
 *   <li>Answer subscribe snapshots so the frontend knows whether a ticker is
 *       tracked (tracked=false means it should use the existing REST pipeline)</li>
 * </ul>
 *
 * <p>SPX/SPXW/VIX/VIXW are never tracked here; they keep the REST setup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmPriceStreamService {

    private static final String TOPIC_PREFIX = "/topic/strata-prices/";

    private final CrmPriceStreamProperties properties;
    private final UnusualWhalesWebSocketClient webSocketClient;
    private final StrataStockPriceTickerUniverseService tickerUniverseService;
    private final CrmPriceSnapshotRepository snapshotRepository;
    private final CrmPriceBaselineRepository baselineRepository;
    private final WebSocketRelayPublisher relayPublisher;

    private final Map<String, UwPriceTickDto> latestTicks = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> prevCloseByTicker = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBroadcastAtMs = new ConcurrentHashMap<>();
    private final Set<String> dirtyTickers = ConcurrentHashMap.newKeySet();
    private volatile Set<String> trackedTickers = Set.of();

    @PostConstruct
    void init() {
        if (!properties.isEnabled()) {
            log.info("[CRM-PRICE-STREAM] Disabled via configuration");
            return;
        }
        loadPersistedTicks();
        webSocketClient.onPriceTick(this::handleTick);
        try {
            refreshSubscriptions();
        } catch (RuntimeException ex) {
            log.warn("[CRM-PRICE-STREAM] Initial subscription refresh failed (scheduler will retry): {}",
                ex.getMessage());
        }
        log.info("[CRM-PRICE-STREAM] Initialized with {} persisted ticks, {} tracked tickers",
            latestTicks.size(), trackedTickers.size());
    }

    /**
     * Re-read the CRM ticker set and reconcile UW price channel subscriptions.
     * Invoked on a fixed interval so newly saved CRM filters start streaming
     * without a restart. Also refreshes the baseline prev-close cache.
     */
    public void refreshSubscriptions() {
        Set<String> target = tickerUniverseService.getCrmPriceStreamTickers();
        Set<String> current = trackedTickers;

        Set<String> added = new LinkedHashSet<>(target);
        added.removeAll(current);
        Set<String> removed = new LinkedHashSet<>(current);
        removed.removeAll(target);

        added.forEach(webSocketClient::subscribePriceTicker);
        removed.forEach(webSocketClient::unsubscribePriceTicker);
        trackedTickers = Set.copyOf(target);

        reloadBaselines();

        if (!added.isEmpty() || !removed.isEmpty()) {
            log.info("[CRM-PRICE-STREAM] Subscription refresh: tracked={}, added={}, removed={}",
                target.size(), added, removed);
        }
    }

    /**
     * Persist the latest tick for tickers that changed since the last flush.
     */
    public void flushDirtyTicks() {
        for (String ticker : dirtyTickers.toArray(String[]::new)) {
            dirtyTickers.remove(ticker);
            UwPriceTickDto tick = latestTicks.get(ticker);
            if (tick == null) {
                continue;
            }
            try {
                snapshotRepository.upsert(
                    ticker,
                    tick.close(),
                    Instant.ofEpochMilli(tick.timeMs()),
                    tick.volume()
                );
            } catch (RuntimeException ex) {
                log.warn("[CRM-PRICE-STREAM] Failed to persist tick for {}: {}", ticker, ex.getMessage());
            }
        }
    }

    /**
     * Reload the prev-close baseline cache from crm_price_baselines.
     */
    public void reloadBaselines() {
        try {
            for (CrmPriceBaseline baseline : baselineRepository.findLatestPerTicker()) {
                if (baseline.getPrevClose() != null && baseline.getPrevClose().signum() > 0) {
                    prevCloseByTicker.put(baseline.getTicker(), baseline.getPrevClose());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("[CRM-PRICE-STREAM] Failed to load baselines: {}", ex.getMessage());
        }
    }

    /**
     * One-shot snapshot for a client subscribing to {@code /app/strata-prices/{ticker}}.
     */
    public CrmPriceStreamSnapshotDto getSnapshot(String rawTicker) {
        String ticker;
        try {
            ticker = StrataTickerPolicy.normalize(rawTicker);
        } catch (IllegalArgumentException ex) {
            return new CrmPriceStreamSnapshotDto(rawTicker, false, null);
        }

        boolean tracked = properties.isEnabled() && trackedTickers.contains(ticker);
        UwPriceTickDto tick = tracked ? latestTicks.get(ticker) : null;
        return new CrmPriceStreamSnapshotDto(ticker, tracked, tick == null ? null : toStreamTick(tick));
    }

    void handleTick(UwPriceTickDto tick) {
        String ticker = tick.ticker();
        if (!trackedTickers.contains(ticker)) {
            return;
        }
        latestTicks.put(ticker, tick);
        dirtyTickers.add(ticker);
        broadcastThrottled(tick);
    }

    private void broadcastThrottled(UwPriceTickDto tick) {
        long now = System.currentTimeMillis();
        Long lastAt = lastBroadcastAtMs.get(tick.ticker());
        if (lastAt != null && now - lastAt < properties.getBroadcastThrottleMs()) {
            return;
        }
        lastBroadcastAtMs.put(tick.ticker(), now);
        try {
            relayPublisher.publish(TOPIC_PREFIX + tick.ticker(), toStreamTick(tick));
        } catch (RuntimeException ex) {
            log.debug("[CRM-PRICE-STREAM] Failed to broadcast tick for {}: {}", tick.ticker(), ex.getMessage());
        }
    }

    private CrmPriceStreamTickDto toStreamTick(UwPriceTickDto tick) {
        BigDecimal prevClose = prevCloseByTicker.get(tick.ticker());
        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (prevClose != null && prevClose.signum() > 0) {
            change = tick.close().subtract(prevClose);
            changePercent = change
                .multiply(BigDecimal.valueOf(100))
                .divide(prevClose, 6, RoundingMode.HALF_UP);
        }
        return new CrmPriceStreamTickDto(
            tick.ticker(),
            tick.close(),
            prevClose,
            change,
            changePercent,
            tick.timeMs(),
            tick.volume()
        );
    }

    private void loadPersistedTicks() {
        try {
            for (CrmPriceSnapshot snapshot : snapshotRepository.findAll()) {
                latestTicks.put(snapshot.getTicker(), new UwPriceTickDto(
                    snapshot.getTicker(),
                    snapshot.getClose(),
                    snapshot.getTickTime().toEpochMilli(),
                    snapshot.getVolume()
                ));
            }
        } catch (RuntimeException ex) {
            log.warn("[CRM-PRICE-STREAM] Failed to load persisted ticks: {}", ex.getMessage());
        }
    }
}
