package com.marketmono.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.TopNetImpactResponse;
import com.marketmono.dto.UnusualWhalesTopNetImpactDto;
import com.marketmono.entity.TopNetImpactSnapshot;
import com.marketmono.repository.TopNetImpactSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Stores and serves the top net impact ticker lists for the dashboard
 * Top Bullish / Top Bearish Net Premium widgets.
 *
 * Read path: database-only from top_net_impact_snapshots. User requests never
 * call the upstream market data API for these widgets.
 *
 * Write path: the scheduler calls {@link #collectAndStoreSnapshot()} every
 * minute during the 9:30 AM - 4:00 PM ET window (Mon-Fri). Each cycle makes a
 * single upstream top-net-impact call, splits the result into bullish and
 * bearish lists (same logic as the legacy live path), and replaces the current
 * day's row. Rows older than the retention window are purged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopNetImpactSnapshotService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    // Collection window: 9:30 AM - 4:00 PM ET, Monday-Friday
    private static final LocalTime COLLECTION_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime COLLECTION_WINDOW_CLOSE = LocalTime.of(16, 0);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<TopNetImpactResponse.TopNetImpactTicker>> TICKER_LIST_TYPE =
        new TypeReference<>() {};

    private final TopNetImpactSnapshotRepository topNetImpactSnapshotRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    @Value("${market-data.top-net-impact.retention-days:4}")
    private int retentionDays = 4;

    /**
     * Serves the latest stored bullish/bearish lists. No upstream fallback:
     * when the store is empty or stale, empty lists are returned.
     */
    public TopNetImpactResponse getTopNetImpactFromDatabase() {
        try {
            TopNetImpactSnapshot snapshot = topNetImpactSnapshotRepository
                .findTopByOrderByTradingDateDesc()
                .orElse(null);

            Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
            if (snapshot == null || snapshot.getFetchedAt().isBefore(staleCutoff)) {
                log.warn("[TOP-NET-IMPACT] No fresh snapshot available, returning empty lists");
                return TopNetImpactResponse.success(
                    new TopNetImpactResponse.TopNetImpactData(List.of(), List.of())
                );
            }

            List<TopNetImpactResponse.TopNetImpactTicker> bullish = deserializeTickers(snapshot.getBullish());
            List<TopNetImpactResponse.TopNetImpactTicker> bearish = deserializeTickers(snapshot.getBearish());
            return TopNetImpactResponse.success(new TopNetImpactResponse.TopNetImpactData(
                bullish == null ? List.of() : bullish,
                bearish == null ? List.of() : bearish
            ));
        } catch (Exception e) {
            // e.g. top_net_impact_snapshots migration not yet applied
            log.warn("[TOP-NET-IMPACT] Snapshot read failed ({}), returning empty lists", e.getMessage());
            return TopNetImpactResponse.success(
                new TopNetImpactResponse.TopNetImpactData(List.of(), List.of())
            );
        }
    }

    /**
     * Fetches the latest top net impact tickers (one upstream call), splits
     * them into bullish/bearish lists, and replaces today's row. Outside the
     * market window it only runs when the store is empty/stale so a fresh
     * deploy still gets data immediately.
     */
    public void collectAndStoreSnapshot() {
        ZonedDateTime marketNow = ZonedDateTime.now(NEW_YORK_ZONE);
        Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        if (!isWithinCollectionWindow(marketNow)
            && topNetImpactSnapshotRepository.existsByFetchedAtAfter(staleCutoff)) {
            log.debug("[TOP-NET-IMPACT] Skipping snapshot collection outside market window");
            return;
        }

        UnusualWhalesTopNetImpactDto response;
        try {
            response = unusualWhalesClient.getTopNetImpact();
        } catch (Exception e) {
            log.warn("[TOP-NET-IMPACT] Failed to fetch top net impact data: {}", e.getMessage());
            return;
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            // Keep the previous snapshot instead of overwriting with empty lists.
            log.warn("[TOP-NET-IMPACT] No top net impact data returned, keeping previous snapshot");
            return;
        }

        // Same split as the legacy live path: bullish sorted by net premium
        // descending, bearish ascending (most negative first).
        List<TopNetImpactResponse.TopNetImpactTicker> bullish = response.data().stream()
            .filter(item -> item.netPremium() != null && item.netPremium() > 0)
            .sorted((a, b) -> Double.compare(b.netPremium(), a.netPremium()))
            .map(item -> new TopNetImpactResponse.TopNetImpactTicker(item.ticker(), item.netPremium()))
            .toList();

        List<TopNetImpactResponse.TopNetImpactTicker> bearish = response.data().stream()
            .filter(item -> item.netPremium() != null && item.netPremium() < 0)
            .sorted(Comparator.comparingDouble(UnusualWhalesTopNetImpactDto.TopNetImpactItem::netPremium))
            .map(item -> new TopNetImpactResponse.TopNetImpactTicker(item.ticker(), item.netPremium()))
            .toList();

        LocalDate tradingDate = marketNow.toLocalDate();
        try {
            topNetImpactSnapshotRepository.upsert(
                tradingDate,
                OBJECT_MAPPER.writeValueAsString(bullish),
                OBJECT_MAPPER.writeValueAsString(bearish),
                Instant.now()
            );
        } catch (Exception e) {
            log.warn("[TOP-NET-IMPACT] Failed to store snapshot for {}: {}", tradingDate, e.getMessage());
            return;
        }

        int purged = topNetImpactSnapshotRepository.deleteByTradingDateBefore(
            tradingDate.minusDays(retentionDays)
        );
        log.info("[TOP-NET-IMPACT] Snapshot cycle finished - date: {}, bullish: {}, bearish: {}, purged: {}",
            tradingDate, bullish.size(), bearish.size(), purged);
    }

    private boolean isWithinCollectionWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(COLLECTION_WINDOW_OPEN) && !time.isAfter(COLLECTION_WINDOW_CLOSE);
    }

    private List<TopNetImpactResponse.TopNetImpactTicker> deserializeTickers(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, TICKER_LIST_TYPE);
        } catch (Exception e) {
            log.warn("[TOP-NET-IMPACT] Failed to deserialize stored ticker list: {}", e.getMessage());
            return null;
        }
    }
}
