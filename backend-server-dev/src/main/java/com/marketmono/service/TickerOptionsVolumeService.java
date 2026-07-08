package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionsVolumeDto;
import com.marketmono.dto.OptionsVolumeItemDto;
import com.marketmono.entity.TickerOptionsVolume;
import com.marketmono.repository.TickerOptionsVolumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Serves ticker options volume (Market Premiums) from our database and keeps
 * it fresh via a scheduled sync against the upstream market data API.
 *
 * Read path: DB-first. If the database cannot satisfy the request (ticker not
 * collected, or more history requested than stored), falls back to proxying
 * upstream — preserving the old behavior for arbitrary tickers while the
 * dashboard tickers (SPY/QQQ/IWM/DIA) are served without any upstream calls.
 */
@Service
@Slf4j
public class TickerOptionsVolumeService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    // Sync window: 9:30 AM - 4:15 PM ET, Monday-Friday
    private static final LocalTime SYNC_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime SYNC_WINDOW_CLOSE = LocalTime.of(16, 15);

    private final UnusualWhalesClient unusualWhalesClient;
    private final TickerOptionsVolumeRepository repository;
    private final List<String> syncTickers;
    private final int historyLimit;

    public TickerOptionsVolumeService(
        UnusualWhalesClient unusualWhalesClient,
        TickerOptionsVolumeRepository repository,
        @Value("${market-data.ticker-options-volume.tickers:SPY,QQQ,IWM,DIA}") List<String> syncTickers,
        @Value("${market-data.ticker-options-volume.history-limit:30}") int historyLimit
    ) {
        this.unusualWhalesClient = unusualWhalesClient;
        this.repository = repository;
        this.syncTickers = syncTickers.stream().map(String::toUpperCase).toList();
        this.historyLimit = historyLimit;
    }

    /**
     * Get options volume for a ticker, newest first (same ordering as the
     * upstream API).
     *
     * Synced dashboard tickers (Market Premiums widget) are database-only:
     * user requests never proxy upstream for them. Other tickers keep the
     * DB-first read with an upstream fallback.
     */
    public OptionsVolumeDto getOptionsVolume(String ticker, int limit) {
        boolean isSyncedTicker = syncTickers.contains(ticker.trim().toUpperCase(Locale.ROOT));

        try {
            List<TickerOptionsVolume> rows = repository.findByTickerOrderByDateDesc(
                ticker, PageRequest.of(0, limit)
            );

            if (isSyncedTicker || rows.size() >= limit) {
                log.debug("Serving {} options volume rows for {} from database", rows.size(), ticker);
                return new OptionsVolumeDto(rows.stream().map(this::toDto).toList());
            }

            log.debug("Database has {}/{} rows for {} - falling back to upstream proxy", rows.size(), limit, ticker);
        } catch (DataAccessException e) {
            // e.g. ticker_options_volume migration not yet applied (prod runs Liquibase manually)
            if (isSyncedTicker) {
                log.warn("Database read for options volume failed ({}), returning empty data for synced ticker {}",
                    e.getMessage(), ticker);
                return new OptionsVolumeDto(List.of());
            }
            log.warn("Database read for options volume failed ({}), falling back to upstream proxy", e.getMessage());
        }
        return unusualWhalesClient.getOptionsVolume(ticker, limit);
    }

    /**
     * Fetch the configured dashboard tickers from the upstream API and upsert
     * their daily snapshots. Invoked every 10 minutes by the sync scheduler;
     * outside the 9:30 AM - 4:15 PM ET Mon-Fri window it only runs when a
     * configured ticker has no data yet (cold start after a fresh deploy).
     */
    @Transactional
    public void syncConfiguredTickers() {
        if (!isWithinSyncWindow(ZonedDateTime.now(NEW_YORK_ZONE)) && hasDataForAllSyncTickers()) {
            log.debug("[OPTIONS-VOLUME-SYNC] Skipping sync outside market window");
            return;
        }

        int successCount = 0;
        int errorCount = 0;

        for (String ticker : syncTickers) {
            try {
                OptionsVolumeDto response = unusualWhalesClient.getOptionsVolume(ticker, historyLimit);

                if (response == null || response.data() == null || response.data().isEmpty()) {
                    log.warn("[OPTIONS-VOLUME-SYNC] No data received for {}", ticker);
                    errorCount++;
                    continue;
                }

                for (OptionsVolumeItemDto item : response.data()) {
                    if (item.date() == null) {
                        continue;
                    }
                    repository.upsert(
                        ticker,
                        item.date(),
                        item.callVolume(),
                        item.putVolume(),
                        parseDecimal(item.callPremium()),
                        parseDecimal(item.putPremium()),
                        item.callOpenInterest(),
                        item.putOpenInterest(),
                        item.callVolumeAskSide(),
                        item.callVolumeBidSide(),
                        item.putVolumeAskSide(),
                        item.putVolumeBidSide(),
                        parseDecimal(item.bullishPremium()),
                        parseDecimal(item.bearishPremium()),
                        parseDecimal(item.netCallPremium()),
                        parseDecimal(item.netPutPremium()),
                        parseDecimal(item.avg3DayCallVolume()),
                        parseDecimal(item.avg3DayPutVolume()),
                        parseDecimal(item.avg7DayCallVolume()),
                        parseDecimal(item.avg7DayPutVolume()),
                        parseDecimal(item.avg30DayCallVolume()),
                        parseDecimal(item.avg30DayPutVolume())
                    );
                }
                successCount++;
                log.debug("[OPTIONS-VOLUME-SYNC] Upserted {} rows for {}", response.data().size(), ticker);
            } catch (Exception e) {
                errorCount++;
                log.warn("[OPTIONS-VOLUME-SYNC] Failed to sync {}: {}", ticker, e.getMessage());
            }
        }

        log.info("[OPTIONS-VOLUME-SYNC] Sync cycle finished - success: {}, errors: {} (tickers: {})",
            successCount, errorCount, syncTickers);
    }

    private boolean isWithinSyncWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(SYNC_WINDOW_OPEN) && !time.isAfter(SYNC_WINDOW_CLOSE);
    }

    private boolean hasDataForAllSyncTickers() {
        try {
            for (String ticker : syncTickers) {
                if (!repository.existsByTicker(ticker)) {
                    return false;
                }
            }
            return true;
        } catch (DataAccessException e) {
            // Table not available yet; syncing now would fail anyway.
            return true;
        }
    }

    private OptionsVolumeItemDto toDto(TickerOptionsVolume row) {
        return new OptionsVolumeItemDto(
            row.getDate(),
            row.getCallVolume(),
            row.getPutVolume(),
            toPlainString(row.getCallPremium()),
            toPlainString(row.getPutPremium()),
            row.getCallOpenInterest(),
            row.getPutOpenInterest(),
            row.getCallVolumeAskSide(),
            row.getCallVolumeBidSide(),
            row.getPutVolumeAskSide(),
            row.getPutVolumeBidSide(),
            toPlainString(row.getBullishPremium()),
            toPlainString(row.getBearishPremium()),
            toPlainString(row.getNetCallPremium()),
            toPlainString(row.getNetPutPremium()),
            toPlainString(row.getAvg3DayCallVolume()),
            toPlainString(row.getAvg3DayPutVolume()),
            toPlainString(row.getAvg7DayCallVolume()),
            toPlainString(row.getAvg7DayPutVolume()),
            toPlainString(row.getAvg30DayCallVolume()),
            toPlainString(row.getAvg30DayPutVolume())
        );
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toPlainString(BigDecimal value) {
        return value != null ? value.toPlainString() : null;
    }
}
