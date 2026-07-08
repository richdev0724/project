package com.marketmono.scheduler;

import com.marketmono.service.TopNetImpactSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes top_net_impact_snapshots every minute so the dashboard Top
 * Bullish / Top Bearish Net Premium widgets are served from our database
 * instead of proxying the upstream market data API on every page load. The
 * service gates actual collection to the 9:30 AM - 4:00 PM ET market window
 * (with a cold-start catch-up when the store is empty).
 */
@Component
@ConditionalOnProperty(prefix = "market-data.top-net-impact", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TopNetImpactSnapshotScheduler {

    private final TopNetImpactSnapshotService topNetImpactSnapshotService;

    @Scheduled(
        fixedRateString = "${market-data.top-net-impact.interval-ms:60000}",
        initialDelayString = "${market-data.top-net-impact.initial-delay-ms:30000}"
    )
    @SchedulerLock(name = "topNetImpactSnapshot_sync", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void syncSnapshot() {
        try {
            topNetImpactSnapshotService.collectAndStoreSnapshot();
        } catch (Exception e) {
            log.error("Error during scheduled top net impact snapshot sync", e);
        }
    }
}
