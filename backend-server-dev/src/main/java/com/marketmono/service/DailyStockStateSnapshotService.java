package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.repository.DailyStockStateSnapshotRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects one upstream stock-state snapshot per ticker per trading day.
 *
 * The dashboard Sectors widget and the Market Overview / Mag 7 collectors
 * previously called the upstream stock-state endpoint every minute per
 * ticker even though the fields they use only change once per session.
 * This service pulls stock-state once at 9:30 AM ET Monday-Friday, stores
 * it in daily_stock_state_snapshots, and purges rows older than the
 * retention window (4 days) so Friday's row still covers the weekend.
 *
 * A catch-up pass fills today's rows after a deploy or a failed 9:30 run
 * (and fills the store from scratch on a cold start at any time).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyStockStateSnapshotService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);

    private final DailyStockStateSnapshotRepository dailyStockStateSnapshotRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    /** Union of the sector ETFs and the Market Overview / Mag 7 tickers. */
    @Value("${market-data.daily-stock-state.tickers:"
        + "XLP,XLU,XLV,XLB,XLC,XLY,XLE,XLF,XLI,XLK,XLRE,"
        + "SPY,QQQ,IWM,DIA,NVDA,META,MSFT,AAPL,GOOG,AMZN,TSLA}")
    private List<String> tickers = new ArrayList<>();

    @Value("${market-data.daily-stock-state.retention-days:4}")
    private int retentionDays = 4;

    /**
     * One stock-state call per ticker; upserts today's row per ticker and
     * purges rows older than the retention window. Invoked by the 9:30 AM ET
     * cron and by the catch-up pass.
     */
    public void collectAndStoreSnapshots() {
        LocalDate todayEt = LocalDate.now(NEW_YORK_ZONE);
        Instant fetchedAt = Instant.now();
        int stored = 0;
        int failed = 0;

        for (String rawTicker : tickers) {
            String ticker = rawTicker.trim().toUpperCase(Locale.ROOT);
            if (ticker.isBlank()) {
                continue;
            }
            StockStateResponse.StockStateData data = fetchStockState(ticker);
            if (data == null) {
                failed++;
                continue;
            }
            try {
                dailyStockStateSnapshotRepository.upsert(
                    ticker,
                    todayEt,
                    parseDecimal(data.close()),
                    parseDecimal(data.prevClose()),
                    parseDecimal(data.open()),
                    parseDecimal(data.high()),
                    parseDecimal(data.low()),
                    data.volume(),
                    data.totalVolume(),
                    data.tapeTime(),
                    data.marketTime(),
                    fetchedAt
                );
                stored++;
            } catch (Exception e) {
                failed++;
                log.warn("[DAILY-STOCK-STATE] Failed to store snapshot for {}: {}", ticker, e.getMessage());
            }
        }

        long purged = dailyStockStateSnapshotRepository.deleteBySnapshotDateBefore(todayEt.minusDays(retentionDays));
        log.info("[DAILY-STOCK-STATE] Daily collection finished - stored: {}, failed: {}, purged: {}",
            stored, failed, purged);
    }

    /**
     * Catch-up pass: collects when today's trading-day rows are incomplete
     * (deploy after 9:30, or a partially failed open run) or when the store
     * has no fresh rows at all (cold start, any day/time).
     */
    public void collectIfMissing() {
        Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        boolean hasFreshRows = dailyStockStateSnapshotRepository.existsByFetchedAtAfter(staleCutoff);
        if (!hasFreshRows) {
            log.info("[DAILY-STOCK-STATE] Cold start detected - collecting initial snapshots");
            collectAndStoreSnapshots();
            return;
        }

        ZonedDateTime marketNow = ZonedDateTime.now(NEW_YORK_ZONE);
        DayOfWeek day = marketNow.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            || marketNow.toLocalTime().isBefore(MARKET_OPEN)) {
            return;
        }

        if (!hasCompleteRowsFor(marketNow.toLocalDate())) {
            log.info("[DAILY-STOCK-STATE] Today's snapshots incomplete - running catch-up collection");
            collectAndStoreSnapshots();
        }
    }

    private boolean hasCompleteRowsFor(LocalDate snapshotDate) {
        List<String> normalized = tickers.stream()
            .map(ticker -> ticker.trim().toUpperCase(Locale.ROOT))
            .filter(ticker -> !ticker.isBlank())
            .distinct()
            .toList();
        long todayRows = dailyStockStateSnapshotRepository.findLatestByTickerIn(normalized).stream()
            .filter(snapshot -> snapshotDate.equals(snapshot.getSnapshotDate()))
            .count();
        return todayRows >= normalized.size();
    }

    private StockStateResponse.StockStateData fetchStockState(String ticker) {
        try {
            StockStateResponse response = unusualWhalesClient.getStockStateForStrataCollector(ticker);
            return response != null ? response.data() : null;
        } catch (Exception e) {
            log.warn("[DAILY-STOCK-STATE] [{}] stock-state fetch failed: {}", ticker, e.getMessage());
            return null;
        }
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
}
