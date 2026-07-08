package com.marketmono.scheduler;

import com.marketmono.service.DashboardContractSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes dashboard_contract_snapshots every minute so the Top Contracts,
 * Top ETF Orders, and Top Sweep Orders widgets are served from our database
 * instead of proxying the upstream screener API on every page load or filter
 * change. The service gates actual collection to the 9:30 AM - 4:00 PM ET
 * market window (with a cold-start catch-up when the store is empty).
 */
@Component
@ConditionalOnProperty(prefix = "market-data.dashboard-contracts", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DashboardContractSnapshotScheduler {

    private final DashboardContractSnapshotService dashboardContractSnapshotService;

    @Scheduled(
        fixedRateString = "${market-data.dashboard-contracts.interval-ms:60000}",
        initialDelayString = "${market-data.dashboard-contracts.initial-delay-ms:35000}"
    )
    @SchedulerLock(name = "dashboardContractSnapshot_sync", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void syncSnapshots() {
        try {
            dashboardContractSnapshotService.collectAndStoreSnapshots();
        } catch (Exception e) {
            log.error("Error during scheduled dashboard contract snapshot sync", e);
        }
    }
}
