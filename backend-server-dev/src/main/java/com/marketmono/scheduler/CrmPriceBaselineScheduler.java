package com.marketmono.scheduler;

import com.marketmono.service.CrmPriceBaselineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls the upstream stock-state endpoint once per CRM ticker per trading day
 * (9:30 AM ET Monday-Friday) into crm_price_baselines. The baseline prev_close
 * is compared against the live websocket price to compute % gain/loss on the
 * Strata page. A catch-up pass backfills today's rows after deploys or a
 * failed market-open run.
 */
@Component
@ConditionalOnProperty(prefix = "market-data.crm-price-stream", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CrmPriceBaselineScheduler {

    private final CrmPriceBaselineService crmPriceBaselineService;

    @Scheduled(
        cron = "${market-data.crm-price-stream.baseline-schedule:0 30 9 * * MON-FRI}",
        zone = "America/New_York"
    )
    @SchedulerLock(name = "crmPriceBaseline_sync", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void collectAtMarketOpen() {
        try {
            crmPriceBaselineService.collectAndStoreBaselines();
        } catch (Exception e) {
            log.error("Error during scheduled CRM price baseline collection", e);
        }
    }

    @Scheduled(
        fixedRateString = "${market-data.crm-price-stream.baseline-catch-up-interval-ms:300000}",
        initialDelayString = "${market-data.crm-price-stream.initial-delay-ms:15000}"
    )
    @SchedulerLock(name = "crmPriceBaseline_catchUp", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void catchUpIfMissing() {
        try {
            crmPriceBaselineService.collectIfMissing();
        } catch (Exception e) {
            log.error("Error during CRM price baseline catch-up check", e);
        }
    }
}
