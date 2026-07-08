package com.marketmono.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DashboardMarketPerformanceResponse;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.entity.DailyStockStateSnapshot;
import com.marketmono.entity.DashboardOhlcSnapshot;
import com.marketmono.repository.DailyStockStateSnapshotRepository;
import com.marketmono.repository.DashboardOhlcSnapshotRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the batched dashboard payload for the market performance widget.
 *
 * Read path: database-only from dashboard_ohlc_snapshots (one stored series
 * per ticker + timeframe, refreshed by the scheduler). User requests never
 * call the upstream market data API for this widget.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardMarketPerformanceService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    // Collection window: 9:30 AM - 4:15 PM ET, Monday-Friday
    private static final LocalTime COLLECTION_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime COLLECTION_WINDOW_CLOSE = LocalTime.of(16, 15);
    private static final List<String> DEFAULT_TICKERS = List.of("SPY", "QQQ", "IWM", "DIA");
    private static final Set<String> ALLOWED_TICKERS = Set.of(
        "SPY", "QQQ", "IWM", "DIA",
        "NVDA", "META", "MSFT", "AAPL", "GOOG", "AMZN", "TSLA"
    );
    private static final Set<String> SUPPORTED_TIMEFRAMES = Set.of(
        "1D", "5D", "1M", "3M", "6M", "YTD", "1Y", "5Y"
    );
    /** Timeframes shown by the dashboard widgets; each is one upstream call per ticker. */
    private static final List<String> COLLECTED_TIMEFRAMES = List.of(
        "1D", "5D", "1M", "3M", "6M", "YTD", "1Y"
    );
    /** Timeframes whose series move intraday; refreshed on every scheduler cycle. */
    private static final Set<String> INTRADAY_COLLECTED_TIMEFRAMES = Set.of("1D", "5D", "1M");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<DashboardMarketPerformanceResponse.SeriesPoint>> SERIES_TYPE =
        new TypeReference<>() {};

    private final DashboardOhlcSnapshotRepository dashboardOhlcSnapshotRepository;
    private final DailyStockStateSnapshotRepository dailyStockStateSnapshotRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    @Value("${market-data.dashboard-ohlc.tickers:SPY,QQQ,IWM,DIA,NVDA,META,MSFT,AAPL,GOOG,AMZN,TSLA}")
    private List<String> collectedTickers = new ArrayList<>(ALLOWED_TICKERS);

    @Value("${market-data.dashboard-ohlc.retention-days:4}")
    private int retentionDays = 4;

    @Value("${market-data.dashboard-ohlc.daily-timeframe-refresh-minutes:60}")
    private int dailyTimeframeRefreshMinutes = 60;

    public DashboardMarketPerformanceResponse getMarketPerformance(String timeframe) {
        return getMarketPerformance(timeframe, null);
    }

    /**
     * Read path is database-only: user requests never trigger upstream market
     * data calls. The scheduler keeps dashboard_ohlc_snapshots fresh.
     */
    public DashboardMarketPerformanceResponse getMarketPerformance(String timeframe, List<String> requestedTickers) {
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        List<String> tickers = normalizeTickers(requestedTickers);
        Map<String, DashboardMarketPerformanceResponse.TickerSeries> tickerSeries =
            buildDatabaseTickerSeries(normalizedTimeframe, tickers);

        log.info("[MARKET-PERFORMANCE] Served timeframe={} tickers={} from dashboard_ohlc_snapshots",
            normalizedTimeframe, tickers);

        String asOf = resolveAsOf(tickerSeries);
        return new DashboardMarketPerformanceResponse(normalizedTimeframe, asOf, tickerSeries);
    }

    private List<String> normalizeTickers(List<String> requestedTickers) {
        if (requestedTickers == null || requestedTickers.isEmpty()) {
            return DEFAULT_TICKERS;
        }

        List<String> normalized = requestedTickers.stream()
            .filter(Objects::nonNull)
            .map(ticker -> ticker.trim().toUpperCase(Locale.ROOT))
            .filter(ticker -> !ticker.isBlank())
            .distinct()
            .toList();

        if (normalized.isEmpty()) {
            return DEFAULT_TICKERS;
        }

        for (String ticker : normalized) {
            if (!ALLOWED_TICKERS.contains(ticker)) {
                throw new IllegalArgumentException("unsupported ticker: " + ticker);
            }
        }

        return normalized;
    }

    private Map<String, DashboardMarketPerformanceResponse.TickerSeries> buildDatabaseTickerSeries(
        String timeframe,
        List<String> tickers
    ) {
        Map<String, DashboardOhlcSnapshot> snapshotsByTicker;
        try {
            snapshotsByTicker = dashboardOhlcSnapshotRepository
                .findByTimeframeAndTickerIn(timeframe, tickers)
                .stream()
                .collect(Collectors.toMap(DashboardOhlcSnapshot::getTicker, Function.identity()));
        } catch (Exception e) {
            // e.g. dashboard_ohlc_snapshots migration not yet applied
            log.warn("[MARKET-PERFORMANCE] Snapshot read failed ({}), returning empty stored series", e.getMessage());
            return buildEmptyTickerSeries(tickers);
        }

        Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        Map<String, DashboardMarketPerformanceResponse.TickerSeries> tickerSeries = new LinkedHashMap<>();

        for (String ticker : tickers) {
            DashboardOhlcSnapshot snapshot = snapshotsByTicker.get(ticker);
            if (snapshot == null || snapshot.getFetchedAt().isBefore(staleCutoff)) {
                tickerSeries.put(ticker, new DashboardMarketPerformanceResponse.TickerSeries(null, List.of()));
                continue;
            }

            List<DashboardMarketPerformanceResponse.SeriesPoint> series = deserializeSeries(snapshot.getSeries());
            if (series == null || series.isEmpty()) {
                tickerSeries.put(ticker, new DashboardMarketPerformanceResponse.TickerSeries(
                    snapshot.getPreviousSessionClose(),
                    List.of()
                ));
                continue;
            }

            tickerSeries.put(ticker, new DashboardMarketPerformanceResponse.TickerSeries(
                snapshot.getPreviousSessionClose(),
                series
            ));
        }

        return tickerSeries;
    }

    private Map<String, DashboardMarketPerformanceResponse.TickerSeries> buildEmptyTickerSeries(List<String> tickers) {
        Map<String, DashboardMarketPerformanceResponse.TickerSeries> tickerSeries = new LinkedHashMap<>();
        for (String ticker : tickers) {
            tickerSeries.put(ticker, new DashboardMarketPerformanceResponse.TickerSeries(null, List.of()));
        }
        return tickerSeries;
    }

    /**
     * Refreshes the stored snapshot for every collected ticker + timeframe.
     * Each cycle:
     * 1. Reads every ticker's previous-session close (used by the 1D percent
     *    gain/loss: (close - prev_close) / prev_close) from the daily
     *    stock-state store, which is pulled from upstream once per trading
     *    day at 9:30 AM ET instead of every minute.
     * 2. Fetches uncached and sequentially, one timeframe at a time (the
     *    shared 5-minute API cache previously left tickers minutes stale
     *    and out of sync with each other).
     * 3. Writes each timeframe's rows as soon as its tickers finish, sharing
     *    one fetchedAt per timeframe batch. The widget requests a single
     *    timeframe per call, so cross-ticker consistency within a timeframe
     *    is what matters; batching per timeframe means a cold start serves
     *    1D (the dashboard/Meridian default, first in the list) within
     *    seconds instead of after the entire ticker x timeframe cycle.
     * Invoked every minute by the snapshot scheduler; outside the market window
     * it only runs when the store is empty/stale so a fresh deploy still gets
     * data immediately.
     */
    public void collectAndStoreSnapshots() {
        ZonedDateTime marketNow = ZonedDateTime.now(NEW_YORK_ZONE);
        Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        if (!isWithinCollectionWindow(marketNow)
            && dashboardOhlcSnapshotRepository.existsByFetchedAtAfter(staleCutoff)) {
            log.debug("[MARKET-PERFORMANCE] Skipping snapshot collection outside market window");
            return;
        }

        List<String> tickers = normalizeTickers(collectedTickers);
        int stored = 0;
        int failed = 0;
        int skippedTimeframes = 0;

        Map<String, BigDecimal> previousSessionCloseByTicker = fetchPreviousSessionCloses(tickers);
        Instant dailyRefreshCutoff = Instant.now().minus(Duration.ofMinutes(dailyTimeframeRefreshMinutes));

        for (String timeframe : COLLECTED_TIMEFRAMES) {
            // Daily-candle timeframes (3M/6M/YTD/1Y) only gain a bar once per
            // session; re-fetching them every minute wastes upstream calls
            // and delays the intraday series everyone is watching.
            if (!INTRADAY_COLLECTED_TIMEFRAMES.contains(timeframe)
                && dashboardOhlcSnapshotRepository.existsByTimeframeAndFetchedAtAfter(timeframe, dailyRefreshCutoff)) {
                skippedTimeframes++;
                continue;
            }

            List<PendingSnapshot> batch = new ArrayList<>();
            for (String ticker : tickers) {
                List<DashboardMarketPerformanceResponse.SeriesPoint> series = fetchSeries(ticker, timeframe);
                if (series.isEmpty()) {
                    // Keep the previous snapshot instead of overwriting with an empty series.
                    failed++;
                    continue;
                }
                batch.add(new PendingSnapshot(
                    ticker,
                    timeframe,
                    previousSessionCloseByTicker.get(ticker),
                    series
                ));
            }

            // One shared fetchedAt per timeframe so every ticker the widget
            // renders together carries the same latest minute.
            Instant fetchedAt = Instant.now();
            for (PendingSnapshot pending : batch) {
                try {
                    dashboardOhlcSnapshotRepository.upsert(
                        pending.ticker(),
                        pending.timeframe(),
                        pending.previousSessionClose(),
                        OBJECT_MAPPER.writeValueAsString(pending.series()),
                        fetchedAt
                    );
                    stored++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[MARKET-PERFORMANCE] Failed to store snapshot for {} {}: {}",
                        pending.ticker(), pending.timeframe(), e.getMessage());
                }
            }
        }

        int purged = dashboardOhlcSnapshotRepository.deleteByFetchedAtBefore(staleCutoff);
        log.info("[MARKET-PERFORMANCE] Snapshot cycle finished - stored: {}, failed: {}, skipped timeframes: {}, purged: {} (tickers: {})",
            stored, failed, skippedTimeframes, purged, tickers);
    }

    /**
     * Previous-session close per ticker from the daily stock-state store
     * (stock-state prev_close, pulled once per trading day at 9:30 AM ET).
     * Falls back to the already-stored value when the daily row is missing
     * so a gap in the daily collection never wipes the 1D baseline.
     */
    private Map<String, BigDecimal> fetchPreviousSessionCloses(List<String> tickers) {
        Map<String, BigDecimal> storedCloses = loadStoredPreviousSessionCloses(tickers);
        Map<String, BigDecimal> dailyCloses = new LinkedHashMap<>();
        try {
            for (DailyStockStateSnapshot snapshot
                    : dailyStockStateSnapshotRepository.findLatestByTickerIn(tickers)) {
                if (snapshot.getPrevClose() != null) {
                    dailyCloses.put(snapshot.getTicker(), snapshot.getPrevClose());
                }
            }
        } catch (Exception e) {
            log.warn("[MARKET-PERFORMANCE] Daily stock-state read failed: {}", e.getMessage());
        }

        Map<String, BigDecimal> closes = new LinkedHashMap<>();
        for (String ticker : tickers) {
            BigDecimal prevClose = dailyCloses.get(ticker);
            if (prevClose == null) {
                prevClose = storedCloses.get(ticker);
            }
            closes.put(ticker, prevClose);
        }

        return closes;
    }

    private Map<String, BigDecimal> loadStoredPreviousSessionCloses(List<String> tickers) {
        try {
            Map<String, BigDecimal> storedCloses = new LinkedHashMap<>();
            for (DashboardOhlcSnapshot snapshot
                    : dashboardOhlcSnapshotRepository.findByTimeframeAndTickerIn("1D", tickers)) {
                if (snapshot.getPreviousSessionClose() != null) {
                    storedCloses.put(snapshot.getTicker(), snapshot.getPreviousSessionClose());
                }
            }
            return storedCloses;
        } catch (Exception e) {
            log.warn("[MARKET-PERFORMANCE] Failed to load stored previous-session closes: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<DashboardMarketPerformanceResponse.SeriesPoint> fetchSeries(String ticker, String timeframe) {
        try {
            UnusualWhalesOhlcDto response = unusualWhalesClient.getOhlcDataUncached(
                ticker,
                determineCandleSize(timeframe),
                null,
                mapToUwTimeframe(timeframe),
                null,
                null
            );
            return transformSeries(timeframe, response);
        } catch (Exception e) {
            log.warn("[MARKET-PERFORMANCE] Failed to fetch series for ticker={} timeframe={}: {}",
                ticker, timeframe, e.getMessage());
            return List.of();
        }
    }

    private boolean isWithinCollectionWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(COLLECTION_WINDOW_OPEN) && !time.isAfter(COLLECTION_WINDOW_CLOSE);
    }

    private List<DashboardMarketPerformanceResponse.SeriesPoint> deserializeSeries(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, SERIES_TYPE);
        } catch (Exception e) {
            log.warn("[MARKET-PERFORMANCE] Failed to deserialize stored series: {}", e.getMessage());
            return null;
        }
    }

    private List<DashboardMarketPerformanceResponse.SeriesPoint> transformSeries(
        String timeframe,
        UnusualWhalesOhlcDto response
    ) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return List.of();
        }

        List<Candle> candles = response.data().stream()
            .map(this::toCandle)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong(Candle::timestamp))
            .toList();

        if (candles.isEmpty()) {
            return List.of();
        }

        List<Candle> filtered = switch (timeframe) {
            case "1D" -> filterOneDay(candles);
            case "5D" -> filterFiveDays(candles);
            case "1M" -> filterOneMonth(candles);
            case "3M" -> filterDailyRange(candles, 90);
            case "6M" -> filterDailyRange(candles, 180);
            case "YTD" -> filterYearToDate(candles);
            case "1Y" -> filterYearRange(candles, 1);
            case "5Y" -> filterYearRange(candles, 5);
            default -> candles;
        };

        return filtered.stream()
            .map(candle -> new DashboardMarketPerformanceResponse.SeriesPoint(
                candle.timestamp(),
                candle.tradingDate() != null ? candle.tradingDate().toString() : null,
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
            ))
            .toList();
    }

    private List<Candle> filterOneDay(List<Candle> candles) {
        return candles.stream()
            .filter(this::isWeekday)
            .filter(this::isWithinRegularHours)
            .toList();
    }

    private List<Candle> filterFiveDays(List<Candle> candles) {
        List<LocalDate> tradingDates = candles.stream()
            .map(Candle::tradingDate)
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .limit(5)
            .toList();

        if (tradingDates.isEmpty()) {
            return List.of();
        }

        Set<LocalDate> allowedDates = Set.copyOf(tradingDates);
        return candles.stream()
            .filter(candle -> candle.tradingDate() != null && allowedDates.contains(candle.tradingDate()))
            .filter(this::isWeekday)
            .filter(this::isWithinRegularHours)
            .toList();
    }

    private List<Candle> filterOneMonth(List<Candle> candles) {
        long latestTimestamp = candles.get(candles.size() - 1).timestamp();
        long cutoffTimestamp = Instant.ofEpochMilli(latestTimestamp)
            .atZone(NEW_YORK_ZONE)
            .minusDays(30)
            .toInstant()
            .toEpochMilli();

        return candles.stream()
            .filter(this::isRegularMarketOnly)
            .filter(candle -> candle.timestamp() >= cutoffTimestamp)
            .toList();
    }

    private List<Candle> filterDailyRange(List<Candle> candles, int daysBack) {
        List<Candle> regularCandles = candles.stream()
            .filter(this::isRegularMarketOnly)
            .toList();

        if (regularCandles.isEmpty()) {
            return List.of();
        }

        LocalDate latestDate = regularCandles.get(regularCandles.size() - 1).tradingDate();
        if (latestDate == null) {
            return regularCandles;
        }

        LocalDate cutoffDate = latestDate.minusDays(daysBack);
        return regularCandles.stream()
            .filter(candle -> candle.tradingDate() != null && !candle.tradingDate().isBefore(cutoffDate))
            .toList();
    }

    private List<Candle> filterYearToDate(List<Candle> candles) {
        List<Candle> regularCandles = candles.stream()
            .filter(this::isRegularMarketOnly)
            .toList();

        if (regularCandles.isEmpty()) {
            return List.of();
        }

        LocalDate cutoffDate = LocalDate.now(NEW_YORK_ZONE).withDayOfYear(1);
        return regularCandles.stream()
            .filter(candle -> candle.tradingDate() != null && !candle.tradingDate().isBefore(cutoffDate))
            .toList();
    }

    private List<Candle> filterYearRange(List<Candle> candles, int yearsBack) {
        List<Candle> regularCandles = candles.stream()
            .filter(this::isRegularMarketOnly)
            .toList();

        if (regularCandles.isEmpty()) {
            return List.of();
        }

        LocalDate latestDate = regularCandles.get(regularCandles.size() - 1).tradingDate();
        if (latestDate == null) {
            return regularCandles;
        }

        LocalDate cutoffDate = latestDate.minusYears(yearsBack);
        return regularCandles.stream()
            .filter(candle -> candle.tradingDate() != null && !candle.tradingDate().isBefore(cutoffDate))
            .toList();
    }

    private Candle toCandle(UnusualWhalesOhlcDto.OhlcItem item) {
        try {
            LocalDate tradingDate = resolveTradingDate(item);
            long timestamp = resolveTimestamp(item, tradingDate);
            BigDecimal open = parseDecimal(item.open());
            BigDecimal high = parseDecimal(item.high());
            BigDecimal low = parseDecimal(item.low());
            BigDecimal close = parseDecimal(item.close());

            if (open == null || high == null || low == null || close == null) {
                return null;
            }

            return new Candle(
                timestamp,
                tradingDate,
                open,
                high,
                low,
                close,
                item.volume(),
                item.marketTime()
            );
        } catch (Exception e) {
            log.debug("[MARKET-PERFORMANCE] Skipping malformed OHLC candle: {}", e.getMessage());
            return null;
        }
    }

    private LocalDate resolveTradingDate(UnusualWhalesOhlcDto.OhlcItem item) {
        if (item.date() != null && !item.date().isBlank()) {
            return LocalDate.parse(item.date());
        }
        if (item.endTime() != null && !item.endTime().isBlank()) {
            return OffsetDateTime.parse(item.endTime()).atZoneSameInstant(NEW_YORK_ZONE).toLocalDate();
        }
        if (item.startTime() != null && !item.startTime().isBlank()) {
            return OffsetDateTime.parse(item.startTime()).atZoneSameInstant(NEW_YORK_ZONE).toLocalDate();
        }
        return null;
    }

    /**
     * Candles are labeled by their open time (standard charting convention).
     * Using endTime shifted every intraday bar one bucket late and pushed the
     * final session bar to 16:00, where regular-hours filters drop it.
     */
    private long resolveTimestamp(UnusualWhalesOhlcDto.OhlcItem item, LocalDate tradingDate) {
        if (item.startTime() != null && !item.startTime().isBlank()) {
            return OffsetDateTime.parse(item.startTime()).toInstant().toEpochMilli();
        }
        if (item.endTime() != null && !item.endTime().isBlank()) {
            return OffsetDateTime.parse(item.endTime()).toInstant().toEpochMilli();
        }
        if (tradingDate != null) {
            return tradingDate.atStartOfDay(NEW_YORK_ZONE).toInstant().toEpochMilli();
        }
        throw new IllegalArgumentException("OHLC candle is missing all time fields");
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private boolean isRegularMarketOnly(Candle candle) {
        return candle.marketTime() != null && candle.marketTime().equalsIgnoreCase("r");
    }

    private boolean isWeekday(Candle candle) {
        if (candle.tradingDate() == null) {
            return true;
        }
        return switch (candle.tradingDate().getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> true;
        };
    }

    private boolean isWithinRegularHours(Candle candle) {
        LocalTime time = Instant.ofEpochMilli(candle.timestamp())
            .atZone(NEW_YORK_ZONE)
            .toLocalTime();
        return !time.isBefore(REGULAR_OPEN) && !time.isAfter(REGULAR_CLOSE);
    }

    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("timeframe is required");
        }

        String normalized = timeframe.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TIMEFRAMES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported timeframe: " + timeframe);
        }

        return normalized;
    }

    private String determineCandleSize(String timeframe) {
        return switch (timeframe) {
            case "1D" -> "1m";
            case "5D" -> "5m";
            case "1M" -> "30m";
            default -> "1d";
        };
    }

    private String mapToUwTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1D" -> "1D";
            case "5D" -> "1W";
            case "1M", "3M", "6M", "YTD", "1Y", "5Y" -> timeframe;
            default -> null;
        };
    }

    private String resolveAsOf(Map<String, DashboardMarketPerformanceResponse.TickerSeries> tickerSeries) {
        long latestTimestamp = tickerSeries.values().stream()
            .flatMap(series -> series.series().stream())
            .mapToLong(DashboardMarketPerformanceResponse.SeriesPoint::timestamp)
            .max()
            .orElseGet(() -> ZonedDateTime.now(NEW_YORK_ZONE).toInstant().toEpochMilli());

        return Instant.ofEpochMilli(latestTimestamp)
            .atZone(NEW_YORK_ZONE)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private record Candle(
        long timestamp,
        LocalDate tradingDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume,
        String marketTime
    ) {}

    /** Fully-fetched snapshot buffered until the whole cycle is written at once. */
    private record PendingSnapshot(
        String ticker,
        String timeframe,
        BigDecimal previousSessionClose,
        List<DashboardMarketPerformanceResponse.SeriesPoint> series
    ) {}
}
