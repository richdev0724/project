package com.marketmono.scheduler;

import com.marketmono.service.DailyStockStateSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls the upstream stock-state endpoint once per ticker per trading day
 * (9:30 AM ET Monday-Friday) into daily_stock_state_snapshots. The Sectors
 * widget and Market Overview / Mag 7 collectors read from that table instead
 * of calling stock-state every minute. A catch-up pass backfills today's
 * rows after deploys or a failed market-open run.
 */
@Component
@ConditionalOnProperty(prefix = "market-data.daily-stock-state", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DailyStockStateSnapshotScheduler {

    private final DailyStockStateSnapshotService dailyStockStateSnapshotService;

    @Scheduled(
        cron = "${market-data.daily-stock-state.collection-schedule:0 30 9 * * MON-FRI}",
        zone = "America/New_York"
    )
    @SchedulerLock(name = "dailyStockState_sync", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void collectAtMarketOpen() {
        try {
            dailyStockStateSnapshotService.collectAndStoreSnapshots();
        } catch (Exception e) {
            log.error("Error during scheduled daily stock-state collection", e);
        }
    }

    @Scheduled(
        fixedRateString = "${market-data.daily-stock-state.catch-up-interval-ms:300000}",
        initialDelayString = "${market-data.daily-stock-state.initial-delay-ms:10000}"
    )
    @SchedulerLock(name = "dailyStockState_catchUp", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void catchUpIfMissing() {
        try {
            dailyStockStateSnapshotService.collectIfMissing();
        } catch (Exception e) {
            log.error("Error during daily stock-state catch-up check", e);
        }
    }
}
