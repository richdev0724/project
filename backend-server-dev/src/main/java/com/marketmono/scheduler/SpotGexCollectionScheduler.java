package com.marketmono.scheduler;

import com.marketmono.metrics.UwCallSource;
import com.marketmono.metrics.UwCallSourceContext;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.service.MarketHoursService;
import com.marketmono.service.SpotGexCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled entry point for recurring spot GEX collection.
 */
@Component
@ConditionalOnProperty(prefix = "spot-gex.collection", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SpotGexCollectionScheduler {

    private final MarketHoursService marketHoursService;
    private final SpotGexCollectionService spotGexCollectionService;
    private final SpotGexCollectionProperties spotGexCollectionProperties;

    @Scheduled(
        fixedDelayString = "${spot-gex.collection.interval-ms:60000}",
        initialDelayString = "${spot-gex.collection.initial-delay-ms:30000}"
    )
    @SchedulerLock(name = "spotGex_collectAll", lockAtMostFor = "PT5M", lockAtLeastFor = "PT50S")
    public void collectSpotGexData() {
        UwCallSourceContext.runWith(UwCallSource.SCHEDULER_SPOT_GEX, () -> {
            if (!marketHoursService.isRegularMarketOpen()) {
                log.debug("[SPOT-GEX] Market closed, skipping scheduled collection");
                return;
            }

            if (spotGexCollectionProperties.getSource() == SpotGexCollectionProperties.SourceMode.STREAM) {
                spotGexCollectionService.collectRestOnlyTickers();
                return;
            }

            spotGexCollectionService.collectAll();
        });
    }
}
