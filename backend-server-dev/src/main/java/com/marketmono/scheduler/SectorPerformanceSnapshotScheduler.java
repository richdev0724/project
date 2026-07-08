package com.marketmono.scheduler;

import com.marketmono.service.SectorPerformanceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes sector_performance_snapshots so the dashboard Sectors widget is
 * served from our database instead of proxying the upstream market data API
 * on user requests. Every tick (1 minute) pulls fresh stock-state and daily
 * OHLC per sector ETF and recomputes daily / 30-day / 6-month performance.
 */
@Component
@ConditionalOnProperty(prefix = "market-data.sector-performance", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SectorPerformanceSnapshotScheduler {

    private final SectorPerformanceSnapshotService sectorPerformanceSnapshotService;

    @Scheduled(
        fixedRateString = "${market-data.sector-performance.interval-ms:60000}",
        initialDelayString = "${market-data.sector-performance.initial-delay-ms:20000}"
    )
    @SchedulerLock(name = "sectorPerformance_sync", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void syncSnapshots() {
        try {
            sectorPerformanceSnapshotService.collectAndStoreSnapshots();
        } catch (Exception e) {
            log.error("Error during scheduled sector performance snapshot sync", e);
        }
    }
}
