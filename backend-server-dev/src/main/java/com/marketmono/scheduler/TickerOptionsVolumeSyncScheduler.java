package com.marketmono.scheduler;

import com.marketmono.service.TickerOptionsVolumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the ticker_options_volume table every 10 minutes so the Market
 * Premiums widget is served from our database instead of proxying the
 * upstream market data API on every page load. The service further gates
 * syncing to the 9:30 AM - 4:15 PM ET Mon-Fri market window (with a
 * cold-start catch-up when a configured ticker has no data yet).
 */
@Component
@ConditionalOnProperty(prefix = "market-data.ticker-options-volume", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TickerOptionsVolumeSyncScheduler {

    private final TickerOptionsVolumeService tickerOptionsVolumeService;

    @Scheduled(
        fixedRateString = "${market-data.ticker-options-volume.interval-ms:600000}",
        initialDelayString = "${market-data.ticker-options-volume.initial-delay-ms:15000}"
    )
    @SchedulerLock(name = "tickerOptionsVolume_sync", lockAtMostFor = "PT2M", lockAtLeastFor = "PT30S")
    public void syncOptionsVolume() {
        log.debug("[OPTIONS-VOLUME-SYNC] Starting scheduled sync");
        tickerOptionsVolumeService.syncConfiguredTickers();
    }
}
