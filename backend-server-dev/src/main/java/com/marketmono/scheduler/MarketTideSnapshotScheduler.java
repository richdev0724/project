package com.marketmono.scheduler;

import com.marketmono.service.MarketTideSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes market_tide_snapshots every minute so the dashboard Mono Matrix
 * chart is served from our database instead of proxying the upstream market
 * data API on every page load. The service gates actual collection to the
 * 9:30 AM - 4:00 PM ET market window (with a cold-start catch-up when the
 * store is empty).
 */
@Component
@ConditionalOnProperty(prefix = "market-data.market-tide", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MarketTideSnapshotScheduler {

    private final MarketTideSnapshotService marketTideSnapshotService;

    @Scheduled(
        fixedRateString = "${market-data.market-tide.interval-ms:60000}",
        initialDelayString = "${market-data.market-tide.initial-delay-ms:25000}"
    )
    @SchedulerLock(name = "marketTideSnapshot_sync", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void syncSnapshot() {
        try {
            marketTideSnapshotService.collectAndStoreSnapshot();
        } catch (Exception e) {
            log.error("Error during scheduled market tide snapshot sync", e);
        }
    }
}
