package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.CrmPriceStreamProperties;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.repository.CrmPriceBaselineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Collects one stock-state snapshot per CRM ticker per trading day at
 * 9:30 AM ET Monday-Friday into crm_price_baselines. This is the only time
 * stock-state is called for a CRM ticker; the baseline prev_close is compared
 * against the live websocket price to compute % gain/loss.
 *
 * A catch-up pass fills today's rows after a deploy or a failed 9:30 run
 * (and fills the store from scratch on a cold start at any time).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmPriceBaselineService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);

    private final CrmPriceBaselineRepository baselineRepository;
    private final UnusualWhalesClient unusualWhalesClient;
    private final StrataStockPriceTickerUniverseService tickerUniverseService;
    private final CrmPriceStreamService crmPriceStreamService;
    private final CrmPriceStreamProperties properties;

    /**
     * One stock-state call per CRM ticker; upserts today's row per ticker and
     * purges rows older than the retention window. Invoked by the 9:30 AM ET
     * cron and by the catch-up pass.
     */
    public void collectAndStoreBaselines() {
        Set<String> tickers = tickerUniverseService.getCrmPriceStreamTickers();
        if (tickers.isEmpty()) {
            log.info("[CRM-PRICE-BASELINE] No CRM tickers to collect");
            return;
        }

        LocalDate todayEt = LocalDate.now(NEW_YORK_ZONE);
        Instant fetchedAt = Instant.now();
        int stored = 0;
        int failed = 0;

        for (String ticker : tickers) {
            StockStateResponse.StockStateData data = fetchStockState(ticker);
            if (data == null) {
                failed++;
                continue;
            }
            try {
                baselineRepository.upsert(
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
                log.warn("[CRM-PRICE-BASELINE] Failed to store baseline for {}: {}", ticker, e.getMessage());
            }
        }

        long purged = baselineRepository.deleteBySnapshotDateBefore(
            todayEt.minusDays(properties.getBaselineRetentionDays())
        );
        crmPriceStreamService.reloadBaselines();
        log.info("[CRM-PRICE-BASELINE] Daily collection finished - stored: {}, failed: {}, purged: {}",
            stored, failed, purged);
    }

    /**
     * Catch-up pass: collects when today's trading-day rows are incomplete
     * (deploy after 9:30, or a partially failed open run) or when the store
     * has no fresh rows at all (cold start, any day/time).
     */
    public void collectIfMissing() {
        Set<String> tickers = tickerUniverseService.getCrmPriceStreamTickers();
        if (tickers.isEmpty()) {
            return;
        }

        Instant staleCutoff = Instant.now().minus(Duration.ofDays(properties.getBaselineRetentionDays()));
        boolean hasFreshRows = baselineRepository.findLatestPerTicker().stream()
            .anyMatch(baseline -> baseline.getFetchedAt().isAfter(staleCutoff));
        if (!hasFreshRows) {
            log.info("[CRM-PRICE-BASELINE] Cold start detected - collecting initial baselines");
            collectAndStoreBaselines();
            return;
        }

        ZonedDateTime marketNow = ZonedDateTime.now(NEW_YORK_ZONE);
        DayOfWeek day = marketNow.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            || marketNow.toLocalTime().isBefore(MARKET_OPEN)) {
            return;
        }

        long todayRows = baselineRepository.findBySnapshotDate(marketNow.toLocalDate()).stream()
            .filter(baseline -> tickers.contains(baseline.getTicker()))
            .count();
        if (todayRows < tickers.size()) {
            log.info("[CRM-PRICE-BASELINE] Today's baselines incomplete ({}/{}) - running catch-up",
                todayRows, tickers.size());
            collectAndStoreBaselines();
        }
    }

    private StockStateResponse.StockStateData fetchStockState(String ticker) {
        try {
            StockStateResponse response = unusualWhalesClient.getStockStateForStrataCollector(ticker);
            return response != null ? response.data() : null;
        } catch (Exception e) {
            log.warn("[CRM-PRICE-BASELINE] [{}] stock-state fetch failed: {}", ticker, e.getMessage());
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
