package com.marketmono.scheduler;

import com.marketmono.service.FlowAlertService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Scheduled task to sync flow alerts from upstream market data API
 */
@Component
@ConditionalOnProperty(prefix = "unusual-whales.flow.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowAlertSyncScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(FlowAlertSyncScheduler.class);
    
    private final FlowAlertService flowAlertService;
    
    public FlowAlertSyncScheduler(FlowAlertService flowAlertService) {
        this.flowAlertService = flowAlertService;
    }
    
    @Scheduled(
        initialDelayString = "${unusual-whales.flow.sync.initial-delay-seconds:10}",
        fixedDelayString = "${unusual-whales.flow.sync.interval-seconds:30}",
        timeUnit = TimeUnit.SECONDS
    )
    @SchedulerLock(name = "flowAlerts_lockedRestPoll", lockAtMostFor = "PT55S", lockAtLeastFor = "PT25S")
    public void syncFlowAlerts() {
        try {
            flowAlertService.syncFlowAlerts();
        } catch (Exception e) {
            log.error("Error during scheduled flow alerts sync", e);
        }
    }
}
