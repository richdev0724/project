package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.SectorPerformanceLiveResponse;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.entity.SectorPerformanceSnapshot;
import com.marketmono.repository.SectorPerformanceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collects and serves the dashboard Sectors widget data from the database.
 *
 * Collection (scheduler-invoked every minute): each cycle fetches fresh
 * stock-state per sector ETF (last price, previous close, day volume ->
 * daily performance) and daily OHLC history (30-day / 6-month baseline
 * closes -> 30d / 6m performance), then upserts the computed snapshot.
 * If a fetch fails for a ticker, the previously stored snapshot is kept.
 *
 * Read path: database-only. User requests never call the upstream market
 * data API for this widget.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SectorPerformanceSnapshotService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    /** Snapshots older than this are considered stale (not served). */
    private static final Duration STALENESS_WINDOW = Duration.ofDays(4);

    /** Same ticker order the /live endpoint has always returned. */
    private static final List<String> SECTOR_ETFS = List.of(
        "XLP", "XLU", "XLV", "XLB", "XLC", "XLY", "XLE", "XLF", "XLI", "XLK", "XLRE"
    );

    private final SectorPerformanceSnapshotRepository sectorPerformanceSnapshotRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    /**
     * Database-only read used by the /sector-performance/live endpoint.
     * Missing or stale rows are returned with null metrics (no upstream fallback).
     */
    public List<SectorPerformanceLiveResponse> getSectorPerformanceFromDatabase() {
        Map<String, SectorPerformanceSnapshot> snapshotsByTicker;
        try {
            snapshotsByTicker = sectorPerformanceSnapshotRepository.findByTickerIn(SECTOR_ETFS)
                .stream()
                .collect(Collectors.toMap(SectorPerformanceSnapshot::getTicker, Function.identity()));
        } catch (Exception e) {
            // e.g. sector_performance_snapshots migration not yet applied
            log.warn("[SECTOR-PERFORMANCE] Snapshot read failed ({}), returning empty metrics", e.getMessage());
            snapshotsByTicker = Map.of();
        }

        Instant staleCutoff = Instant.now().minus(STALENESS_WINDOW);
        List<SectorPerformanceLiveResponse> results = new ArrayList<>();

        for (String ticker : SECTOR_ETFS) {
            SectorPerformanceSnapshot snapshot = snapshotsByTicker.get(ticker);
            if (snapshot == null || snapshot.getFetchedAt().isBefore(staleCutoff)) {
                results.add(new SectorPerformanceLiveResponse(
                    ticker, null, null, null, null, null, null, null, null, null, null));
                continue;
            }

            BigDecimal lastPrice = snapshot.getLastPrice();
            results.add(new SectorPerformanceLiveResponse(
                ticker,
                toDouble(lastPrice),
                toDouble(snapshot.getPrevClose()),
                snapshot.getDailyPct(),
                dollarChange(lastPrice, snapshot.getPrevClose()),
                snapshot.getPct30d(),
                dollarChange(lastPrice, snapshot.getBaseline30dClose()),
                snapshot.getPct6m(),
                dollarChange(lastPrice, snapshot.getBaseline6mClose()),
                snapshot.getVolume(),
                snapshot.getLastDate() != null ? snapshot.getLastDate().toString() : null
            ));
        }

        return results;
    }

    /**
     * Refreshes the stored snapshot for every sector ETF. Runs every scheduler
     * tick (1 minute): fetches fresh stock-state and daily OHLC per ticker,
     * recomputes daily / 30-day / 6-month performance, and upserts the row.
     */
    public void collectAndStoreSnapshots() {
        LocalDate todayEt = LocalDate.now(NEW_YORK_ZONE);
        Map<String, SectorPerformanceSnapshot> existingByTicker =
            sectorPerformanceSnapshotRepository.findByTickerIn(SECTOR_ETFS)
                .stream()
                .collect(Collectors.toMap(SectorPerformanceSnapshot::getTicker, Function.identity()));

        int stored = 0;
        int failed = 0;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<CollectedSnapshot>> futures = new LinkedHashMap<>();
            for (String ticker : SECTOR_ETFS) {
                SectorPerformanceSnapshot existing = existingByTicker.get(ticker);
                futures.put(ticker, executor.submit(() -> collectTicker(ticker, existing, todayEt)));
            }

            Instant fetchedAt = Instant.now();
            for (Map.Entry<String, Future<CollectedSnapshot>> entry : futures.entrySet()) {
                String ticker = entry.getKey();
                try {
                    CollectedSnapshot snapshot = entry.getValue().get();
                    if (snapshot == null) {
                        // Keep the previous snapshot instead of overwriting with nulls.
                        failed++;
                        continue;
                    }
                    sectorPerformanceSnapshotRepository.upsert(
                        ticker,
                        snapshot.lastPrice(),
                        snapshot.prevClose(),
                        snapshot.dailyPct(),
                        snapshot.pct30d(),
                        snapshot.pct6m(),
                        snapshot.volume(),
                        snapshot.lastDate(),
                        snapshot.baseline30dClose(),
                        snapshot.baseline6mClose(),
                        snapshot.baselineDate(),
                        fetchedAt
                    );
                    stored++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[SECTOR-PERFORMANCE] Interrupted while collecting {}", ticker);
                    return;
                } catch (Exception e) {
                    failed++;
                    log.warn("[SECTOR-PERFORMANCE] Failed to store snapshot for {}: {}", ticker, e.getMessage());
                }
            }
        }

        log.info("[SECTOR-PERFORMANCE] Snapshot cycle finished - stored: {}, failed: {}", stored, failed);
    }

    private CollectedSnapshot collectTicker(String ticker, SectorPerformanceSnapshot existing, LocalDate todayEt) {
        StockStateResponse.StockStateData stockState = fetchStockState(ticker);
        if (stockState == null) {
            return null;
        }

        BigDecimal lastPrice = parseDecimal(stockState.close());
        BigDecimal prevClose = parseDecimal(stockState.prevClose());
        if (lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[SECTOR-PERFORMANCE] [{}] Invalid stock-state close: {}", ticker, stockState.close());
            return null;
        }

        Baselines baselines = fetchBaselines(ticker, existing, todayEt);

        Double dailyPct = percentChange(prevClose, lastPrice);
        Double pct30d = percentChange(baselines.close30d(), lastPrice);
        Double pct6m = percentChange(baselines.close6m(), lastPrice);

        return new CollectedSnapshot(
            lastPrice,
            prevClose,
            dailyPct,
            pct30d,
            pct6m,
            stockState.totalVolume(),
            resolveLastDate(stockState.tapeTime(), todayEt),
            baselines.close30d(),
            baselines.close6m(),
            baselines.baselineDate()
        );
    }

    private StockStateResponse.StockStateData fetchStockState(String ticker) {
        try {
            StockStateResponse response = unusualWhalesClient.getStockStateForStrataCollector(ticker);
            StockStateResponse.StockStateData data = response != null ? response.data() : null;
            if (data == null) {
                log.warn("[SECTOR-PERFORMANCE] [{}] Empty stock-state response", ticker);
            }
            return data;
        } catch (Exception e) {
            log.warn("[SECTOR-PERFORMANCE] [{}] stock-state fetch failed: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches daily OHLC history every cycle and resolves the 30-day /
     * 6-month baseline closes with the same lookback logic the live endpoint
     * used. Falls back to the previously stored baselines on failure.
     */
    private Baselines fetchBaselines(String ticker, SectorPerformanceSnapshot existing, LocalDate todayEt) {
        try {
            UnusualWhalesOhlcDto ohlcData = unusualWhalesClient.getOhlcData(ticker, "1d", null, "1Y", null, 400);
            if (ohlcData == null || ohlcData.data() == null || ohlcData.data().isEmpty()) {
                log.warn("[SECTOR-PERFORMANCE] [{}] No daily OHLC data for baselines", ticker);
                return fallbackBaselines(existing);
            }

            List<UnusualWhalesOhlcDto.OhlcItem> regularSessionData = ohlcData.data().stream()
                .filter(item -> "r".equals(item.marketTime()))
                .filter(item -> item.date() != null && !item.date().isBlank())
                .sorted(Comparator.comparing(UnusualWhalesOhlcDto.OhlcItem::date))
                .toList();

            if (regularSessionData.isEmpty()) {
                return fallbackBaselines(existing);
            }

            LocalDate latestDate = LocalDate.parse(regularSessionData.get(regularSessionData.size() - 1).date());
            BigDecimal close30d = findBaselineClose(regularSessionData, latestDate.minusDays(30));
            BigDecimal close6m = findBaselineClose(regularSessionData, latestDate.minusDays(180));

            return new Baselines(close30d, close6m, todayEt);
        } catch (Exception e) {
            log.warn("[SECTOR-PERFORMANCE] [{}] Baseline OHLC fetch failed: {}", ticker, e.getMessage());
            return fallbackBaselines(existing);
        }
    }

    private Baselines fallbackBaselines(SectorPerformanceSnapshot existing) {
        if (existing != null) {
            return new Baselines(existing.getBaseline30dClose(), existing.getBaseline6mClose(), existing.getBaselineDate());
        }
        return new Baselines(null, null, null);
    }

    /**
     * Earliest regular-session close on/after the threshold date, falling back
     * to the oldest available candle (mirrors the previous live computation).
     */
    private BigDecimal findBaselineClose(List<UnusualWhalesOhlcDto.OhlcItem> regularSessionData, LocalDate threshold) {
        UnusualWhalesOhlcDto.OhlcItem startCandle = regularSessionData.stream()
            .filter(candle -> !LocalDate.parse(candle.date()).isBefore(threshold))
            .findFirst()
            .orElse(regularSessionData.get(0));

        BigDecimal close = parseDecimal(startCandle.close());
        return close != null && close.compareTo(BigDecimal.ZERO) > 0 ? close : null;
    }

    private LocalDate resolveLastDate(String tapeTime, LocalDate fallbackDate) {
        if (tapeTime != null && !tapeTime.isBlank()) {
            try {
                return OffsetDateTime.parse(tapeTime).atZoneSameInstant(NEW_YORK_ZONE).toLocalDate();
            } catch (Exception ignored) {
                // fall through to the fallback date
            }
        }
        return fallbackDate;
    }

    private Double percentChange(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || current == null || baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return current.subtract(baseline)
            .divide(baseline, 8, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double dollarChange(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null) {
            return null;
        }
        return current.subtract(baseline).doubleValue();
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private record Baselines(BigDecimal close30d, BigDecimal close6m, LocalDate baselineDate) {}

    private record CollectedSnapshot(
        BigDecimal lastPrice,
        BigDecimal prevClose,
        Double dailyPct,
        Double pct30d,
        Double pct6m,
        Long volume,
        LocalDate lastDate,
        BigDecimal baseline30dClose,
        BigDecimal baseline6mClose,
        LocalDate baselineDate
    ) {}
}
