package com.marketmono.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DashboardMarketPerformanceResponse;
import com.marketmono.dto.MarketTideDataPoint;
import com.marketmono.dto.MarketTideResponse;
import com.marketmono.dto.UnusualWhalesMarketTideDto;
import com.marketmono.entity.DashboardOhlcSnapshot;
import com.marketmono.entity.MarketTideSnapshot;
import com.marketmono.repository.DashboardOhlcSnapshotRepository;
import com.marketmono.repository.MarketTideSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores and serves the market-wide tide series for the Mono Matrix dashboard
 * chart.
 *
 * Read path: database-only from market_tide_snapshots. User requests never
 * call the upstream market data API for this widget.
 *
 * Write path: the scheduler calls {@link #collectAndStoreSnapshot()} every
 * minute during the 9:30 AM - 4:00 PM ET window (Mon-Fri). Each cycle makes a
 * single upstream market-tide call and reuses the SPY 1D series already stored
 * by the Market Overview collector for the SPY price line, so no extra SPY
 * calls are made. The current day's snapshot is replaced in place and rows
 * older than the retention window are purged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketTideSnapshotService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    // Collection window: 9:30 AM - 4:00 PM ET, Monday-Friday
    private static final LocalTime COLLECTION_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime COLLECTION_WINDOW_CLOSE = LocalTime.of(16, 0);
    private static final long ONE_MINUTE_MILLIS = 60_000L;
    private static final long TWO_MINUTES_MILLIS = 120_000L;
    private static final String SPY_TICKER = "SPY";
    private static final String SPY_TIMEFRAME = "1D";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<MarketTideDataPoint>> SERIES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DashboardMarketPerformanceResponse.SeriesPoint>> SPY_SERIES_TYPE =
        new TypeReference<>() {};

    private final MarketTideSnapshotRepository marketTideSnapshotRepository;
    private final DashboardOhlcSnapshotRepository dashboardOhlcSnapshotRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    @Value("${market-data.market-tide.retention-days:4}")
    private int retentionDays = 4;

    /**
     * Serves the latest stored tide series. No upstream fallback: when the
     * store is empty or stale an empty series is returned.
     */
    public MarketTideResponse getMarketTideFromDatabase() {
        try {
            MarketTideSnapshot snapshot = marketTideSnapshotRepository
                .findTopByOrderByTradingDateDesc()
                .orElse(null);

            Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
            if (snapshot == null || snapshot.getFetchedAt().isBefore(staleCutoff)) {
                log.warn("[MARKET-TIDE] No fresh snapshot available, returning empty series");
                return MarketTideResponse.success(List.of());
            }

            List<MarketTideDataPoint> series = deserializeSeries(snapshot.getSeries());
            return MarketTideResponse.success(series == null ? List.of() : series);
        } catch (Exception e) {
            // e.g. market_tide_snapshots migration not yet applied
            log.warn("[MARKET-TIDE] Snapshot read failed ({}), returning empty series", e.getMessage());
            return MarketTideResponse.success(List.of());
        }
    }

    /**
     * Fetches today's tide series (one upstream call), merges SPY prices from
     * the stored Market Overview SPY 1D snapshot, and replaces today's row.
     * Outside the market window it only runs when the store is empty/stale so
     * a fresh deploy still gets data immediately.
     */
    public void collectAndStoreSnapshot() {
        ZonedDateTime marketNow = ZonedDateTime.now(NEW_YORK_ZONE);
        Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        if (!isWithinCollectionWindow(marketNow)
            && marketTideSnapshotRepository.existsByFetchedAtAfter(staleCutoff)) {
            log.debug("[MARKET-TIDE] Skipping snapshot collection outside market window");
            return;
        }

        UnusualWhalesMarketTideDto tideResponse;
        try {
            tideResponse = unusualWhalesClient.getMarketTideUncached(false, false, null);
        } catch (Exception e) {
            log.warn("[MARKET-TIDE] Failed to fetch market tide data: {}", e.getMessage());
            return;
        }

        if (tideResponse == null || tideResponse.data() == null || tideResponse.data().isEmpty()) {
            // Keep the previous snapshot instead of overwriting with an empty series.
            log.warn("[MARKET-TIDE] No market tide data returned, keeping previous snapshot");
            return;
        }

        Map<Long, BigDecimal> spyPriceMap = buildSpyPriceMapFromStoredSnapshot();
        List<MarketTideDataPoint> series = mergeAndTransform(tideResponse.data(), spyPriceMap);
        if (series.isEmpty()) {
            log.warn("[MARKET-TIDE] Transformed series is empty, keeping previous snapshot");
            return;
        }

        LocalDate tradingDate = resolveTradingDate(tideResponse.data(), marketNow);
        try {
            marketTideSnapshotRepository.upsert(
                tradingDate,
                OBJECT_MAPPER.writeValueAsString(series),
                Instant.now()
            );
        } catch (Exception e) {
            log.warn("[MARKET-TIDE] Failed to store snapshot for {}: {}", tradingDate, e.getMessage());
            return;
        }

        int purged = marketTideSnapshotRepository.deleteByTradingDateBefore(
            marketNow.toLocalDate().minusDays(retentionDays)
        );
        long withSpyPrice = series.stream().filter(point -> point.spyClose() != null).count();
        log.info("[MARKET-TIDE] Snapshot cycle finished - date: {}, points: {} ({} with SPY price), purged: {}",
            tradingDate, series.size(), withSpyPrice, purged);
    }

    /**
     * Builds a timestamp-to-close map from the SPY 1D series stored by the
     * Market Overview collector (dashboard_ohlc_snapshots), avoiding a
     * dedicated SPY OHLC upstream call.
     */
    private Map<Long, BigDecimal> buildSpyPriceMapFromStoredSnapshot() {
        Map<Long, BigDecimal> spyPriceMap = new HashMap<>();
        try {
            List<DashboardOhlcSnapshot> snapshots = dashboardOhlcSnapshotRepository
                .findByTimeframeAndTickerIn(SPY_TIMEFRAME, List.of(SPY_TICKER));
            if (snapshots.isEmpty()) {
                log.warn("[MARKET-TIDE] No stored SPY 1D snapshot, tide points will have no SPY price");
                return spyPriceMap;
            }

            List<DashboardMarketPerformanceResponse.SeriesPoint> spySeries =
                OBJECT_MAPPER.readValue(snapshots.get(0).getSeries(), SPY_SERIES_TYPE);
            if (spySeries == null) {
                return spyPriceMap;
            }

            for (DashboardMarketPerformanceResponse.SeriesPoint point : spySeries) {
                if (point.close() == null) {
                    continue;
                }
                // Stored 1D points are stamped with the 1m candle's end time;
                // tide points use the candle start, so shift back one minute
                // to line the two series up.
                long timestamp = point.timestamp() - ONE_MINUTE_MILLIS;
                spyPriceMap.put(timestamp, point.close());

                long roundedTimestamp = (timestamp / ONE_MINUTE_MILLIS) * ONE_MINUTE_MILLIS;
                spyPriceMap.putIfAbsent(roundedTimestamp, point.close());
            }
        } catch (Exception e) {
            log.warn("[MARKET-TIDE] Failed to read stored SPY 1D series: {}", e.getMessage());
        }
        return spyPriceMap;
    }

    /**
     * Same merge logic as the legacy live market-tide path: exact timestamp
     * match first, then minute-rounded, then closest within 2 minutes.
     */
    private List<MarketTideDataPoint> mergeAndTransform(
        List<UnusualWhalesMarketTideDto.MarketTideItem> tideItems,
        Map<Long, BigDecimal> spyPriceMap
    ) {
        return tideItems.stream()
            .map(item -> {
                long timestamp = parseTimestamp(item.timestamp());
                if (timestamp == 0L) {
                    return null;
                }
                long roundedTimestamp = (timestamp / ONE_MINUTE_MILLIS) * ONE_MINUTE_MILLIS;

                BigDecimal spyClose = spyPriceMap.get(timestamp);
                if (spyClose == null) {
                    spyClose = spyPriceMap.get(roundedTimestamp);
                }
                if (spyClose == null) {
                    spyClose = findClosestSpyPrice(timestamp, spyPriceMap);
                }

                return new MarketTideDataPoint(
                    timestamp,
                    item.date(),
                    item.netCallPremium(),
                    item.netPutPremium(),
                    item.netVolume(),
                    spyClose,
                    item.timestamp()
                );
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private BigDecimal findClosestSpyPrice(long timestamp, Map<Long, BigDecimal> spyPriceMap) {
        for (long offset = -TWO_MINUTES_MILLIS; offset <= TWO_MINUTES_MILLIS; offset += ONE_MINUTE_MILLIS) {
            BigDecimal price = spyPriceMap.get(timestamp + offset);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private LocalDate resolveTradingDate(
        List<UnusualWhalesMarketTideDto.MarketTideItem> tideItems,
        ZonedDateTime marketNow
    ) {
        for (UnusualWhalesMarketTideDto.MarketTideItem item : tideItems) {
            if (item.date() != null && !item.date().isBlank()) {
                try {
                    return LocalDate.parse(item.date());
                } catch (Exception ignored) {
                    // fall through to market-clock date
                }
            }
        }
        return marketNow.toLocalDate();
    }

    private boolean isWithinCollectionWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(COLLECTION_WINDOW_OPEN) && !time.isAfter(COLLECTION_WINDOW_CLOSE);
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            log.debug("[MARKET-TIDE] Error parsing timestamp {}: {}", timestamp, e.getMessage());
            return 0L;
        }
    }

    private List<MarketTideDataPoint> deserializeSeries(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, SERIES_TYPE);
        } catch (Exception e) {
            log.warn("[MARKET-TIDE] Failed to deserialize stored series: {}", e.getMessage());
            return null;
        }
    }
}
