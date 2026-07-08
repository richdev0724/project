package com.marketmono.scheduler;

import com.marketmono.service.CrmPriceStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Node-local jobs for the CRM price stream: reconciles Unusual Whales
 * price channel subscriptions with the CRM ticker set, and flushes the
 * latest in-memory ticks to crm_price_snapshots. Not ShedLock-guarded on
 * purpose - every node maintains its own upstream websocket subscriptions,
 * and the flush is a last-writer-wins upsert.
 */
@Component
@ConditionalOnProperty(prefix = "market-data.crm-price-stream", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CrmPriceStreamScheduler {

    private final CrmPriceStreamService crmPriceStreamService;

    @Scheduled(
        fixedRateString = "${market-data.crm-price-stream.ticker-refresh-ms:60000}",
        initialDelayString = "${market-data.crm-price-stream.initial-delay-ms:15000}"
    )
    public void refreshSubscriptions() {
        try {
            crmPriceStreamService.refreshSubscriptions();
        } catch (Exception e) {
            log.error("Error during CRM price stream subscription refresh", e);
        }
    }

    @Scheduled(
        fixedRateString = "${market-data.crm-price-stream.persist-interval-ms:5000}",
        initialDelayString = "${market-data.crm-price-stream.initial-delay-ms:15000}"
    )
    public void flushTicks() {
        try {
            crmPriceStreamService.flushDirtyTicks();
        } catch (Exception e) {
            log.error("Error during CRM price stream tick flush", e);
        }
    }
}
