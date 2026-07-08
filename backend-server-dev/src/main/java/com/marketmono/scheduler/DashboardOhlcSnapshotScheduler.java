package com.marketmono.scheduler;

import com.marketmono.service.DashboardMarketPerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes dashboard_ohlc_snapshots every minute so the Market Overview and
 * Magnificent Seven charts are served from our database instead of proxying
 * the upstream market data API on every page load. The service gates actual
 * collection to the 9:30 AM - 4:15 PM ET market window (with a cold-start
 * catch-up when the store is empty).
 */
@Component
@ConditionalOnProperty(prefix = "market-data.dashboard-ohlc", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DashboardOhlcSnapshotScheduler {

    private final DashboardMarketPerformanceService dashboardMarketPerformanceService;

    @Scheduled(
        fixedRateString = "${market-data.dashboard-ohlc.interval-ms:60000}",
        initialDelayString = "${market-data.dashboard-ohlc.initial-delay-ms:15000}"
    )
    @SchedulerLock(name = "dashboardOhlc_sync", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void syncSnapshots() {
        try {
            dashboardMarketPerformanceService.collectAndStoreSnapshots();
        } catch (Exception e) {
            log.error("Error during scheduled dashboard OHLC snapshot sync", e);
        }
    }
}
